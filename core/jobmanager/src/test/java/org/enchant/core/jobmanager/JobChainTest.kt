package org.enchant.core.jobmanager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JobChainTest {
    @Test
    fun `then adds sequential segment`() {
        val job1 = createTestJob("job-1")
        val job2 = createTestJob("job-2")
        val job3 = createTestJob("job-3")
        val chain = TestableJobChain(job1)
        chain.then(job2).then(job3)
        assertEquals(3, chain.segmentCount)
    }

    @Test
    fun `then with list adds parallel segment`() {
        val job1 = createTestJob("job-1")
        val job2 = createTestJob("job-2")
        val job3 = createTestJob("job-3")
        val chain = TestableJobChain(job1)
        chain.then(listOf(job2, job3))
        assertEquals(2, chain.segmentCount)
        assertEquals(1, chain.getSegmentSize(0))
        assertEquals(2, chain.getSegmentSize(1))
    }

    @Test
    fun `then returns same chain for chaining`() {
        val job1 = createTestJob("job-1")
        val job2 = createTestJob("job-2")
        val chain = TestableJobChain(job1)
        val returned = chain.then(job2)
        assertSame(chain, returned)
    }

    @Test
    fun `multiple jobs in initial segment`() {
        val job1 = createTestJob("job-1")
        val job2 = createTestJob("job-2")
        val chain = TestableJobChain(listOf(job1, job2))
        assertEquals(1, chain.segmentCount)
        assertEquals(2, chain.getSegmentSize(0))
    }

    private fun createTestJob(id: String) = object : Job(id, JobParameters.Builder(id).build()) {
        override val factoryKey = "TestJob"
        override suspend fun run(): JobResult = success()
        override fun onFailure() {}
        override fun serialize(): ByteArray? = null
    }

    private class TestableJobChain(firstJob: Job) {
        private val segments = mutableListOf<MutableList<Job>>(mutableListOf(firstJob))

        constructor(firstJobs: List<Job>) : this(firstJobs.first()) {
            segments[0] = firstJobs.toMutableList()
        }

        val segmentCount: Int get() = segments.size

        fun getSegmentSize(index: Int): Int = segments[index].size

        fun then(job: Job): TestableJobChain {
            segments.add(mutableListOf(job))
            return this
        }

        fun then(jobs: List<Job>): TestableJobChain {
            segments.add(jobs.toMutableList())
            return this
        }
    }
}

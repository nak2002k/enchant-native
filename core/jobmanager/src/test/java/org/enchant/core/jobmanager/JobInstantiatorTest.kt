package org.enchant.core.jobmanager

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JobInstantiatorTest {
    @Test
    fun `getFactory returns registered factory`() {
        val factory = object : Job.Factory<TestJob> {
            override fun create(id: String, serializedData: ByteArray?): TestJob = TestJob(id)
        }
        val instantiator = JobInstantiator(mapOf("TestJob" to factory))
        assertEquals(factory, instantiator.getFactory("TestJob"))
    }

    @Test
    fun `getFactory returns null for unregistered key`() {
        val instantiator = JobInstantiator(emptyMap())
        assertNull(instantiator.getFactory("UnknownJob"))
    }

    @Test
    fun `hasFactory returns true for registered key`() {
        val factory = object : Job.Factory<TestJob> {
            override fun create(id: String, serializedData: ByteArray?): TestJob = TestJob(id)
        }
        val instantiator = JobInstantiator(mapOf("TestJob" to factory))
        assertTrue(instantiator.hasFactory("TestJob"))
    }

    @Test
    fun `hasFactory returns false for unregistered key`() {
        val instantiator = JobInstantiator(emptyMap())
        assertFalse(instantiator.hasFactory("UnknownJob"))
    }

    @Test
    fun `factory creates job with correct id`() {
        val factory = object : Job.Factory<TestJob> {
            override fun create(id: String, serializedData: ByteArray?): TestJob = TestJob(id)
        }
        val instantiator = JobInstantiator(mapOf("TestJob" to factory))
        val job = instantiator.getFactory("TestJob")?.create("custom-id", null)
        assertNotNull(job)
        assertEquals("custom-id", job!!.id)
    }

    private class TestJob(
        id: String,
        parameters: JobParameters = JobParameters.Builder(id).build()
    ) : Job(id, parameters) {
        override val factoryKey = "TestJob"
        override suspend fun run(): JobResult = success()
        override fun onFailure() {}
        override fun serialize(): ByteArray? = null
    }
}

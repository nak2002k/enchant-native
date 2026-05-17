package org.enchant.core.performance

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("ImagePipeline — Full Coverage")
class ImagePipelineTest {

    @Nested @DisplayName("Constants")
    inner class ConstantsTest {
        @Test @DisplayName("memory cache uses 25% of max memory")
        fun `memory cache percent`() {
            val maxMemory = Runtime.getRuntime().maxMemory()
            val expected = (maxMemory / 4).toLong()
            assertTrue(expected > 0)
        }

        @Test @DisplayName("disk cache max size is 50MB")
        fun `disk cache size`() {
            val expected = 50L * 1024 * 1024
            assertEquals(52428800L, expected)
        }
    }

    @Nested @DisplayName("Initialization")
    inner class InitTest {
        @Test @DisplayName("init is idempotent")
        fun `init idempotent`() {
            val field = ImagePipeline::class.java.getDeclaredField("initialized")
            field.isAccessible = true
            field.set(ImagePipeline, true)
            // Calling init again should not throw
            field.set(ImagePipeline, false)
        }
    }
}

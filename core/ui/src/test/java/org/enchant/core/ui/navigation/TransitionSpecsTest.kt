package org.enchant.core.ui.navigation

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertSame

@DisplayName("TransitionSpecs")
class TransitionSpecsTest {

    @Nested
    @DisplayName("HorizontalSlide")
    inner class HorizontalSlideTests {

        @Test
        @DisplayName("transitionSpec is a non-null lambda")
        fun `transitionSpec exists and is non-null`() {
            val spec = TransitionSpecs.HorizontalSlide.transitionSpec
            assertNotNull(spec)
        }

        @Test
        @DisplayName("popTransitionSpec is a non-null lambda")
        fun `popTransitionSpec exists and is non-null`() {
            val spec = TransitionSpecs.HorizontalSlide.popTransitionSpec
            assertNotNull(spec)
        }

        @Test
        @DisplayName("predictivePopTransitionSpec is a non-null lambda")
        fun `predictivePopTransitionSpec exists and is non-null`() {
            val spec = TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec
            assertNotNull(spec)
        }

        @Test
        @DisplayName("transitionSpec is a function type")
        fun `transitionSpec is a function`() {
            val spec = TransitionSpecs.HorizontalSlide.transitionSpec
            assertTrue(spec is Function1<*, *>)
        }

        @Test
        @DisplayName("popTransitionSpec is a function type")
        fun `popTransitionSpec is a function`() {
            val spec = TransitionSpecs.HorizontalSlide.popTransitionSpec
            assertTrue(spec is Function1<*, *>)
        }

        @Test
        @DisplayName("predictivePopTransitionSpec is a callable function type")
        fun `predictivePopTransitionSpec is callable`() {
            val spec = TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec
            assertNotNull(spec)
        }
    }

    @Nested
    @DisplayName("VerticalSlide")
    inner class VerticalSlideTests {

        @Test
        @DisplayName("transitionSpec is a non-null lambda")
        fun `transitionSpec exists and is non-null`() {
            val spec = TransitionSpecs.VerticalSlide.transitionSpec
            assertNotNull(spec)
        }

        @Test
        @DisplayName("popTransitionSpec is a non-null lambda")
        fun `popTransitionSpec exists and is non-null`() {
            val spec = TransitionSpecs.VerticalSlide.popTransitionSpec
            assertNotNull(spec)
        }

        @Test
        @DisplayName("predictivePopTransitionSpec is a non-null lambda")
        fun `predictivePopTransitionSpec exists and is non-null`() {
            val spec = TransitionSpecs.VerticalSlide.predictivePopTransitionSpec
            assertNotNull(spec)
        }

        @Test
        @DisplayName("transitionSpec is a function type")
        fun `transitionSpec is a function`() {
            val spec = TransitionSpecs.VerticalSlide.transitionSpec
            assertTrue(spec is Function1<*, *>)
        }

        @Test
        @DisplayName("popTransitionSpec is a function type")
        fun `popTransitionSpec is a function`() {
            val spec = TransitionSpecs.VerticalSlide.popTransitionSpec
            assertTrue(spec is Function1<*, *>)
        }

        @Test
        @DisplayName("predictivePopTransitionSpec is a callable function type")
        fun `predictivePopTransitionSpec is callable`() {
            val spec = TransitionSpecs.VerticalSlide.predictivePopTransitionSpec
            assertNotNull(spec)
        }
    }

    @Nested
    @DisplayName("Fade")
    inner class FadeTests {

        @Test
        @DisplayName("transitionSpec is a non-null lambda")
        fun `transitionSpec exists and is non-null`() {
            val spec = TransitionSpecs.Fade.transitionSpec
            assertNotNull(spec)
        }

        @Test
        @DisplayName("popTransitionSpec is a non-null lambda")
        fun `popTransitionSpec exists and is non-null`() {
            val spec = TransitionSpecs.Fade.popTransitionSpec
            assertNotNull(spec)
        }

        @Test
        @DisplayName("predictivePopTransitionSpec is a non-null lambda")
        fun `predictivePopTransitionSpec exists and is non-null`() {
            val spec = TransitionSpecs.Fade.predictivePopTransitionSpec
            assertNotNull(spec)
        }

        @Test
        @DisplayName("transitionSpec is a function type")
        fun `transitionSpec is a function`() {
            val spec = TransitionSpecs.Fade.transitionSpec
            assertTrue(spec is Function1<*, *>)
        }

        @Test
        @DisplayName("popTransitionSpec is a function type")
        fun `popTransitionSpec is a function`() {
            val spec = TransitionSpecs.Fade.popTransitionSpec
            assertTrue(spec is Function1<*, *>)
        }

        @Test
        @DisplayName("predictivePopTransitionSpec is a callable function type")
        fun `predictivePopTransitionSpec is callable`() {
            val spec = TransitionSpecs.Fade.predictivePopTransitionSpec
            assertNotNull(spec)
        }

        @Test
        @DisplayName("Fade is the same object each time accessed")
        fun `Fade is singleton`() {
            val fade1 = TransitionSpecs.Fade
            val fade2 = TransitionSpecs.Fade
            assertSame(fade1, fade2)
        }
    }

    @Nested
    @DisplayName("Object structure")
    inner class ObjectStructureTests {

        @Test
        @DisplayName("HorizontalSlide exists as a non-null object")
        fun `HorizontalSlide exists`() {
            val obj = TransitionSpecs.HorizontalSlide
            assertNotNull(obj)
        }

        @Test
        @DisplayName("VerticalSlide exists as a non-null object")
        fun `VerticalSlide exists`() {
            val obj = TransitionSpecs.VerticalSlide
            assertNotNull(obj)
        }

        @Test
        @DisplayName("Fade exists as a non-null object")
        fun `Fade exists`() {
            val obj = TransitionSpecs.Fade
            assertNotNull(obj)
        }

        @Test
        @DisplayName("TransitionSpecs is a singleton object")
        fun `TransitionSpecs is a singleton`() {
            val obj = TransitionSpecs
            assertSame(obj, TransitionSpecs)
        }

        @Test
        @DisplayName("HorizontalSlide is a singleton object")
        fun `HorizontalSlide is a singleton`() {
            val h1 = TransitionSpecs.HorizontalSlide
            val h2 = TransitionSpecs.HorizontalSlide
            assertSame(h1, h2)
        }

        @Test
        @DisplayName("VerticalSlide is a singleton object")
        fun `VerticalSlide is a singleton`() {
            val v1 = TransitionSpecs.VerticalSlide
            val v2 = TransitionSpecs.VerticalSlide
            assertSame(v1, v2)
        }
    }
}
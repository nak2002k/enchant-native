package org.enchant.core.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.reflect.full.memberProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame

@DisplayName("TransitionSpecs")
class TransitionSpecsTest {

    private fun createFakeScene(): Scene<NavKey> {
        val fakeNavKey = object : NavKey {
            override val id: String = "test"
        }
        return Scene(listOf(), listOf(fakeNavKey), null, fakeNavKey)
    }

    private fun createFakeAnimatedContentScope(): AnimatedContentTransitionScope<Scene<NavKey>> {
        return object : AnimatedContentTransitionScope<Scene<NavKey>> {
            override val isContainerFocused: Boolean = true
            override val isTargetContainerFocused: Boolean = false
            override val transitionDirection: AnimatedContentTransitionScope<*>.TransitionDirection =
                AnimatedContentTransitionScope.TransitionDirection.UNSPECIFIED
        }
    }

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
        @DisplayName("transitionSpec returns a valid ContentTransform")
        fun `transitionSpec returns valid ContentTransform`() {
            val scope = createFakeAnimatedContentScope()
            val spec = TransitionSpecs.HorizontalSlide.transitionSpec
            val result = spec.invoke(scope)
            assertNotNull(result)
            assertNotNull(result.initialContentTransform)
            assertNotNull(result.targetContentTransform)
        }

        @Test
        @DisplayName("popTransitionSpec returns a valid ContentTransform")
        fun `popTransitionSpec returns valid ContentTransform`() {
            val scope = createFakeAnimatedContentScope()
            val spec = TransitionSpecs.HorizontalSlide.popTransitionSpec
            val result = spec.invoke(scope)
            assertNotNull(result)
            assertNotNull(result.initialContentTransform)
            assertNotNull(result.targetContentTransform)
        }

        @Test
        @DisplayName("predictivePopTransitionSpec accepts @NavigationEvent.SwipeEdge Int and returns ContentTransform")
        fun `predictivePopTransitionSpec accepts swipe edge parameter`() {
            val scope = createFakeAnimatedContentScope()
            val spec = TransitionSpecs.HorizontalSlide.predictivePopTransitionSpec
            val result = spec.invoke(scope, 0)
            assertNotNull(result)
            assertNotNull(result.initialContentTransform)
            assertNotNull(result.targetContentTransform)
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
        @DisplayName("transitionSpec returns a valid ContentTransform")
        fun `transitionSpec returns valid ContentTransform`() {
            val scope = createFakeAnimatedContentScope()
            val spec = TransitionSpecs.VerticalSlide.transitionSpec
            val result = spec.invoke(scope)
            assertNotNull(result)
            assertNotNull(result.initialContentTransform)
            assertNotNull(result.targetContentTransform)
        }

        @Test
        @DisplayName("popTransitionSpec returns a valid ContentTransform")
        fun `popTransitionSpec returns valid ContentTransform`() {
            val scope = createFakeAnimatedContentScope()
            val spec = TransitionSpecs.VerticalSlide.popTransitionSpec
            val result = spec.invoke(scope)
            assertNotNull(result)
            assertNotNull(result.initialContentTransform)
            assertNotNull(result.targetContentTransform)
        }

        @Test
        @DisplayName("predictivePopTransitionSpec accepts @NavigationEvent.SwipeEdge Int and returns ContentTransform")
        fun `predictivePopTransitionSpec accepts swipe edge parameter`() {
            val scope = createFakeAnimatedContentScope()
            val spec = TransitionSpecs.VerticalSlide.predictivePopTransitionSpec
            val result = spec.invoke(scope, 0)
            assertNotNull(result)
            assertNotNull(result.initialContentTransform)
            assertNotNull(result.targetContentTransform)
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
        @DisplayName("transitionSpec returns a valid ContentTransform")
        fun `transitionSpec returns valid ContentTransform`() {
            val scope = createFakeAnimatedContentScope()
            val spec = TransitionSpecs.Fade.transitionSpec
            val result = spec.invoke(scope)
            assertNotNull(result)
            assertNotNull(result.initialContentTransform)
            assertNotNull(result.targetContentTransform)
        }

        @Test
        @DisplayName("popTransitionSpec returns a valid ContentTransform")
        fun `popTransitionSpec returns valid ContentTransform`() {
            val scope = createFakeAnimatedContentScope()
            val spec = TransitionSpecs.Fade.popTransitionSpec
            val result = spec.invoke(scope)
            assertNotNull(result)
            assertNotNull(result.initialContentTransform)
            assertNotNull(result.targetContentTransform)
        }

        @Test
        @DisplayName("predictivePopTransitionSpec accepts @NavigationEvent.SwipeEdge Int and returns ContentTransform")
        fun `predictivePopTransitionSpec accepts swipe edge parameter`() {
            val scope = createFakeAnimatedContentScope()
            val spec = TransitionSpecs.Fade.predictivePopTransitionSpec
            val result = spec.invoke(scope, 0)
            assertNotNull(result)
            assertNotNull(result.initialContentTransform)
            assertNotNull(result.targetContentTransform)
        }

        @Test
        @DisplayName("Fade.transitionSpec and Fade.popTransitionSpec return equal ContentTransforms")
        fun `popTransitionSpec equals transitionSpec for Fade`() {
            val scope = createFakeAnimatedContentScope()
            val transition = TransitionSpecs.Fade.transitionSpec.invoke(scope)
            val pop = TransitionSpecs.Fade.popTransitionSpec.invoke(scope)
            assertEquals(transition.initialContentTransform, pop.initialContentTransform)
            assertEquals(transition.targetContentTransform, pop.targetContentTransform)
        }
    }

    @Nested
    @DisplayName("Object structure")
    inner class ObjectStructureTests {

        @Test
        @DisplayName("HorizontalSlide is a nested object inside TransitionSpecs")
        fun `HorizontalSlide exists`() {
            val obj = TransitionSpecs.HorizontalSlide
            assertNotNull(obj)
        }

        @Test
        @DisplayName("VerticalSlide is a nested object inside TransitionSpecs")
        fun `VerticalSlide exists`() {
            val obj = TransitionSpecs.VerticalSlide
            assertNotNull(obj)
        }

        @Test
        @DisplayName("Fade is a nested object inside TransitionSpecs")
        fun `Fade exists`() {
            val obj = TransitionSpecs.Fade
            assertNotNull(obj)
        }

        @Test
        @DisplayName("TransitionSpecs is an object (singleton)")
        fun `TransitionSpecs is an object`() {
            val clazz = TransitionSpecs::class
            assertTrue(clazz.isInstance(TransitionSpecs))
            assertSame(TransitionSpecs, TransitionSpecs)
        }
    }
}
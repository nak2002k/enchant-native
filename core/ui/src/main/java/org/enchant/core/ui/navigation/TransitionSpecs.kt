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

object TransitionSpecs {

    object HorizontalSlide {
        private const val DURATION = 200

        val transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
            (
                slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(DURATION))
                + fadeIn(animationSpec = tween(DURATION))
            ) togetherWith (
                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(DURATION))
                + fadeOut(animationSpec = tween(DURATION))
            )
        }

        val popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
            (
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(DURATION))
                + fadeIn(animationSpec = tween(DURATION))
            ) togetherWith (
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(DURATION))
                + fadeOut(animationSpec = tween(DURATION))
            )
        }

        val predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform = {
            (
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(DURATION))
                + fadeIn(animationSpec = tween(DURATION))
            ) togetherWith (
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(DURATION))
                + fadeOut(animationSpec = tween(DURATION))
            )
        }
    }

    object VerticalSlide {
        private const val DURATION = 300

        val transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
            (
                slideInVertically(initialOffsetY = { it }, animationSpec = tween(DURATION))
                + fadeIn(animationSpec = tween(DURATION))
            ) togetherWith
                fadeOut(animationSpec = tween(DURATION))
        }

        val popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
            val slideOut = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(DURATION))
            val fadeOutExit = fadeOut(animationSpec = tween(DURATION))
            fadeIn(animationSpec = tween(DURATION)) togetherWith (slideOut + fadeOutExit)
        }

        val predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform = {
            val slideOut = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(DURATION))
            val fadeOutExit = fadeOut(animationSpec = tween(DURATION))
            fadeIn(animationSpec = tween(DURATION)) togetherWith (slideOut + fadeOutExit)
        }
    }

    object Fade {
        private const val DURATION = 200

        val transitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
            fadeIn(animationSpec = tween(DURATION)) togetherWith fadeOut(animationSpec = tween(DURATION))
        }

        val popTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.() -> ContentTransform = {
            fadeIn(animationSpec = tween(DURATION)) togetherWith fadeOut(animationSpec = tween(DURATION))
        }

        val predictivePopTransitionSpec: AnimatedContentTransitionScope<Scene<NavKey>>.(@NavigationEvent.SwipeEdge Int) -> ContentTransform = {
            fadeIn(animationSpec = tween(DURATION)) togetherWith fadeOut(animationSpec = tween(DURATION))
        }
    }
}
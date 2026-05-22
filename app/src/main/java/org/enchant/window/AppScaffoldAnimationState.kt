package org.enchant.window

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.GraphicsLayerScope
import kotlinx.coroutines.launch

private const val ENTER_DURATION = 350
private const val EXIT_DURATION = 300
private const val SEEK_DURATION = 200

@Stable
class AppScaffoldAnimationState {
    var alpha: Animatable<Float, AnimationVector1D> = Animatable(1f)
    var offsetX: Animatable<Float, AnimationVector1D> = Animatable(0f)
    var offsetY: Animatable<Float, AnimationVector1D> = Animatable(0f)
    var scale: Animatable<Float, AnimationVector1D> = Animatable(1f)
    var cornerRadius: Animatable<Float, AnimationVector1D> = Animatable(0f)

    suspend fun animateToEnterState() {
        kotlinx.coroutines.coroutineScope {
            launch { alpha.animateTo(1f, tween(ENTER_DURATION)) }
            launch { offsetX.animateTo(0f, tween(ENTER_DURATION)) }
            launch { offsetY.animateTo(0f, tween(ENTER_DURATION)) }
            launch { scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
            launch { cornerRadius.animateTo(0f, tween(ENTER_DURATION)) }
        }
    }

    suspend fun animateToExitState(
        exitOffsetX: Float = 0f,
        exitOffsetY: Float = 0f,
        exitScale: Float = 1f
    ) {
        kotlinx.coroutines.coroutineScope {
            launch { alpha.animateTo(0f, tween(EXIT_DURATION)) }
            launch { offsetX.animateTo(exitOffsetX, tween(EXIT_DURATION)) }
            launch { offsetY.animateTo(exitOffsetY, tween(EXIT_DURATION)) }
            launch { scale.animateTo(exitScale, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
            launch { cornerRadius.animateTo(0f, tween(EXIT_DURATION)) }
        }
    }

    suspend fun animateToSeekState(fraction: Float) {
        val clampedFraction = fraction.coerceIn(0f, 1f)
        kotlinx.coroutines.coroutineScope {
            launch { alpha.animateTo(1f - clampedFraction * 0.3f, tween(SEEK_DURATION)) }
            launch { offsetX.animateTo(clampedFraction * -50f, tween(SEEK_DURATION)) }
            launch { offsetY.animateTo(clampedFraction * 20f, tween(SEEK_DURATION)) }
            launch { scale.animateTo(1f - clampedFraction * 0.05f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
            launch { cornerRadius.animateTo(clampedFraction * 16f, tween(SEEK_DURATION)) }
        }
    }

    suspend fun animateToReleaseState() {
        animateToExitState(exitOffsetX = -100f, exitScale = 0.95f)
    }

    fun applyChildValues(): GraphicsLayerScope.() -> Unit = {
        val alphaVal = alpha.value
        val offsetXVal = offsetX.value
        val offsetYVal = offsetY.value
        val scaleVal = scale.value
        this.alpha = alphaVal
        this.translationX = offsetXVal
        this.translationY = offsetYVal
        this.scaleX = scaleVal
        this.scaleY = scaleVal
    }
}

@Composable
fun rememberAppScaffoldAnimationState(): AppScaffoldAnimationState {
    return remember { AppScaffoldAnimationState() }
}

interface AppScaffoldAnimationStateFactory {
    fun getListAnimationState(state: Any): AppScaffoldAnimationState
    fun getDetailAnimationState(state: Any): AppScaffoldAnimationState

    object Default : AppScaffoldAnimationStateFactory {
        override fun getListAnimationState(state: Any): AppScaffoldAnimationState = AppScaffoldAnimationState()
        override fun getDetailAnimationState(state: Any): AppScaffoldAnimationState = AppScaffoldAnimationState()
    }
}
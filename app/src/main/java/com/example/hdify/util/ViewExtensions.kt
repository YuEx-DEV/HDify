package com.example.hdify.util

import android.os.Bundle
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.Interpolator
import androidx.annotation.IdRes
import androidx.core.view.animation.PathInterpolatorCompat
import androidx.navigation.NavController
import com.example.hdify.logging.HdLogger

/**
 * Ultra-smooth fluid physics curves for natural Material You & iOS-grade transitions.
 */
val FluidDecelerateInterpolator: Interpolator = PathInterpolatorCompat.create(0.16f, 1.0f, 0.3f, 1.0f)
val FluidSpringInterpolator: Interpolator = PathInterpolatorCompat.create(0.25f, 1.32f, 0.4f, 1.0f)

/**
 * Applies Material You tactile spring bounce effect on touch events.
 * Scales down gently on press, springs back with fluid overshoot on release.
 */
fun View.applyMaterialBounce(scaleDown: Float = 0.965f) {
    setOnTouchListener { v, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                v.animate().cancel()
                v.animate()
                    .scaleX(scaleDown)
                    .scaleY(scaleDown)
                    .setDuration(90)
                    .setInterpolator(FluidDecelerateInterpolator)
                    .withLayer()
                    .start()
            }
            MotionEvent.ACTION_UP -> {
                v.animate().cancel()
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(220)
                    .setInterpolator(FluidSpringInterpolator)
                    .withLayer()
                    .start()
                try {
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                } catch (_: Throwable) {}
            }
            MotionEvent.ACTION_CANCEL -> {
                v.animate().cancel()
                v.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(150)
                    .setInterpolator(FluidDecelerateInterpolator)
                    .withLayer()
                    .start()
            }
        }
        false // Allow normal onClick / single click handlers to fire
    }
}

fun View.applyLiquidBounce(scaleDown: Float = 0.965f) = applyMaterialBounce(scaleDown)

/**
 * Combines Material You tactile touch bounce with debounced click action.
 */
fun View.setOnMaterialClickListener(debounceTime: Long = 400L, action: (View) -> Unit) {
    applyMaterialBounce()
    setOnSingleClickListener(debounceTime, action)
}

fun View.setOnLiquidClickListener(debounceTime: Long = 400L, action: (View) -> Unit) =
    setOnMaterialClickListener(debounceTime, action)

fun View.setOnSingleClickListener(debounceTime: Long = 500L, action: (View) -> Unit) {
    var lastClickTime = 0L
    setOnClickListener { view ->
        val currentTime = SystemClock.elapsedRealtime()
        if (currentTime - lastClickTime >= debounceTime) {
            lastClickTime = currentTime
            action(view)
        }
    }
}

fun NavController.safeNavigate(@IdRes resId: Int, args: Bundle? = null) {
    try {
        val currentDest = currentDestination ?: return
        val action = currentDest.getAction(resId)
        if (action != null) {
            navigate(resId, args)
        } else if (currentDest.id != resId && findDestination(resId) != null) {
            navigate(resId, args)
        }
    } catch (e: Exception) {
        HdLogger.w("Navigation", "Safe navigate skipped for destination ID $resId: ${e.message}")
    }
}

fun NavController.safePopBackStack(): Boolean {
    return try {
        popBackStack()
    } catch (e: Exception) {
        HdLogger.w("Navigation", "Safe popBackStack skipped: ${e.message}")
        false
    }
}

/**
 * Smooth Material You staggered cascade entrance animation.
 * Views gently translate upwards and fade in with fluid deceleration curve and hardware layer caching.
 */
fun animateCascadeEntrance(
    views: List<View>,
    startDelay: Long = 15L,
    staggerDelay: Long = 32L,
    duration: Long = 320L
) {
    views.forEachIndexed { index, view ->
        view.animate().cancel()
        view.alpha = 0f
        view.translationY = 22f
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setStartDelay(startDelay + (index * staggerDelay))
            .setInterpolator(FluidDecelerateInterpolator)
            .withLayer()
            .start()
    }
}

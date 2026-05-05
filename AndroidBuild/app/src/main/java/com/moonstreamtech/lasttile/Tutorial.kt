package com.moonstreamtech.lasttile

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Five interactive tutorial steps plus a terminal Done state. The
 * `index` is the 1-based step counter the UI shows ("1 / 5"). Done has
 * index 0 because the counter is hidden once the tutorial finishes.
 */
sealed class TutorialStep(val index: Int) {
    object Merge : TutorialStep(1)
    object Frame : TutorialStep(2)
    object Hazard : TutorialStep(3)
    object Shield : TutorialStep(4)
    object Leaderboard : TutorialStep(5)
    object Done : TutorialStep(0)

    companion object {
        const val TOTAL_INTERACTIVE_STEPS = 5
    }
}

data class TutorialState(
    val active: Boolean,
    val currentStep: TutorialStep,
    val instructionTextRes: Int,
    val ctaTextRes: Int,
    // Set by [TutorialController.markStepCompleted] when an auto-advanced
    // step's trigger condition fires. The overlay swaps to a success
    // beat (checkmark + localized "nice merge / frame opened / hazard
    // cleared" message) and the GameScreen waits ~2s before calling
    // next(), giving the user a moment to register what just happened.
    val stepCompleted: Boolean = false
)

/**
 * Owns the tutorial's persistent flag (`tutorial_v1_seen`) and the
 * in-memory step pointer. Compose observes [state] directly via
 * mutableStateOf so step changes recompose the overlay automatically.
 *
 * Backgrounding mid-tutorial does NOT persist the current step — on
 * the next process start, if hasSeenTutorial is still false the
 * tutorial restarts from Merge. This is the simpler-of-the-two
 * approaches the spec offered.
 */
class TutorialController(private val prefs: SharedPreferences) {
    var state: TutorialState by mutableStateOf(IDLE_STATE)
        private set

    val hasSeenTutorial: Boolean
        get() = prefs.getBoolean(KEY_SEEN, false)

    fun start() {
        state = stateForStep(TutorialStep.Merge)
    }

    fun next() {
        val nextStep = when (state.currentStep) {
            TutorialStep.Merge -> TutorialStep.Frame
            TutorialStep.Frame -> TutorialStep.Hazard
            TutorialStep.Hazard -> TutorialStep.Shield
            TutorialStep.Shield -> TutorialStep.Leaderboard
            TutorialStep.Leaderboard -> TutorialStep.Done
            TutorialStep.Done -> TutorialStep.Done
        }
        if (nextStep == TutorialStep.Done) {
            finish()
        } else {
            state = stateForStep(nextStep)
        }
    }

    fun skip() {
        finish()
    }

    /**
     * Marks the current step's trigger condition as satisfied without
     * advancing yet. The overlay reads [TutorialState.stepCompleted] to
     * swap into the success beat (checkmark + localized success copy);
     * GameScreen schedules the actual [next] call after a short delay so
     * the user can register that their action worked.
     *
     * No-op when the tutorial isn't active or when the step is already
     * marked completed (so a second auto-advance trigger firing during
     * the celebrate beat doesn't reset anything).
     */
    fun markStepCompleted() {
        if (!state.active) return
        if (state.stepCompleted) return
        state = state.copy(stepCompleted = true)
    }

    private fun finish() {
        state = IDLE_STATE
        prefs.edit().putBoolean(KEY_SEEN, true).apply()
    }

    private fun stateForStep(step: TutorialStep): TutorialState {
        val instructionRes = when (step) {
            TutorialStep.Merge -> R.string.tutorial_step_merge_instruction
            TutorialStep.Frame -> R.string.tutorial_step_frame_instruction
            TutorialStep.Hazard -> R.string.tutorial_step_hazard_instruction
            TutorialStep.Shield -> R.string.tutorial_step_shield_instruction
            TutorialStep.Leaderboard -> R.string.tutorial_step_leaderboard_instruction
            TutorialStep.Done -> 0
        }
        return TutorialState(
            active = true,
            currentStep = step,
            instructionTextRes = instructionRes,
            ctaTextRes = R.string.tutorial_cta_got_it
        )
    }

    companion object {
        private const val KEY_SEEN = "tutorial_v1_seen"
        private val IDLE_STATE = TutorialState(
            active = false,
            currentStep = TutorialStep.Done,
            instructionTextRes = 0,
            ctaTextRes = 0
        )
    }
}

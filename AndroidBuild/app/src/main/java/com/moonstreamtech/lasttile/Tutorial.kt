package com.moonstreamtech.lasttile

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Six interactive tutorial steps plus a terminal Done state. The
 * `index` is the 1-based step counter the UI shows ("1 / 6"). Done has
 * index 0 because the counter is hidden once the tutorial finishes.
 */
sealed class TutorialStep(val index: Int) {
    object Merge : TutorialStep(1)
    object Frame : TutorialStep(2)
    object Hazard : TutorialStep(3)
    object Shield : TutorialStep(4)
    object Leaderboard : TutorialStep(5)
    object Username : TutorialStep(6)
    object Done : TutorialStep(0)

    companion object {
        const val TOTAL_INTERACTIVE_STEPS = 6
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
    val stepCompleted: Boolean = false,
    // Board cells the spotlight overlay should render bright + pulsing
    // for the current step. Coordinates mirror the tiles
    // [com.moonstreamtech.lasttile.GameState.setupForTutorial] places —
    // when one moves, the other has to move with it. Steps that have no
    // board target (Leaderboard, Username) leave this empty. The shield
    // card highlight on step 4 is NOT in this set; it's a UI element
    // outside the board grid and is gated separately on
    // currentStep == Shield.
    val highlightedCells: Set<Pair<Int, Int>> = emptySet(),
    // First-launch tutorials cannot be skipped or dismissed — Skip is
    // hidden, hardware back is blocked, and step 6 only completes after
    // the player commits a username. Re-triggers from the "?" button
    // run with this off.
    val mandatory: Boolean = false
)

/**
 * Owns the tutorial's persistent flag (`tutorial_completed_once`) and
 * the in-memory step pointer. Compose observes [state] directly via
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
        get() = prefs.getBoolean(KEY_COMPLETED_ONCE, false)

    /**
     * Starts the tutorial from step 1.
     *
     * [mandatory] = true on the first-launch path so the overlay hides
     * Skip, blocks back-press, and refuses to finish until step 6's
     * username save succeeds. Re-triggers from the "?" help button pass
     * false to allow normal dismissal.
     */
    fun start(mandatory: Boolean = false) {
        state = stateForStep(TutorialStep.Merge, mandatory)
    }

    fun next() {
        val nextStep = when (state.currentStep) {
            TutorialStep.Merge -> TutorialStep.Frame
            TutorialStep.Frame -> TutorialStep.Hazard
            TutorialStep.Hazard -> TutorialStep.Shield
            TutorialStep.Shield -> TutorialStep.Leaderboard
            TutorialStep.Leaderboard -> TutorialStep.Username
            TutorialStep.Username -> TutorialStep.Done
            TutorialStep.Done -> TutorialStep.Done
        }
        if (nextStep == TutorialStep.Done) {
            finish()
        } else {
            state = stateForStep(nextStep, state.mandatory)
        }
    }

    /**
     * Optional skip path. Honoured only when the tutorial isn't running
     * in mandatory mode (first launch). Mandatory tutorials can only be
     * completed by saving a username on step 6.
     */
    fun skip() {
        if (state.mandatory) return
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
        prefs.edit().putBoolean(KEY_COMPLETED_ONCE, true).apply()
    }

    private fun stateForStep(step: TutorialStep, mandatory: Boolean): TutorialState {
        val instructionRes = when (step) {
            TutorialStep.Merge -> R.string.tutorial_step_merge_instruction
            TutorialStep.Frame -> R.string.tutorial_step_frame_instruction
            TutorialStep.Hazard -> R.string.tutorial_step_hazard_instruction
            TutorialStep.Shield -> R.string.tutorial_step_shield_instruction
            TutorialStep.Leaderboard -> R.string.tutorial_step_leaderboard_instruction_v2
            TutorialStep.Username -> R.string.tutorial_step_username_instruction
            TutorialStep.Done -> 0
        }
        // Steps 5 and 6 require a UI tap to advance, so the "Got it"
        // CTA is hidden — the user must perform the action.
        val ctaRes = when (step) {
            TutorialStep.Leaderboard, TutorialStep.Username -> 0
            else -> R.string.tutorial_cta_got_it
        }
        // Mirrors GameState.setupForTutorial — keep these tile
        // coordinates in lockstep with the tiles that get placed on
        // the scripted board.
        val cells: Set<Pair<Int, Int>> = when (step) {
            TutorialStep.Merge -> setOf(3 to 2, 3 to 3)
            TutorialStep.Frame -> setOf(3 to 2, 3 to 3)
            TutorialStep.Hazard -> setOf(2 to 2, 3 to 3, 4 to 4)
            TutorialStep.Shield -> setOf(3 to 3)
            TutorialStep.Leaderboard, TutorialStep.Username, TutorialStep.Done -> emptySet()
        }
        return TutorialState(
            active = true,
            currentStep = step,
            instructionTextRes = instructionRes,
            ctaTextRes = ctaRes,
            highlightedCells = cells,
            mandatory = mandatory
        )
    }

    companion object {
        // v0.2.0: superseded the legacy tutorial_v1_seen flag. Migration
        // from the legacy key happens once at process start in
        // [migrateLegacyTutorialFlag], called from
        // [LastTileApplication.onCreate].
        private const val KEY_COMPLETED_ONCE = "tutorial_completed_once"
        private const val LEGACY_KEY_SEEN = "tutorial_v1_seen"

        private val IDLE_STATE = TutorialState(
            active = false,
            currentStep = TutorialStep.Done,
            instructionTextRes = 0,
            ctaTextRes = 0
        )

        /**
         * One-time migration: users who completed the v0.1.x five-step
         * tutorial (legacy flag tutorial_v1_seen=true) shouldn't be
         * forced through the new mandatory step 6 flow. We opt them
         * out by promoting the legacy flag to tutorial_completed_once
         * the first time this code runs after install upgrade.
         */
        fun migrateLegacyTutorialFlag(prefs: SharedPreferences) {
            if (prefs.contains(KEY_COMPLETED_ONCE)) return
            if (prefs.contains(LEGACY_KEY_SEEN) &&
                prefs.getBoolean(LEGACY_KEY_SEEN, false)
            ) {
                prefs.edit().putBoolean(KEY_COMPLETED_ONCE, true).apply()
            }
        }
    }
}

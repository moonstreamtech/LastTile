package com.moonstreamtech.lasttile

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.random.Random
import org.json.JSONArray
import org.json.JSONObject

class GameState(
    private val prefs: SharedPreferences? = null,
    private val leaderboard: LeaderboardProvider? = null,
    // Called once per run with the final score after the local leaderboard
    // has accepted the entry. Used by the UI layer to forward the score to
    // an online provider (e.g. Google Play Games Services). Failures here
    // must not affect game state — submission is fire-and-forget.
    private val onRunSubmitted: ((Int) -> Unit)? = null,
    // Returns true while the interactive tutorial is on screen. Used to
    // suppress local + online leaderboard writes for scripted tutorial
    // boards (we never want a tutorial run to chart). The lambda is
    // queried inside [submitRunOnce] and checked again from the live-PB
    // hook in [mergeTiles] so a manual Restart, a hand-engineered game-
    // over inside a tutorial step, and the tutorial's own controlled
    // merges all skip submission deterministically.
    private val isTutorialActive: () -> Boolean = { false },
    // Returns the live tutorial step, or null when the tutorial is
    // inactive. Distinct from [isTutorialActive] because the shield
    // gate in [applyShieldOn] only blocks during non-Shield tutorial
    // steps — Step 4 is the one place during the tutorial where a
    // shield drag should still fire (against the scripted Fire(4)).
    // Default { null } keeps tests / preview-only constructions
    // unchanged.
    private val currentTutorialStep: () -> TutorialStep? = { null }
) {
    companion object {
        const val INITIAL_ACTIVE = 5
        const val INITIAL_SIZE = 7
        private const val START_TILES = 4
        private const val FIRE_LIFE = 5
        private const val POISON_FUSE = 7
        private const val ICE_LIFE = 3
        // Cleanse thresholds. Triggered when the player creates a merge whose
        // resulting value matches the tier (or the universal panic value).
        // Fixed values on the base 7×7 board; shifted up as the board grows.
        private const val CLEAR_ICE_BASE = 16     // 8+8 = 16  → cleanse oldest ice
        private const val CLEAR_FIRE_BASE = 32    // 16+16 = 32 → cleanse oldest fire
        private const val CLEAR_POISON_BASE = 64  // 32+32 = 64 → cleanse oldest poison
        private const val CLEAR_ALL_BASE = 128    // 64+64 = 128 → wipe every hazard
        private const val SPLIT_MIN = 2
        private const val DOUBLE_TAP_MS = 350L
        private const val HAZARD_RESPAWN_COOLDOWN = 3
        // Shield economy. Initial grant on first launch; each rewarded
        // video play credits SHIELD_REWARD_GRANT additional shields.
        const val SHIELD_INITIAL = 1
        const val SHIELD_REWARD_GRANT = 3
        private const val SHIELD_PREF_KEY = "shield_count"
    }

    var size by mutableStateOf(INITIAL_SIZE)
        private set

    var board by mutableStateOf(emptyBoard(INITIAL_SIZE))
        private set

    var unlocked by mutableStateOf(initialUnlocked(INITIAL_SIZE))
        private set

    var score by mutableStateOf(0)
        private set

    var turn by mutableStateOf(0)
        private set

    var selected by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    var gameOver by mutableStateOf(false)
        private set

    var combo by mutableStateOf(0)
        private set

    var lastHazardsCleared by mutableStateOf(0)
        private set

    var lastSplit by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    // Cells the next threshold-merge unlocks would have to pay back before
    // any cells actually open. Charged when the player splits a tile that
    // sits on an unlock threshold; spent the next time a merge crosses one.
    // Carries across board growth so the anti-farm rule survives expansions.
    var unlockDebt by mutableStateOf(0)
        private set

    // Most recent tile the player or the engine touched in a meaningful
    // way (last merge target, last split origin, last selection). The
    // viewport uses this to auto-follow the action so the player rarely
    // needs to pan a large board manually.
    var lastFocus by mutableStateOf<Pair<Int, Int>?>(null)
        private set

    var unlockedTargets by mutableStateOf<Set<Pair<Int, Int>>>(emptySet())
        private set

    var unlockKey by mutableStateOf(0)
        private set

    var pressedTiles by mutableStateOf<Set<Pair<Int, Int>>>(emptySet())
        private set

    var lastSubmittedRank by mutableStateOf(-1)
        private set

    // Shield economy state. shieldCount is persisted under its own
    // SharedPreferences key (independent of the run state, so Restart
    // never wipes it — only Settings → Apps → Clear data does). Shields
    // are applied via drag-and-drop only: drop the shield card onto a
    // hazard tile to cure it back to a Normal of the same value. Tapping
    // the shield card always opens the Earn Shield rewarded-ad dialog.
    var shieldCount by mutableStateOf(SHIELD_INITIAL)
        private set

    // Pre-tutorial shieldCount, captured the first time setupForTutorial
    // is called for the Shield step. Step 4 then forces shieldCount to 1
    // (in memory only — see saveShieldCount gate below) so the demo is
    // consistent regardless of the player's actual shield economy. When
    // the tutorial finishes (Done OR skip) [restoreSavedShieldForTutorial]
    // writes this value back so the player never loses a shield to the
    // tutorial. Null when nothing is captured (e.g. user skipped before
    // ever reaching the Shield step) — restore is a no-op in that case.
    private var savedShieldCountForTutorial: Int? = null

    // Highest score already pushed to the online leaderboard during this
    // run. We forward every new in-run personal best to GPGS so growBoard's
    // effectively-endless runs still produce live online scores. GPGS keeps
    // the max-per-player so duplicate / lower submissions are no-ops.
    private var bestThisRun: Int = 0
    private var submittedThisRun = false
    private var lastTapTime = 0L
    private var lastTapPos: Pair<Int, Int>? = null
    // Counter that ticks on every player action — merge, split, slide and
    // swap. Hazard cooldowns measure their gap in actions, not in turns,
    // because slide / swap don't advance turn but should still let the
    // cooldown breathe.
    private var actionCount = 0
    private var lastFireDeathAction = -HAZARD_RESPAWN_COOLDOWN
    private var lastIceDeathAction = -HAZARD_RESPAWN_COOLDOWN
    private var lastPoisonDeathAction = -HAZARD_RESPAWN_COOLDOWN

    init {
        if (!tryLoad()) restart()
        loadShieldCount()
    }

    private fun loadShieldCount() {
        val p = prefs ?: return
        shieldCount = p.getInt(SHIELD_PREF_KEY, SHIELD_INITIAL).coerceAtLeast(0)
    }

    private fun saveShieldCount() {
        val p = prefs ?: return
        p.edit().putInt(SHIELD_PREF_KEY, shieldCount).apply()
    }

    fun restart() {
        // Manual Restart counts as a completed run — push the current
        // score to the local + online leaderboards before wiping state,
        // so the player's deliberate "I'm done with this run" gesture
        // doesn't lose their score. Endless runs (where natural game-
        // over rarely fires) rely on this entirely. submittedThisRun
        // gates against double-submitting if the player tapped Restart
        // after already hitting game-over.
        submitRunOnce()
        size = INITIAL_SIZE
        unlocked = initialUnlocked(INITIAL_SIZE)
        val b = mutableBoard(INITIAL_SIZE)

        val active = activeCells(unlocked)
        val first = active.random()
        b[first.first][first.second] = Tile.Normal(2)

        val neighborPool = neighbors(first.first, first.second, INITIAL_SIZE)
            .filter { unlocked[it.first][it.second] }
        val second = neighborPool.random()
        b[second.first][second.second] = Tile.Normal(2)

        repeat(START_TILES - 2) {
            val spot = randomEmpty(b) ?: return@repeat
            b[spot.first][spot.second] = Tile.Normal(2)
        }

        board = b.toImmutable()
        score = 0
        turn = 0
        selected = null
        gameOver = false
        combo = 0
        lastHazardsCleared = 0
        lastSplit = null
        unlockedTargets = emptySet()
        pressedTiles = emptySet()
        lastTapTime = 0L
        lastTapPos = null
        submittedThisRun = false
        bestThisRun = 0
        lastSubmittedRank = -1
        actionCount = 0
        // shieldCount is intentionally NOT reset by Restart — the
        // shield economy is a meta-progression resource that persists
        // across runs (only Settings → Apps → Clear data wipes it).
        lastFireDeathAction = -HAZARD_RESPAWN_COOLDOWN
        lastIceDeathAction = -HAZARD_RESPAWN_COOLDOWN
        lastPoisonDeathAction = -HAZARD_RESPAWN_COOLDOWN
        unlockDebt = 0
        lastFocus = null
        save()
    }

    /**
     * Resets the board into a scripted state for [step] of the
     * interactive tutorial. Active region is the standard 5×5 in the
     * middle of a 7×7 board (rows 1..5, cols 1..5). Each step places
     * exactly the tiles the user needs to interact with so the
     * tutorial overlay's auto-advance hooks (which observe GameState
     * fields like combo / unlockedTargets / shieldCount) fire on the
     * correct user action and not on incidental board state.
     *
     * Does NOT call submitRunOnce — the tutorial is gated by
     * isTutorialActive at the submission site, but skipping the call
     * here keeps a tutorial reset from racing the gate. Persists via
     * save() so backgrounding mid-step preserves the scripted layout.
     */
    fun setupForTutorial(step: TutorialStep) {
        // Step 4 → Step 5 transition restores the player's real shield
        // count immediately rather than waiting for the whole tutorial
        // to end. Otherwise Step 5's instruction card sits on top of a
        // KORUMA value that was artificially set to 1 during step 4,
        // and a player who glances at the HUD on step 5 thinks the
        // tutorial just consumed their shield. Idempotent — the
        // restore is a no-op when nothing was captured (e.g. the user
        // skipped before step 4).
        if (step == TutorialStep.Leaderboard) {
            restoreSavedShieldForTutorial()
        }
        size = INITIAL_SIZE
        unlocked = initialUnlocked(INITIAL_SIZE)
        val b = mutableBoard(INITIAL_SIZE)

        when (step) {
            TutorialStep.Merge -> {
                b[3][2] = Tile.Normal(2)
                b[3][3] = Tile.Normal(2)
            }
            TutorialStep.Frame -> {
                // Merging two 32s yields 64, which on a fresh 7×7 board
                // (unlockBase = 64) fires applyExpansion → 1 cell unlocks.
                b[3][2] = Tile.Normal(32)
                b[3][3] = Tile.Normal(32)
            }
            TutorialStep.Hazard -> {
                b[2][2] = Tile.Fire(2, 0)
                b[3][3] = Tile.Ice(2, 0)
                b[4][4] = Tile.Poison(2, 0)
            }
            TutorialStep.Shield -> {
                b[3][3] = Tile.Fire(4, 0)
                // Demo shield: capture the player's real shield count
                // (only on the first entry of this Shield step, so a
                // re-script doesn't overwrite the saved value with our
                // own demo 1) and force a single shield for the drag.
                // restoreSavedShieldForTutorial puts the saved value
                // back when the tutorial ends. We deliberately do NOT
                // call saveShieldCount() here — the in-memory override
                // must not hit disk, otherwise a process kill mid-
                // tutorial would persist shieldCount = 1.
                if (savedShieldCountForTutorial == null) {
                    savedShieldCountForTutorial = shieldCount
                }
                shieldCount = 1
            }
            TutorialStep.Leaderboard -> {
                b[3][3] = Tile.Normal(2)
            }
            TutorialStep.Done -> Unit
        }

        board = b.toImmutable()
        score = 0
        turn = 0
        selected = null
        gameOver = false
        combo = 0
        lastHazardsCleared = 0
        lastSplit = null
        unlockedTargets = emptySet()
        pressedTiles = emptySet()
        lastTapTime = 0L
        lastTapPos = null
        submittedThisRun = false
        bestThisRun = 0
        lastSubmittedRank = -1
        actionCount = 0
        lastFireDeathAction = -HAZARD_RESPAWN_COOLDOWN
        lastIceDeathAction = -HAZARD_RESPAWN_COOLDOWN
        lastPoisonDeathAction = -HAZARD_RESPAWN_COOLDOWN
        unlockDebt = 0
        lastFocus = null

        // shieldCount is set above for the Shield step only. Other
        // steps leave the player's real shield count alone — the demo
        // override is exclusive to step 4 and is restored on tutorial
        // exit by restoreSavedShieldForTutorial.

        save()
    }

    /**
     * Restores the pre-tutorial shieldCount captured by
     * [setupForTutorial] for [TutorialStep.Shield]. Call from the UI
     * layer when the tutorial transitions to inactive (Done OR skip),
     * before any restart() that follows. Idempotent and a no-op when
     * nothing was captured (e.g. user skipped before reaching step 4).
     */
    fun restoreSavedShieldForTutorial() {
        val saved = savedShieldCountForTutorial ?: return
        shieldCount = saved
        savedShieldCountForTutorial = null
        saveShieldCount()
    }

    // One-shot per-run submission to local + GPGS leaderboards. Used by
    // both natural game-over (recordRunIfOver) and manual Restart, so a
    // run's score lands on the leaderboards exactly once regardless of
    // how the player ends it. GPGS is forwarded unconditionally — server-
    // side dedup keeps it cheap, and we want the "final" score to land
    // on the global board even when an earlier in-run PB already did.
    private fun submitRunOnce() {
        if (submittedThisRun) return
        if (isTutorialActive()) return
        val lb = leaderboard ?: return
        if (score <= 0) return
        submittedThisRun = true
        lastSubmittedRank = lb.submit(
            LeaderboardEntry(
                score = score,
                turn = turn,
                maxSize = size,
                timestamp = System.currentTimeMillis()
            )
        )
        if (score > bestThisRun) bestThisRun = score
        runCatching { onRunSubmitted?.invoke(score) }
    }

    private fun recordRunIfOver() {
        if (!gameOver) return
        submitRunOnce()
    }

    fun tryDragMerge(from: Pair<Int, Int>, to: Pair<Int, Int>): Boolean {
        if (gameOver) return false
        if (from == to) return false
        if (!isInBounds(from.first, from.second) || !isInBounds(to.first, to.second)) return false
        if (!unlocked[from.first][from.second] || !unlocked[to.first][to.second]) return false
        if (!isAdjacent(from, to)) return false
        val a = board[from.first][from.second]
        val b = board[to.first][to.second]
        if (isInfected(a) || isInfected(b)) return false
        if (a is Tile.Normal && b is Tile.Normal && a.value == b.value) {
            mergeTiles(from, to, a.value)
            return true
        }
        return false
    }

    fun tap(row: Int, col: Int) {
        if (gameOver) return
        if (!isInBounds(row, col) || !unlocked[row][col]) return

        val now = System.currentTimeMillis()
        val doubleTap = lastTapPos == (row to col) && (now - lastTapTime) < DOUBLE_TAP_MS
        lastTapTime = now
        lastTapPos = row to col

        val current = board[row][col]
        if (isInfected(current)) {
            selected = null
            return
        }

        if (doubleTap) {
            if (current is Tile.Normal && current.value >= SPLIT_MIN) {
                trySplit(row, col, current.value)
                return
            }
            selected = null
            combo = 0
            return
        }

        val sel = selected
        if (sel == null) {
            if (current is Tile.Normal) {
                selected = row to col
                lastFocus = row to col
            }
            return
        }

        if (sel == (row to col)) {
            return
        }

        if (isAdjacent(sel, row to col)) {
            val a = board[sel.first][sel.second]
            val b = current
            if (a is Tile.Normal && b is Tile.Normal && a.value == b.value) {
                mergeTiles(sel, row to col, a.value)
                return
            }
        }

        combo = 0
        selected = if (current is Tile.Normal) row to col else null
        if (selected != null) lastFocus = selected
    }

    private fun trySplit(row: Int, col: Int, value: Int) {
        // A tile that's still under its post-move "pressed" cooldown is
        // locked — tryMove already rejects acting on it, and split is
        // another player-driven action against the same tile, so it
        // must reject too. Without this guard, double-tapping a freshly-
        // moved tile would split it AND silently drop the lock.
        if ((row to col) in pressedTiles) return
        val half = value / 2
        if (half < 1) return

        val b = board.toMutableBoard()
        b[row][col] = Tile.Empty

        val empties = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until size) for (c in 0 until size) {
            if (unlocked[r][c] && b[r][c] == Tile.Empty) empties.add(r to c)
        }
        if (empties.size < 2) return

        empties.shuffle()
        val (p1, p2) = empties[0] to empties[1]
        b[p1.first][p1.second] = Tile.Normal(half)
        b[p2.first][p2.second] = Tile.Normal(half)

        actionCount += 1
        combo = 0
        selected = null
        lastSplit = row to col
        lastFocus = row to col
        lastHazardsCleared = 0
        lastTapPos = null
        // Splitting a tile that sits on an unlock threshold accrues a debt
        // equal to the cells that value normally opens. The next merges
        // that hit unlock thresholds drain the debt before opening cells,
        // which prevents farming extra unlocks via split → re-merge loops.
        val splitPenalty = unlockCountFor(value)
        if (splitPenalty > 0) unlockDebt += splitPenalty
        // Note: no pressedTiles cleanup needed here. The guard at the top
        // of trySplit already rejects when the source is pressed, so the
        // source position cannot be in pressedTiles when we get here.
        advanceTurn(b, skipSpawn = true)
        recordRunIfOver()
        save()
    }

    // Merge spawn and hazard spawn are independent. Merge always
    // spawns a 2 on an empty cell (if any). Hazard spawn follows its own
    // scheduler and targets a non-empty cell.
    private fun mergeTiles(from: Pair<Int, Int>, to: Pair<Int, Int>, value: Int) {
        val merged = value * 2
        val b = board.toMutableBoard()
        b[from.first][from.second] = Tile.Empty
        b[to.first][to.second] = Tile.Normal(merged)

        actionCount += 1
        val cleared = clearHazardsFromMerge(b, merged)
        lastHazardsCleared = cleared
        lastSplit = null
        lastFocus = to

        combo += 1
        val multiplier = 1 + (combo - 1) * 0.25
        val baseGain = (merged * multiplier).toInt()
        val hazardBonus = cleared * merged / 2
        score += baseGain + hazardBonus

        // Live online PB push. GPGS keeps max-per-player so it's safe to
        // call frequently; bestThisRun guards against re-submitting the
        // same value when the score didn't actually increase past our
        // last push. Suppressed during the tutorial so scripted merges
        // don't chart.
        if (score > bestThisRun && !isTutorialActive()) {
            bestThisRun = score
            runCatching { onRunSubmitted?.invoke(score) }
        }

        selected = null
        lastTapPos = null
        if (from in pressedTiles || to in pressedTiles) {
            pressedTiles = pressedTiles - from - to
        }

        advanceTurn(b, skipSpawn = false)
        applyExpansion(merged)
        gameOver = checkGameOver(board)
        recordRunIfOver()
        save()
    }

    private fun applyExpansion(value: Int) {
        val gross = unlockCountFor(value)
        if (gross == 0) return
        // Debt accumulated from earlier splits eats the would-be unlocks
        // before any actual frame cells open.
        if (unlockDebt > 0) {
            val absorb = minOf(unlockDebt, gross)
            unlockDebt -= absorb
            val net = gross - absorb
            if (net == 0) return
            applyExpansionUnlock(net)
            return
        }
        applyExpansionUnlock(gross)
    }

    // Number of frame cells a merge resulting in `value` would normally
    // open. Returns 0 when the value isn't on the unlock ladder.
    private fun unlockCountFor(value: Int): Int {
        val base = unlockBase()
        return when (value) {
            base -> 1
            base * 2 -> 2
            base * 4 -> 3
            base * 8 -> 4
            base * 16 -> 5
            base * 32 -> 6
            else -> 0
        }
    }

    private fun applyExpansionUnlock(count: Int) {
        if (count <= 0) return
        val locked = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until size) for (c in 0 until size) {
            if (!unlocked[r][c]) locked.add(r to c)
        }
        if (locked.isEmpty()) return
        locked.shuffle()
        unlockCells(locked.take(count.coerceAtMost(locked.size)).toSet())
    }

    fun unlockBase(): Int = 64 shl (size - INITIAL_SIZE).coerceAtLeast(0)

    // Shield public API. Drag-and-drop is the only application path:
    // applyShieldOn is called with the drop target's cell coordinates.
    // Tapping the shield card opens the Earn Shield rewarded-ad flow
    // and never enters this method directly. Returns whether a shield
    // was actually consumed; if not, the caller should treat the
    // action as a no-op (no toast, no counter change).
    fun applyShieldOn(row: Int, col: Int): Boolean {
        // Tutorial gate: shields can only be applied during Step 4
        // (Shield). Earlier steps still show the player's real
        // shieldCount on the HUD, so without this gate a curious user
        // could drag onto a hazard during Step 3 and burn a real
        // shield — and the PR #22 saveShieldCount gate alone would
        // not undo that until tutorial end. Returning false silently
        // here makes the drag a no-op (the floating shield overlay
        // still snaps away because handleShieldDragEnd cleared the
        // drag pos before calling us).
        val tutorialStep = currentTutorialStep()
        if (tutorialStep != null && tutorialStep != TutorialStep.Shield) return false
        if (shieldCount <= 0) return false
        if (!isInBounds(row, col) || !unlocked[row][col]) return false
        val tile = board[row][col]
        if (!isInfected(tile)) return false
        val cured = when (tile) {
            is Tile.Fire -> Tile.Normal(tile.value).also { lastFireDeathAction = actionCount }
            is Tile.Ice -> Tile.Normal(tile.value).also { lastIceDeathAction = actionCount }
            is Tile.Poison -> Tile.Normal(tile.value).also { lastPoisonDeathAction = actionCount }
            else -> return false
        }
        val b = board.toMutableBoard()
        b[row][col] = cured
        board = b.toImmutable()
        shieldCount = (shieldCount - 1).coerceAtLeast(0)
        lastFocus = row to col
        // During the Shield tutorial step the decrement is intentionally
        // not persisted — setupForTutorial writes shieldCount = 1 in
        // memory only and restoreSavedShieldForTutorial puts the real
        // value back when the tutorial ends. Saving here would defeat
        // the override and let the tutorial consume a real shield if
        // the process is killed mid-tutorial.
        if (!isTutorialActive()) saveShieldCount()
        save()
        return true
    }

    fun grantShields(count: Int) {
        if (count <= 0) return
        shieldCount += count
        saveShieldCount()
    }

    fun tryMove(from: Pair<Int, Int>, to: Pair<Int, Int>): Boolean {
        if (gameOver) return false
        if (from == to) return false
        if (!isInBounds(from.first, from.second) || !isInBounds(to.first, to.second)) return false
        if (!unlocked[from.first][from.second] || !unlocked[to.first][to.second]) return false
        if (!isAdjacent(from, to)) return false
        if (from in pressedTiles) return false
        val source = board[from.first][from.second]
        if (source !is Tile.Normal) return false
        val target = board[to.first][to.second]
        if (isInfected(target)) return false

        val b = board.toMutableBoard()
        val newPressed = pressedTiles.toMutableSet()
        when {
            target == Tile.Empty -> {
                b[from.first][from.second] = Tile.Empty
                b[to.first][to.second] = source
                newPressed.remove(to)
                newPressed.add(to)
            }
            target is Tile.Normal && target.value != source.value -> {
                b[from.first][from.second] = target
                b[to.first][to.second] = source
                val targetWasPressed = to in newPressed
                newPressed.remove(to)
                if (targetWasPressed) newPressed.add(from)
                newPressed.add(to)
            }
            else -> return false
        }
        board = b.toImmutable()
        pressedTiles = newPressed
        actionCount += 1
        combo = 0
        selected = null
        lastTapPos = null
        lastSplit = null
        lastFocus = to
        save()
        return true
    }

    private fun unlockCells(targets: Set<Pair<Int, Int>>) {
        if (targets.isEmpty()) return
        val next = unlocked.map { it.toMutableList() }.toMutableList()
        for ((r, c) in targets) {
            if (r in 0 until size && c in 0 until size) next[r][c] = true
        }
        unlocked = next.map { it.toList() }
        unlockedTargets = targets
        unlockKey += 1

        if (isFullyUnlocked()) growBoard()
    }

    private fun isFullyUnlocked(): Boolean {
        for (r in 0 until size) for (c in 0 until size) {
            if (!unlocked[r][c]) return false
        }
        return true
    }

    private fun growBoard() {
        val offset = 1
        val newSize = size + 2
        val newBoard = MutableList(newSize) { r ->
            MutableList(newSize) { c ->
                val or = r - offset
                val oc = c - offset
                if (or in 0 until size && oc in 0 until size) board[or][oc] else Tile.Empty
            }
        }
        val newUnlocked = MutableList(newSize) { r ->
            MutableList(newSize) { c ->
                val or = r - offset
                val oc = c - offset
                if (or in 0 until size && oc in 0 until size) unlocked[or][oc] else false
            }
        }
        size = newSize
        board = newBoard.toImmutable()
        unlocked = newUnlocked.map { it.toList() }

        unlockedTargets = unlockedTargets.map { (r, c) -> (r + offset) to (c + offset) }.toSet()
        selected = selected?.let { (r, c) -> (r + offset) to (c + offset) }
        lastSplit = lastSplit?.let { (r, c) -> (r + offset) to (c + offset) }
        lastFocus = lastFocus?.let { (r, c) -> (r + offset) to (c + offset) }
        lastTapPos = lastTapPos?.let { (r, c) -> (r + offset) to (c + offset) }
        pressedTiles = pressedTiles.map { (r, c) -> (r + offset) to (c + offset) }.toSet()
    }

    private fun tierShift() = ((size - INITIAL_SIZE) / 2).coerceAtLeast(0)
    fun currentIceTier() = CLEAR_ICE_BASE shl tierShift()
    fun currentFireTier() = CLEAR_FIRE_BASE shl tierShift()
    fun currentPoisonTier() = CLEAR_POISON_BASE shl tierShift()
    fun currentAllTier() = CLEAR_ALL_BASE shl tierShift()

    // Cleanse triggered by a merge result, regardless of where the merge
    // happened on the board. Lower tiers cleanse a single oldest hazard of
    // their kind; the universal tier wipes every hazard at once.
    private fun clearHazardsFromMerge(
        b: MutableList<MutableList<Tile>>,
        mergedValue: Int
    ): Int {
        val shift = tierShift()
        val iceTier = CLEAR_ICE_BASE shl shift
        val fireTier = CLEAR_FIRE_BASE shl shift
        val poisonTier = CLEAR_POISON_BASE shl shift
        val allTier = CLEAR_ALL_BASE shl shift
        if (mergedValue >= allTier) return clearAllHazards(b)
        return when (mergedValue) {
            poisonTier -> clearOldestHazard(b, HazardKind.POISON)
            fireTier -> clearOldestHazard(b, HazardKind.FIRE)
            iceTier -> clearOldestHazard(b, HazardKind.ICE)
            else -> 0
        }
    }

    private fun clearAllHazards(b: MutableList<MutableList<Tile>>): Int {
        var cleared = 0
        for (r in 0 until size) for (c in 0 until size) {
            if (!unlocked[r][c]) continue
            val replacement = when (val tile = b[r][c]) {
                is Tile.Fire -> { lastFireDeathAction = actionCount; Tile.Normal(tile.value) }
                is Tile.Ice -> { lastIceDeathAction = actionCount; Tile.Normal(tile.value) }
                is Tile.Poison -> { lastPoisonDeathAction = actionCount; Tile.Normal(tile.value) }
                else -> null
            }
            if (replacement != null) {
                b[r][c] = replacement
                cleared++
            }
        }
        return cleared
    }

    // Picks the hazard tile of the given kind whose age is highest (closest
    // to expiring) and converts it back to a normal tile.
    private fun clearOldestHazard(
        b: MutableList<MutableList<Tile>>,
        kind: HazardKind
    ): Int {
        var bestPos: Pair<Int, Int>? = null
        var bestAge = -1
        for (r in 0 until size) for (c in 0 until size) {
            if (!unlocked[r][c]) continue
            val tile = b[r][c]
            val age = when {
                kind == HazardKind.FIRE && tile is Tile.Fire -> tile.age
                kind == HazardKind.ICE && tile is Tile.Ice -> tile.age
                kind == HazardKind.POISON && tile is Tile.Poison -> tile.age
                else -> -1
            }
            if (age > bestAge) {
                bestAge = age
                bestPos = r to c
            }
        }
        val (r, c) = bestPos ?: return 0
        when (val tile = b[r][c]) {
            is Tile.Fire -> { b[r][c] = Tile.Normal(tile.value); lastFireDeathAction = actionCount }
            is Tile.Ice -> { b[r][c] = Tile.Normal(tile.value); lastIceDeathAction = actionCount }
            is Tile.Poison -> { b[r][c] = Tile.Normal(tile.value); lastPoisonDeathAction = actionCount }
            else -> return 0
        }
        return 1
    }

    private fun advanceTurn(working: MutableList<MutableList<Tile>>, skipSpawn: Boolean) {
        turn += 1
        ageIce(working)
        ageFire(working)
        damageWithPoison(working)
        if (!skipSpawn) {
            // Order matters: hazard scheduler runs first so the merge
            // spawn sees the post-hazard board state. Hazards target
            // Normal tiles and the merge spawn targets empties, so
            // they cannot collide on the same cell — sequencing keeps
            // the contract explicit and lets the merge spawn observe
            // any cell a hazard happened to release.
            rollHazardSpawn(working)
            spawnTwoOnRandomEmptyCell(working)
        }
        board = working.toImmutable()
        gameOver = checkGameOver(board)
        // recordRunIfOver() is intentionally NOT called here. mergeTiles still
        // has applyExpansion to run after this, which can flip a transient
        // game-over back to false. Each caller records once the post-action
        // state is final.
    }

    private fun ageIce(b: MutableList<MutableList<Tile>>) {
        for (r in 0 until size) for (c in 0 until size) {
            if (!unlocked[r][c]) continue
            val t = b[r][c]
            if (t is Tile.Ice) {
                if (t.age + 1 >= ICE_LIFE) {
                    b[r][c] = Tile.Normal(t.value)
                    lastIceDeathAction = actionCount
                } else {
                    b[r][c] = Tile.Ice(t.value, t.age + 1)
                }
            }
        }
    }

    private fun ageFire(b: MutableList<MutableList<Tile>>) {
        // Snapshot fire positions before iterating so freshly jumped fires
        // are not aged twice in the same tick.
        val firePositions = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until size) for (c in 0 until size) {
            if (unlocked[r][c] && b[r][c] is Tile.Fire) firePositions.add(r to c)
        }
        for ((r, c) in firePositions) {
            val t = b[r][c] as? Tile.Fire ?: continue
            if (t.age + 1 < FIRE_LIFE) {
                b[r][c] = Tile.Fire(t.value, t.age + 1)
                continue
            }
            // Fuse expired without being cleansed: instead of dying, the fire
            // jumps to the player's most strategically painful tile and burns
            // there for another full cycle. The original spot is released as
            // a Normal tile carrying the fire's previous value.
            val target = pickFireJumpTarget(b, exclude = r to c)
            if (target == null) {
                // No Normal tile available to land on — keep burning here and
                // restart the timer so we retry once new tiles appear.
                b[r][c] = Tile.Fire(t.value, 0)
                continue
            }
            val (nr, nc) = target
            val victim = b[nr][nc] as Tile.Normal
            b[r][c] = Tile.Normal(t.value)
            b[nr][nc] = Tile.Fire(victim.value, 0)
            if ((nr to nc) in pressedTiles) pressedTiles = pressedTiles - (nr to nc)
            if (selected == (nr to nc)) selected = null
        }
    }

    // Picks the highest-value Normal tile that has at least one same-value
    // Normal neighbor (the player's most valuable pending merge). Falls back
    // to any random Normal tile if no mergeable pair exists. Returns null if
    // there are no Normal tiles on the board at all.
    private fun pickFireJumpTarget(
        b: List<List<Tile>>,
        exclude: Pair<Int, Int>
    ): Pair<Int, Int>? {
        var bestPos: Pair<Int, Int>? = null
        var bestValue = -1
        val anyNormal = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until size) for (c in 0 until size) {
            if (!unlocked[r][c]) continue
            if ((r to c) == exclude) continue
            val tile = b[r][c] as? Tile.Normal ?: continue
            anyNormal.add(r to c)
            val mergeable = neighbors(r, c, size).any { (nr, nc) ->
                if (!unlocked[nr][nc]) return@any false
                if ((nr to nc) == exclude) return@any false
                val n = b[nr][nc] as? Tile.Normal ?: return@any false
                n.value == tile.value
            }
            if (mergeable && tile.value > bestValue) {
                bestValue = tile.value
                bestPos = r to c
            }
        }
        return bestPos ?: anyNormal.randomOrNull()
    }

    private fun damageWithPoison(b: MutableList<MutableList<Tile>>) {
        val poisons = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until size) for (c in 0 until size) {
            if (unlocked[r][c] && b[r][c] is Tile.Poison) poisons.add(r to c)
        }
        for ((r, c) in poisons) {
            val p = b[r][c] as Tile.Poison
            if (p.age + 1 >= POISON_FUSE) {
                val halved = p.value / 2
                // Below value 2 the cell is just released as empty — the
                // game's smallest playable Normal value is 2, and poison
                // can now spawn fresh at value 2 (halving would drop to 1).
                b[r][c] = if (halved >= 2) Tile.Normal(halved) else Tile.Empty
                lastPoisonDeathAction = actionCount
            } else {
                b[r][c] = Tile.Poison(p.value, p.age + 1)
            }
        }
    }

    // Merge spawn: ALWAYS places one Normal(2) on a randomly chosen
    // empty cell after a merge. Independent of hazards, level, or any
    // other system. The only legitimate skip path is when there are
    // literally zero empty cells on the board (nothing to place onto).
    private fun spawnTwoOnRandomEmptyCell(b: MutableList<MutableList<Tile>>) {
        val spot = randomEmpty(b) ?: return
        b[spot.first][spot.second] = Tile.Normal(2)
    }

    // Hazard scheduler. Independent of the merge spawn — runs on its
    // own per-turn probability, and when it triggers it picks a
    // hazard kind weighted by the same per-phase mix the game has
    // always used. Hazards target a non-empty (Normal) tile, so a
    // hazard appearance never consumes an empty cell that the merge
    // spawn could have used.
    private fun rollHazardSpawn(b: MutableList<MutableList<Tile>>) {
        val phase = phaseFor(turn)
        val totalHazardRate = phase.fire + phase.ice + phase.poison
        if (totalHazardRate <= 0) return
        val roll = Random.nextInt(100)
        if (roll >= totalHazardRate) return
        when {
            roll < phase.fire -> spawnHazardOnNormal(b, HazardKind.FIRE)
            roll < phase.fire + phase.ice -> spawnHazardOnNormal(b, HazardKind.ICE)
            else -> spawnHazardOnNormal(b, HazardKind.POISON)
        }
    }

    // Hazard spawn = INFECTION. Pick the Normal tile that hurts the
    // player the most: prefer one that has a same-value Normal
    // neighbour (the player's most valuable pending merge), and among
    // those candidates prefer the highest-value tile. If no paired
    // Normal exists, fall back to the highest-value lone Normal.
    // If no Normal exists at all, the per-kind cap is reached, or the
    // post-death cooldown is still active, the call returns silently
    // — the merge spawn step that follows is the one that keeps the
    // board fed with new tiles.
    private fun spawnHazardOnNormal(b: MutableList<MutableList<Tile>>, kind: HazardKind) {
        val onCooldown = actionCount - lastDeathAction(kind) < HAZARD_RESPAWN_COOLDOWN
        if (onCooldown || countHazard(b, kind) >= hazardCapPerKind()) return

        val normals = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until size) for (c in 0 until size) {
            if (!unlocked[r][c]) continue
            if (b[r][c] is Tile.Normal) normals.add(r to c)
        }
        if (normals.isEmpty()) return

        // 1) Prefer Normals that have at least one same-value Normal
        // neighbour — these are the player's pending merges and the
        // most painful targets to lose.
        val paired = normals.filter { (r, c) ->
            val v = (b[r][c] as Tile.Normal).value
            neighbors(r, c, size).any { (nr, nc) ->
                unlocked[nr][nc] && (b[nr][nc] as? Tile.Normal)?.value == v
            }
        }
        val pool = paired.ifEmpty { normals }
        // 2) Among the chosen pool, take the highest-value tiles and
        // pick one at random.
        val maxValue = pool.maxOf { (r, c) -> (b[r][c] as Tile.Normal).value }
        val candidates = pool.filter { (r, c) -> (b[r][c] as Tile.Normal).value == maxValue }
        val (vr, vc) = candidates.random()

        val victim = b[vr][vc] as Tile.Normal
        b[vr][vc] = when (kind) {
            HazardKind.FIRE -> Tile.Fire(victim.value, 0)
            HazardKind.ICE -> Tile.Ice(victim.value, 0)
            HazardKind.POISON -> Tile.Poison(victim.value, 0)
        }
        val pos = vr to vc
        if (pos in pressedTiles) pressedTiles = pressedTiles - pos
        if (selected == pos) selected = null
    }

    private fun hazardCapPerKind(): Int = (size - INITIAL_SIZE) / 2 + 1

    private fun lastDeathAction(kind: HazardKind): Int = when (kind) {
        HazardKind.FIRE -> lastFireDeathAction
        HazardKind.ICE -> lastIceDeathAction
        HazardKind.POISON -> lastPoisonDeathAction
    }

    private fun countHazard(b: List<List<Tile>>, kind: HazardKind): Int {
        var n = 0
        for (r in 0 until size) for (c in 0 until size) {
            if (!unlocked[r][c]) continue
            val match = when (kind) {
                HazardKind.FIRE -> b[r][c] is Tile.Fire
                HazardKind.ICE -> b[r][c] is Tile.Ice
                HazardKind.POISON -> b[r][c] is Tile.Poison
            }
            if (match) n++
        }
        return n
    }

    private enum class HazardKind { FIRE, ICE, POISON }

    private data class Phase(
        val fire: Int,
        val ice: Int,
        val poison: Int
    )

    // Per-phase hazard spawn distribution. Each field is the percent
    // chance of that hazard kind landing on a merge turn; their sum
    // is the total per-turn hazard probability (Phase 1 = 9%, Phase
    // 4 = 42%). The merge spawn (Normal(2) on an empty cell) runs
    // independently of these rolls — this distribution governs only
    // the hazard scheduler.
    private fun phaseFor(t: Int): Phase = when {
        t < 8 -> Phase(fire = 3, ice = 4, poison = 2)
        t < 20 -> Phase(fire = 7, ice = 8, poison = 6)
        t < 40 -> Phase(fire = 11, ice = 12, poison = 10)
        else -> Phase(fire = 14, ice = 15, poison = 13)
    }

    private fun isInfected(t: Tile): Boolean =
        t is Tile.Fire || t is Tile.Ice || t is Tile.Poison

    private fun neighbors(r: Int, c: Int, bound: Int): List<Pair<Int, Int>> = listOf(
        r - 1 to c, r + 1 to c, r to c - 1, r to c + 1
    ).filter { (nr, nc) -> nr in 0 until bound && nc in 0 until bound }

    private fun isAdjacent(a: Pair<Int, Int>, b: Pair<Int, Int>): Boolean {
        val dr = abs(a.first - b.first)
        val dc = abs(a.second - b.second)
        return (dr == 1 && dc == 0) || (dr == 0 && dc == 1)
    }

    private fun isInBounds(r: Int, c: Int) = r in 0 until size && c in 0 until size

    private fun randomEmpty(b: List<List<Tile>>): Pair<Int, Int>? {
        val empty = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until size) for (c in 0 until size) {
            if (unlocked[r][c] && b[r][c] == Tile.Empty) empty.add(r to c)
        }
        return empty.randomOrNull()
    }

    private fun checkGameOver(b: List<List<Tile>>): Boolean {
        var emptyCount = 0
        var splittableExists = false
        var oneCount = 0
        for (r in 0 until size) for (c in 0 until size) {
            if (!unlocked[r][c]) continue
            val t = b[r][c]
            when {
                t == Tile.Empty -> emptyCount++
                t is Tile.Normal -> {
                    if (t.value >= SPLIT_MIN) splittableExists = true
                    if (t.value == 1) oneCount++
                }
                else -> { /* infected tile contributes nothing on its own */ }
            }
        }
        // The player can advance turns when a merge or a split (or a 1+1
        // future merge) is available. If none of those exist the run is
        // soft-locked and game-over fires regardless of any infected tile.
        val canAdvance = (emptyCount >= 1 && splittableExists) ||
            (emptyCount >= 1 && oneCount >= 2)
        for (r in 0 until size) for (c in 0 until size) {
            if (!unlocked[r][c]) continue
            val t = b[r][c] as? Tile.Normal ?: continue
            for ((nr, nc) in neighbors(r, c, size)) {
                if (!unlocked[nr][nc]) continue
                val n = b[nr][nc]
                if (n is Tile.Normal && n.value == t.value) return false
                // An infected neighbor that will eventually release a matching
                // value defers game over only if the player can still advance
                // turns to wait for the release. Otherwise calling it not-over
                // would leave the run in an unrecoverable soft-lock.
                if (canAdvance && futureNormalValue(n) == t.value) return false
            }
        }
        if (canAdvance) return false
        return true
    }

    private fun futureNormalValue(t: Tile): Int? = when (t) {
        is Tile.Fire -> t.value
        is Tile.Ice -> t.value
        is Tile.Poison -> if (t.value / 2 >= 1) t.value / 2 else null
        else -> null
    }

    private fun activeCells(mask: List<List<Boolean>>): List<Pair<Int, Int>> {
        val out = mutableListOf<Pair<Int, Int>>()
        for (r in mask.indices) for (c in mask[r].indices) {
            if (mask[r][c]) out.add(r to c)
        }
        return out
    }

    private fun emptyBoard(n: Int): List<List<Tile>> = List(n) { List(n) { Tile.Empty } }

    private fun mutableBoard(n: Int): MutableList<MutableList<Tile>> =
        MutableList(n) { MutableList(n) { Tile.Empty } }

    private fun initialUnlocked(n: Int): List<List<Boolean>> {
        val pad = (n - INITIAL_ACTIVE) / 2
        return List(n) { r ->
            List(n) { c ->
                r in pad until (pad + INITIAL_ACTIVE) && c in pad until (pad + INITIAL_ACTIVE)
            }
        }
    }

    private fun MutableList<MutableList<Tile>>.toImmutable(): List<List<Tile>> =
        this.map { it.toList() }

    private fun List<List<Tile>>.toMutableBoard(): MutableList<MutableList<Tile>> =
        this.map { it.toMutableList() }.toMutableList()

    private fun save() {
        val prefs = prefs ?: return
        try {
            val json = JSONObject()
            json.put("v", 1)
            json.put("size", size)
            json.put("score", score)
            json.put("turn", turn)
            json.put("combo", combo)
            json.put("gameOver", gameOver)
            json.put("ac", actionCount)
            json.put("fda", lastFireDeathAction)
            json.put("ida", lastIceDeathAction)
            json.put("pda", lastPoisonDeathAction)
            json.put("debt", unlockDebt)
            if (pressedTiles.isNotEmpty()) {
                val arr = JSONArray()
                pressedTiles.forEach { (r, c) ->
                    val p = JSONArray()
                    p.put(r); p.put(c)
                    arr.put(p)
                }
                json.put("pressed", arr)
            }

            val boardArr = JSONArray()
            for (r in 0 until size) {
                val row = JSONArray()
                for (c in 0 until size) row.put(tileToJson(board[r][c]))
                boardArr.put(row)
            }
            json.put("board", boardArr)

            val unlockedArr = JSONArray()
            for (r in 0 until size) {
                val row = JSONArray()
                for (c in 0 until size) row.put(unlocked[r][c])
                unlockedArr.put(row)
            }
            json.put("unlocked", unlockedArr)

            prefs.edit().putString("state", json.toString()).apply()
        } catch (_: Exception) {
        }
    }

    private fun tryLoad(): Boolean {
        val prefs = prefs ?: return false
        val str = prefs.getString("state", null) ?: return false
        return try {
            val json = JSONObject(str)
            val newSize = json.getInt("size")
            if (newSize < INITIAL_SIZE) return false

            val boardArr = json.getJSONArray("board")
            val unlockedArr = json.getJSONArray("unlocked")
            if (boardArr.length() != newSize || unlockedArr.length() != newSize) return false

            val newBoard = List(newSize) { r ->
                val row = boardArr.getJSONArray(r)
                if (row.length() != newSize) return false
                List(newSize) { c -> jsonToTile(row.getJSONObject(c)) }
            }
            val newUnlocked = List(newSize) { r ->
                val row = unlockedArr.getJSONArray(r)
                if (row.length() != newSize) return false
                List(newSize) { c -> row.getBoolean(c) }
            }

            size = newSize
            board = newBoard
            unlocked = newUnlocked
            score = json.getInt("score")
            turn = json.getInt("turn")
            combo = json.getInt("combo")
            gameOver = json.getBoolean("gameOver")
            actionCount = json.optInt("ac", 0)
            lastFireDeathAction = json.optInt("fda", -HAZARD_RESPAWN_COOLDOWN)
            lastIceDeathAction = json.optInt("ida", -HAZARD_RESPAWN_COOLDOWN)
            lastPoisonDeathAction = json.optInt("pda", -HAZARD_RESPAWN_COOLDOWN)
            unlockDebt = json.optInt("debt", 0).coerceAtLeast(0)
            lastFocus = null
            submittedThisRun = gameOver
            // Seed the live-PB tracker with the saved score so resumed runs
            // don't re-push values GPGS has already received in a previous
            // session. Subsequent merges only forward strictly higher scores.
            bestThisRun = score.coerceAtLeast(0)
            lastSubmittedRank = -1
            pressedTiles = if (json.has("pressed")) {
                val arr = json.getJSONArray("pressed")
                val set = mutableSetOf<Pair<Int, Int>>()
                for (i in 0 until arr.length()) {
                    val p = arr.getJSONArray(i)
                    set.add(p.getInt(0) to p.getInt(1))
                }
                set.toSet()
            } else emptySet()
            selected = null
            lastHazardsCleared = 0
            lastSplit = null
            unlockedTargets = emptySet()
            lastTapTime = 0L
            lastTapPos = null
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tileToJson(t: Tile): JSONObject {
        val o = JSONObject()
        when (t) {
            Tile.Empty -> o.put("t", "E")
            is Tile.Normal -> { o.put("t", "N"); o.put("v", t.value) }
            is Tile.Fire -> { o.put("t", "F"); o.put("v", t.value); o.put("a", t.age) }
            is Tile.Ice -> { o.put("t", "I"); o.put("v", t.value); o.put("a", t.age) }
            is Tile.Poison -> { o.put("t", "P"); o.put("v", t.value); o.put("a", t.age) }
        }
        return o
    }

    private fun jsonToTile(o: JSONObject): Tile = when (o.getString("t")) {
        "N" -> Tile.Normal(o.getInt("v"))
        "F" -> Tile.Fire(o.optInt("v", 2), o.getInt("a"))
        "I" -> Tile.Ice(o.optInt("v", 2), o.getInt("a"))
        "P" -> Tile.Poison(o.optInt("v", 2), o.getInt("a"))
        else -> Tile.Empty
    }
}

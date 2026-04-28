# Last Tile Architecture

> `AndroidBuild/` (Kotlin + Compose) is the single source of truth for the mechanics described in this document. Class names below are the canonical system boundaries; the live module groups some of them differently (e.g. `GameState` currently consolidates the grid, merge, hazard, and turn responsibilities), but the contracts are the same.

## Runtime flow
1. `GameBootstrap` loads save data and wires all runtime systems.
2. `GridManager` owns a **growing** board state (`TileData[,]`). The active region starts at 5×5 inside a 7×7 locked frame; it expands by two on every full unlock.
3. Player input routes through one of four actions:
   - tap-to-select then tap adjacent match → `MergeSystem.TryMerge`
   - drag onto adjacent match → `MergeSystem.TryMerge`
   - double-tap a value ≥ 2 tile → `MergeSystem.TrySplit` (skips spawn)
   - drag one step to an empty cell or a non-matching neighbor → `MoveSystem.TrySlideOrSwap` (cooldown on the initiating tile)
4. On successful merge:
   - `ScoreSystem` updates score with combo multiplier and best score.
   - `ComboSystem` increments combo; a slide or swap resets it.
   - `HazardSystem` applies the global cleanse: a 16-merge clears the oldest ice on the board, 32 the oldest fire, 64 the oldest poison, and 128+ wipes every hazard at once. Tiers shift up with board size like `unlockBase`.
   - `ExpansionSystem` checks for value-tier unlocks. Splits at threshold values accrue an `unlockDebt`; the next threshold-merges drain that debt before opening any frame cells. Once the active region is fully unlocked, a board growth step runs.
5. `TurnSystem.AdvanceTurn()` increments turn count and triggers:
   - `StageSystem` danger escalation (`Stable → RisingThreat → Critical → Collapse`)
   - `HazardSystem` ages every hazard and runs spawns. Buz expires (3 turns), poison halves the underlying value when its 7-turn fuse runs out (and never lands on a value-2 tile), ateş never dies — when its 5-turn fuse expires without a cleanse it jumps to the highest-value Normal tile that still has a same-value neighbor (the player's most valuable pending merge), inherits that tile's value, and burns there for another full cycle. Each hazard kind has a `(board − 5) / 2 + 1` per-board cap and a 3-turn respawn cooldown after being cleansed or aged out.
   - `GridManager` tile spawn
   - `ChallengeSystem` progress tracking
6. `SaveSystem` persists the full snapshot (board, unlocks, score, turn, combo, pressed tile, game-over flag) after every state-changing action, plus profile progression and challenge progress.
7. On game over, `LeaderboardProvider.submit(LeaderboardEntry)` records `{score, turn, maxSize, timestamp}` into the local top-10 store. The same interface is designed to be swapped for an online provider.

## Key systems
- **GridManager:** board state, growth, and spawn logic.
- **MergeSystem:** merge + split orchestration and gameplay effects.
- **MoveSystem:** one-step slide / swap with single-tile `pressedTiles` cooldown.
- **HazardSystem:** spawn / age / decay / jump for fire, ice, poison plus the global tier-based cleanse triggered by merge results (16/32/64 → oldest-of-kind, 128+ → universal wipe).
- **TurnSystem:** single-turn clock driving run pressure.
- **StageSystem:** readable in-run escalation stages.
- **ScoreSystem:** run score and persistent best score.
- **ComboSystem:** combo counter that grows on chained merges and resets on slide/swap or non-match.
- **ExpansionSystem:** value-tier unlock thresholds and board growth.
- **ProgressionSystem:** endless profile XP + level curve.
- **ChallengeSystem:** daily challenges and progress reset by UTC date.
- **LeaderboardProvider:** abstraction over local top-10 today; online provider plug-in ready.
- **AdsManager:** rewarded-ad abstraction with mock implementation; also owns the bottom banner placeholder slot used by `GameScreen`.
- **AudioManager / UIManager:** hooks for presentation layer.

## UI surfaces (current)
- Fixed-height bottom banner slot (50 dp, full-width) pinned below the game area — currently a placeholder; any `BannerAd`-style composable can replace it without touching the game loop.
- In-game **Leaderboard** button opens a dialog listing top-10 runs with rank, score, turn, max board size, and date.
- "BEST" line under the action row shows the best local score and the last run's rank when applicable.
- 7×7 viewport over a potentially larger board with **auto-follow** (camera recenters on the most recent merge / split origin / selection whenever the focus drifts off-window) and a **tappable mini-map** that previews the full board and lets the player jump the camera anywhere with one tap. There are no edge pan arrows.

## Extensibility points
- Swap `LocalLeaderboard` for a cloud-backed `LeaderboardProvider` (e.g. Play Games Services) without changing the call sites.
- Ad SDK integration plugs into `IAdsProvider`; the bottom banner composable is the only UI-layer insertion point.
- Cosmetics can be expanded by adding catalogs and a store presenter.
- Fine balancing should live in ScriptableObjects (`GameBalanceConfig`, `DailyChallengeCatalog`).

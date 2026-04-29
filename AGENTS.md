# Agent Instructions for Last Tile

This file is read by Claude Code, Codex, and other coding agents working in
this repo. Follow these rules before committing.

## Project context
Last Tile is a calm merge + survival puzzle for Android, written in Kotlin
+ Jetpack Compose under `AndroidBuild/`.

## Single source of truth
`AndroidBuild/` is the single source of truth. There is no parallel
codebase. All gameplay, persistence, ad-slot, and leaderboard work happens
inside `AndroidBuild/`.

## Online leaderboard (Google Play Games Services)
Last Tile uses Google Play Games Services for online leaderboards. When
changes affect score submission, leaderboard UI, or sign-in flow, verify
that `legal/privacy-policy.md`, `docs/privacy-policy/index.html`, and
`store-assets/play-store/data-safety.md` still match the implementation
and update them in the same commit if not.

## Score submission semantics
- LOCAL leaderboard: submit on natural game-over AND on manual Restart
  (one entry per run). Restart counts because growBoard makes natural
  game-over rare; pressing Restart is the player's deliberate "this run
  is done" signal.
- GPGS (online) leaderboard: submit on game-over, on Restart, AND on
  every new in-run personal best. GPGS keeps the max-per-player so
  duplicate / lower submissions are server-side no-ops.
- The submittedThisRun flag prevents double-submission within a single
  run regardless of how the run ends. bestThisRun is the per-run high-
  water mark for the live PB tracker.

## Spawn semantics
Every successful player action (merge, slide, swap, split) spawns
exactly one Normal value=2 tile in a random empty cell. Hazards
(Fire, Ice, Poison) spawn ADDITIONALLY with independent
probabilities — they NEVER replace the Normal 2 spawn. If the
board has insufficient empty cells, the Normal 2 takes priority.

The current per-phase hazard rates are independent percentages
(see `phaseFor` in `GameState.kt`): Phase 1 (turn < 8) is
hazard-free; Phase 2 (turn < 20) is fire 8% / ice 4% / poison 4%;
Phase 3 (turn < 40) is 12% / 8% / 8%; Phase 4 (turn ≥ 40) is
15% / 10% / 10%. Each kind also respects a per-board-size cap
and a 3-turn post-death respawn cooldown.

When changing spawn balance, update `phaseFor`,
`release-info/balancing-guide.md`, the README spawn note, and
this section together.

## Compose pointerInput correctness rule
Modifier.pointerInput must NEVER be keyed on Unit when its callbacks
capture state values that can change during the lifetime of the
Composable. Either key on the captured values directly, or wrap
callbacks in rememberUpdatedState. This applies to all gesture
detectors: detectDragGestures, detectTapGestures, etc.

The canonical bug this rule prevents: in BoardView the viewport's
(originRow, originCol) shifts when the board grows past 7×7 and the
auto-follow LaunchedEffect re-centers on the latest focus tile. Each
TileView's `pos` is a derived value (originRow + rIdx, originCol + cIdx)
captured by drag callbacks. With key=Unit, the pointerInput suspend
block freezes those callbacks at first composition; after a pan, the
slot fires the OLD callbacks against the OLD pos and a tile 1-3 cells
away from the player's finger animates instead. Always key on `pos`
(or equivalent) for any pointerInput inside an iterated grid.

## Branch policy
All AI-driven work happens on a `claude/...` or `codex/...` branch and is
merged via pull request. Never push directly to `main`/`master`.

## Store Assets Synchronization
The `store-assets/play-store/` folder contains the canonical Play Store
listing content. Whenever you make changes that affect what users see or how
the app behaves, you MUST also update the relevant store assets in the same
commit. See `store-assets/play-store/README.md` for the full sync rules.

Quick checklist before committing:
- Did I add/remove a gameplay feature? → update `full-description.txt`
- Did I add a new SDK or permission? → update `data-safety.md`
- Did I bump the version? → archive `whats-new/current.txt`
- Did I change UI in a visible way? → add screenshot TODO

## First-release rule
Until the first public Play Store release ships, treat every iteration as
the first version. Do not write backward-looking changelog language ("we
added X", "improved Y") in the public store copy or in `whats-new/current.txt`.
`whats-new/history/` stays empty until `versionName` is bumped after the
first publish. Internal commit messages and PR descriptions can describe
deltas freely.

## Privacy policy and Data Safety
`legal/privacy-policy.md` and `docs/privacy-policy/index.html` (the published
GitHub Pages copy) must agree with `store-assets/play-store/data-safety.md`.
If you change one, update the others in the same commit.

## AdMob secrets sync rule
AdMob App ID and Banner Ad Unit ID are stored only in GitHub Secrets
(`ADMOB_APP_ID`, `ADMOB_BANNER_AD_UNIT_ID`). They MUST NEVER be committed
to the repository. Local development and any build without those secrets
automatically uses Google's official test IDs via the fallback in
`AndroidBuild/app/build.gradle.kts`. When adding new ad units, add a new
secret and a new `BuildConfig` field — do not hardcode IDs.

## Screenshots
Screenshots in the Play Store listing reflect the actual app. If you change
visible UI, mark a TODO in the PR description that screenshots may need to
be re-captured. Keep master graphics under
`store-assets/play-store/graphics/`.

## History note

This repository was migrated from a previous private repository on
27 April 2026. When the repo was made public under the moonstreamtech
organization, git history was reset to a single initial commit.
Earlier development history is not preserved in this repository.

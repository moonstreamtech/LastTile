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

# Last Tile

Last Tile is a native Android game built with **Kotlin + Jetpack Compose** — a calm merge + survival puzzle for Google Play release. The entire production codebase lives under `AndroidBuild/`.

## Game Summary
- **Title:** Last Tile
- **Genre:** Hybrid-casual 2D puzzle survival
- **Core Loop:** Merge matching tiles on a **growing board** (starts at a 5×5 active region inside a 7×7 frame, expands to 7×7, 9×9, 11×11… active as you unlock tiers). Each merge advances one turn; slides/swaps are free. Survive until no adjacent pair remains.
- **Key Actions:** tap-to-select → tap match, drag-to-merge, double-tap to split, drag one step to slide/swap (single-tile cooldown)
- **Progression:** value-tier unlocks — reaching `base`, `base×2`, … `base×32` opens 1–6 random frame cells; filling the active ring grows the whole board by two cells. `base = 64 << (size − 7)`. Splitting a tile that sits on an unlock threshold accrues an "unlock debt" that the next threshold-merges must pay back before any new cells open, so split → re-merge loops can't farm extra unlocks.
- **Hazards:** Fire / Ice / Poison tile types are live. All three freeze the tile they sit on. Ice expires after 3 turns; poison after 7 turns and halves the underlying value (poison spawned at value 2 simply releases an empty cell on expiry); fire never dies on its own — if the player can't trigger its cleanse it jumps to the most valuable pending merge every 5 turns. Cleanse is global (board-wide): a 16/32/64 merge clears the oldest ice/fire/poison; a 128 merge wipes every hazard at once.
- **Spawn semantics:** every successful action (merge, slide, swap, split) spawns exactly one Normal value-2 tile in a random empty cell. Hazard spawns are **additive** — fire/ice/poison are rolled independently against per-phase probabilities and, on success, drop a fresh value-2 hazard into a separate empty cell. Hazards never replace the guaranteed Normal(2); if the board has no room for an additional hazard the hazard skips silently. The Normal(2) takes priority for the last empty cell.
- **Persistence:** board, score, turn, combo, unlocks, and pressed-tile state are snapshotted to `SharedPreferences` after every action; cold restart resumes the exact same run.
- **Session Target:** Fast runs (early 1–3 minutes) with strong replayability. Connectivity not required.
- **Language:** English (game content and publishing content)

## Repository Structure

```text
/AndroidBuild       # Native Kotlin + Jetpack Compose game (single production target)
/docs
/docs/privacy-policy
/docs/terms
/docs/support
/store-assets
/store-assets/play-store
/store-assets/branding
/.github/workflows
/legal
/release-info
README.md
```

## Production target

`AndroidBuild/` is the single source of truth. There is no parallel codebase. Every gameplay, persistence, ad-slot and leaderboard change happens here.

Highlights:
- Active region starts at 5×5 inside a 7×7 locked frame; grows +2 per side as unlocks complete (7×7, 9×9, …).
- Visible board is capped at a 7×7 viewport. Past 7×7 the viewport auto-follows the most recent action (last merge / split origin / current selection) so the player rarely needs to pan, and a tappable mini-map below the board gives a full-board overview and lets the player jump the viewport anywhere with one tap.
- Actions: tap-tap merge, drag-to-merge, double-tap to split (no new spawn that turn), slide one step into an empty cell or swap with a non-matching neighbor (single-tile "pressed" cooldown until its value changes).
- Value-tier unlocks open 1–6 random frame cells; filling the whole active area triggers a board-size growth. Splits at threshold values queue up an "unlock debt" the next threshold-merges have to clear before new cells open.
- Run state (board, unlocks, score, turn, combo, pressed tile) persists in `SharedPreferences`, so cold restarts resume the exact run.
- Local leaderboard: top-10 finished or restarted runs (score / turn / max board size / timestamp) are stored on-device and surfaced from the in-game **Leaderboard** button. `LeaderboardProvider` is interface-driven so an online provider can be dropped in later.
- Online leaderboard via Google Play Games Services: scores are submitted to the global GPGS leaderboard on every new in-run personal best (live update), on natural game-over, and when the player taps **Restart** (so endless runs always count). The local on-device leaderboard records the run on game-over **and** on Restart, since `growBoard` makes natural game-over rare. The in-game Leaderboard dialog has a **Global** tab that opens the GPGS leaderboard UI. Sign-out / no-internet keeps the Local tab fully functional.
- Bottom banner ad (320×50, fixed 50 dp full-width) served by Google AdMob via the Google Mobile Ads SDK. The ad slot fails gracefully on devices without Google Play Services and during dev/CI builds (which use Google's official test ad IDs).
- Hazards (Fire / Ice / Poison) are active, persisted, and capped per board size. The Normal(2) spawn is unconditional after each action; hazard rolls are **independent** add-ons (no hazard ever replaces the guaranteed Normal(2)). Phase 1 is hazard-free; rates ramp to fire 15% / ice 10% / poison 10% in the collapse stage. See `release-info/balancing-guide.md` for the full table.

See architecture details in `release-info/architecture.md`.

## Build the APK

### Requirements
- JDK 17
- Android SDK with platform 34 + build-tools 34.0.0 (`ANDROID_HOME` set)
- Gradle 8.4 (or use the wrapper if added later)

### Local debug build

```bash
cd AndroidBuild
gradle :app:assembleDebug
# APK at: AndroidBuild/app/build/outputs/apk/debug/app-debug.apk
```

### Local release build (when keystore is set up)

```bash
cd AndroidBuild
gradle :app:assembleRelease    # signed APK
gradle :app:bundleRelease      # AAB for Play Console
```

Release signing needs your own keystore credentials wired into `app/build.gradle.kts` (`signingConfigs { release { ... } }`). Until that is configured, only the debug variant is buildable.

## GitHub Actions CI

Single workflow: `.github/workflows/android-native.yml`.

### Triggers
- `push` to `main`, `master`, `claude/**`, `codex/**`
- `pull_request` to `main` or `master`
- manual `workflow_dispatch`

### Behavior
- Sets up JDK 17, the Android SDK, and Gradle 8.4 on a public GitHub-hosted runner.
- Runs `gradle :app:assembleDebug` against `AndroidBuild/`.
- Renames the APK to `lasttile-debug.apk` and uploads it as the `lasttile-native-apk` artifact.
- No secrets required.

> CI builds both the debug APK and an **unsigned** release AAB (`bundleRelease`) so release-only issues (R8/proguard, manifest stripping, resource shrinking) are caught on every push. The AAB is not yet uploadable to Play directly — wire up a keystore + the corresponding `*_BASE64`, `*_PASSWORD`, `*_ALIAS` secrets to produce a signed AAB, or rely on Play App Signing on upload.

### AdMob credentials
AdMob credentials are injected at build time via three GitHub Secrets:
`ADMOB_APP_ID`, `ADMOB_BANNER_UNIT_ID`, `ADMOB_REWARDED_UNIT_ID`. DEBUG
builds always use Google's public test ids regardless of secret
presence (so local dev and CI debug builds never serve real inventory);
RELEASE builds require all three secrets and are protected by four
layers of guards:

1. **Build-time** — gradle `taskGraph` aborts release builds when any
   real id is empty or contains the test publisher prefix.
2. **Runtime** — `AdConfig.verifyReleaseIntegrity()` runs in
   `LastTileApplication.onCreate` before `MobileAds.initialize` and
   crashes the process if any release-built BuildConfig id is blank.
   The test-prefix check itself is enforced at build time so the test
   publisher string is never embedded in a release-variant binary.
3. **CI pre-build** — workflow step validates each secret before
   gradle starts, never echoing values.
4. **CI post-build** — final step decompresses the AAB and rejects the
   bundle if any byte references the test publisher prefix.

The configuration step prints `[Last Tile] AdMob mode: TEST` or
`... PRODUCTION` so the active mode is auditable from CI logs without
leaking the real ids.

### Download the APK from GitHub Actions
1. Push to a tracked branch (or run **Actions → Android Native APK → Run workflow** manually).
2. Open the completed workflow run.
3. Scroll to the **Artifacts** section at the bottom.
4. Download `lasttile-native-apk` (contains `lasttile-debug.apk`).
5. Transfer to your Android phone, allow install from unknown sources, and open the APK.

## Static legal/marketing pages
A static site lives under `/docs` and includes:
- Home
- Privacy Policy
- Terms of Use
- Support

These pages are kept in sync with `legal/` markdown sources in this dev repo, but are **not deployed from here**. GitHub Pages will be enabled on the production repo once this code is moved over; until then `/docs` is just source-controlled HTML.

## Play Store Assets
All base listing copy and checklists are in:
- `store-assets/play-store/`

Includes:
- App title, short/full description
- Release notes
- Taglines and screenshot captions
- ASO notes
- Category/content guidance
- Monetization notes
- Store listing + publish checklists

## Legal Source Files
Editable markdown versions:
- `legal/privacy-policy.md`
- `legal/terms-of-use.md`
- `legal/support.md`

Published web versions:
- `docs/privacy-policy/index.html`
- `docs/terms/index.html`
- `docs/support/index.html`

## What Is Ready vs. What Needs Your Credentials

### Ready now
- Native Kotlin + Compose game module under `AndroidBuild/`
- Debug APK CI workflow
- Static legal / marketing HTML under `/docs` (deployed from the future production repo)
- Play Store publishing text pack
- Legal page drafts

### Requires your accounts/secrets
- `applicationId` is `com.moonstreamtech.lasttile` (canonical Play Store identity — never change after first publish)
- Production keystore/signing credentials and a signed-release CI job
- Google Play Console publisher account
- Google Play Games Services entry: both `game_services_app_id` (in
  `app/src/main/res/values/strings.xml`) and `LEADERBOARD_ID` (in
  `app/src/main/java/com/moonstreamtech/lasttile/GpgsLeaderboard.kt`)
  are wired to the Last Tile Play Games Services project. Sign-in works
  for testers added in Play Console.
- Final support email/company identity replacements
- Final branding assets (icon, screenshots, feature graphic)

## Release Checklist (Practical)
1. Confirm company details (support email, legal contact) match `com.moonstreamtech.lasttile`.
2. Replace placeholder contact email in legal/docs pages.
3. Finalize assets (icon, screenshots, feature graphic).
4. Wire up keystore + add a signed-release CI job.
5. Run CI and confirm signed APK + AAB artifacts.
6. Upload AAB to internal testing in Play Console.
7. Complete Data Safety and content rating forms.
8. Roll out staged production release.

---

Do not treat this repository as a throwaway prototype; it is structured for iterative production hardening and publishing.

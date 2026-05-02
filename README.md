# Last Tile

> A calm merge + survival puzzle for Android — merge tiles, fight hazards with shields, and grow the board until no match remains.

![Platform: Android](https://img.shields.io/badge/platform-Android-3DDC84.svg)
![Min SDK 24](https://img.shields.io/badge/min%20SDK-24-blue.svg)
![Target SDK 35](https://img.shields.io/badge/target%20SDK-35-blue.svg)
![License: All rights reserved](https://img.shields.io/badge/license-All%20rights%20reserved-lightgrey.svg)

**Quick links:**
[Website](https://moonstreamtech.com/LastTile/) ·
[Privacy Policy](https://moonstreamtech.com/LastTile/privacy-policy/) ·
[Support / FAQ](https://moonstreamtech.com/LastTile/support/) ·
Google Play (coming soon)

---

## About

Last Tile is a single-player puzzle game where every merge is a turn.
The board starts as a small active region inside a locked frame and
keeps growing as you climb tiers. Slides and swaps are free, splits get
you out of lockouts, and the run ends only when no adjacent matching
pair remains anywhere on the board.

Then the hazards arrive. Fire, Ice and Poison tiles freeze the cells
they sit on. Burn them off with high-tier merges, wait some of them out,
or spend a Shield to clean them and keep the underlying value. The
Shield system gives the game a calm, decision-driven rhythm with no
timers and no forced sessions.

Last Tile is built by [Moonstream Tech](https://moonstreamtech.com), a
small independent studio. Development happens in the open on GitHub.

## Features

- Merge-survival puzzle gameplay with real strategic depth
- Growing board: 5×5 → 7×7 → 9×9 → 11×11 and beyond
- Hazard system (Fire, Ice, Poison) with global cleanse mechanics
- Shield (Koruma) mechanic with an optional rewarded-video earn loop
- Local on-device top-ten leaderboard
- Optional Google Play Games global leaderboard (sign-in is optional)
- Adaptive Google AdMob banner ad (non-intrusive, anchored to the bottom)
- Offline-friendly: gameplay works without internet; ads and global
  leaderboard need a connection
- Auto-save: cold restart resumes the exact same board, score and combo
- Multi-language: English and Turkish
- Targets Android 7.0+ (`minSdk` 24, `targetSdk` 35)

## Tech stack

- **Language:** Kotlin
- **UI:** Jetpack Compose
- **Build:** Gradle (JDK 17)
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 35
- **Third-party SDKs:** Google Mobile Ads (AdMob), Google Play Services Games v2

The production codebase lives under [`AndroidBuild/`](AndroidBuild/).

## Building from source

Clone the repository and open `AndroidBuild/` in Android Studio (Iguana
or newer). To build a debug APK from the command line:

```bash
git clone https://github.com/moonstreamtech/LastTile.git
cd LastTile/AndroidBuild
./gradlew :app:assembleDebug
```

Debug builds use Google’s official AdMob test ad IDs automatically — no
secrets are required to build a debug APK or run the game on an
emulator. The CI workflow on GitHub Actions builds the same debug APK on
every push.

Release builds (signed APK or AAB for the Play Store) require a project
keystore and AdMob credentials configured as GitHub Actions secrets.
Those are documented in the internal `release-info/` directory and are
not buildable from a fresh clone.

## Contributing

Bug reports and feature suggestions are welcome via
[GitHub issues](https://github.com/moonstreamtech/LastTile/issues), or by
email to <moonstreamtech@gmail.com>. Pull requests are reviewed on a
best-effort basis.

When filing an issue, please include:

- Device model and Android version
- App version (Settings → About in the running build)
- A short description of what happened and what you expected
- Screenshots or a screen recording, if possible

## Privacy

Last Tile discloses every third-party service it uses. The full privacy
policy is published at
<https://moonstreamtech.com/LastTile/privacy-policy/>.

In short: no required account, no analytics SDKs, no third-party
trackers beyond **Google AdMob** (banner + optional rewarded videos) and
**Google Play Games Services** (optional global leaderboard sign-in).
Game progress is stored locally on the device using Android
`SharedPreferences` and is removed when the app is uninstalled or its
data is cleared.

## Credits

Built by [Moonstream Tech](https://moonstreamtech.com) — quiet software,
built with care. See <https://moonstreamtech.com> for the studio’s other
projects.

## License

© 2026 Moonstream Tech. All rights reserved. The Last Tile name, art,
gameplay design, source code and visual assets are the intellectual
property of Moonstream Tech.

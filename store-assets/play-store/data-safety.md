# Data Safety Form Answers

## CURRENT VERSION (Google Play Games Services online leaderboard added)

### Data collected
- **App activity → Game scores** — submitted to Google Play Games Services
  when the player finishes a run, so the score can appear on the global
  leaderboard.
- **User IDs → Google Play Games player ID** — handled by Google. The app
  itself never reads or stores it.

No data is collected on Last Tile's own servers. All other game state
(board, run history, on-device top-10 leaderboard) is stored only on the
player's device via Android SharedPreferences.

### Data shared with third parties
- **Google Play Games Services** — receives the numeric score and the
  player's Google Play Games identity for leaderboard functionality only.
  Subject to Google's Play Games Services Privacy Policy.

No other third parties receive data.

### Security practices
- Score submissions are sent over HTTPS by the Play Games Services SDK.
- No account or login required to play; sign-in is only requested when the
  player opens the global leaderboard or when their score qualifies.
- Local game state and on-device leaderboard are stored via Android
  SharedPreferences and never leave the device.
- Data can be deleted by signing out of Google Play Games (removes online
  scores from the player's identity) and uninstalling the app (removes
  local data).

### Form answers (Play Console)
- "Does your app collect or share any of the required user data types?"
  → YES (App activity > Game scores; User IDs > Google Play Games player ID)
- "Is all of the user data collected by your app encrypted in transit?"
  → YES (Play Games Services uses HTTPS)
- "Do you provide a way for users to request that their data is deleted?"
  → YES (sign out of Google Play Games + uninstall)

## PLANNED FOR FUTURE VERSIONS

### When AdMob is added:
- Will collect: Device or other IDs (advertising ID),
  App activity (interactions for ad serving)
- Will share: With Google AdMob (advertising partner)
- Update Play Store form before publishing the version with ads.
- Update privacy policy and add a "Contains ads" declaration.

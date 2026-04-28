# Data Safety Form Answers

## CURRENT VERSION (GPGS leaderboard + AdMob banner)

### Data collected
- **App activity → Game scores** — submitted to Google Play Games Services
  on every new in-run personal best, when the player finishes a run, and
  when the player taps Restart, so the score can appear on the global
  leaderboard.
- **User IDs → Google Play Games player ID** — handled by Google. The app
  itself never reads or stores it.
- **Device or other IDs → Advertising ID** — read by Google AdMob to serve
  banner ads.
- **App activity → App interactions** — interactions with the bottom banner
  ad (impressions, clicks) are collected by Google AdMob to serve and
  measure ads.

No data is collected on Last Tile's own servers. All other game state
(board, run history, on-device top-10 leaderboard) is stored only on the
player's device via Android SharedPreferences.

### Data shared with third parties
- **Google Play Games Services** — receives the numeric score and the
  player's Google Play Games identity for leaderboard functionality only.
  Subject to Google's Play Games Services Privacy Policy.
- **Google AdMob** — receives the device's advertising ID, IP address, and
  ad interaction events to serve and measure the bottom banner ad.
  Subject to Google's Privacy Policy: https://policies.google.com/privacy.

No other third parties receive data.

### Ads section (Play Console "Ads" declaration)
- **Contains ads:** YES (a single 320x50 banner at the bottom of the game
  screen, served by Google AdMob).
- Data collected for ads: Advertising ID; ad interactions.
- Shared with: Google AdMob.
- Purpose: Advertising (serve and measure banner ads).
- User control: Users can reset the advertising ID or opt out of
  personalized ads from the Android system settings:
  Settings → Privacy → Ads → Reset advertising ID / Opt out of ad
  personalization.

### Security practices
- Score submissions are sent over HTTPS by the Play Games Services SDK.
- AdMob requests are sent over HTTPS by the Google Mobile Ads SDK.
- No account or login required to play; sign-in is only requested when the
  player opens the global leaderboard or when their score qualifies.
- Local game state and on-device leaderboard are stored via Android
  SharedPreferences and never leave the device.
- Data can be deleted by signing out of Google Play Games (removes online
  scores from the player's identity), resetting the advertising ID from
  Android settings (severs the link to ad history), and uninstalling the
  app (removes local data).

### Form answers (Play Console)
- "Does your app collect or share any of the required user data types?"
  → YES (App activity > Game scores and ad interactions; User IDs >
  Google Play Games player ID; Device or other IDs > Advertising ID)
- "Is all of the user data collected by your app encrypted in transit?"
  → YES (Play Games Services and Google Mobile Ads SDK both use HTTPS)
- "Do you provide a way for users to request that their data is deleted?"
  → YES (sign out of Google Play Games + reset advertising ID + uninstall)
- "Contains ads?" → YES

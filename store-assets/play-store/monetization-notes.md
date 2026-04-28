# Play Store Monetization Notes (internal)

## Shipped today
- **One AdMob banner ad** (320x50, fixed at the bottom of the game screen)
  served by Google Mobile Ads SDK. No interstitials, no rewarded video, no
  in-app purchases, no other third-party SDKs.
- AdMob credentials (App ID and Banner Ad Unit ID) are injected at build
  time via GitHub Secrets (`ADMOB_APP_ID`, `ADMOB_BANNER_AD_UNIT_ID`).
  Builds without those secrets fall back to Google's official test IDs so
  developers and CI runs never serve real inventory.

## Planned (do not declare in store copy until shipped)
- Optional rewarded ads (revive, post-run bonus, daily challenge reroll).
- Optional cosmetics.

## Store disclosure rules
- **Contains ads:** ON. The Play Console store listing must have the
  "Contains ads" toggle switched ON before publishing the first ad-enabled
  version. Confirm `data-safety.md` and the privacy policy declare AdMob.
- **In-app purchases:** must stay OFF until cosmetics or rewarded items
  ship.
- When either changes, update `data-safety.md`, the privacy policy, and
  `full-description.txt` in the same commit.

## What to avoid (long-term policy)
- Interstitials between runs.
- Forced video ads.
- Pay-to-win boosts.
- Monetization that blocks core progression or offline play.

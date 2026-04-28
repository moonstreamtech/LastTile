# Play Store Monetization Notes (internal)

## Shipped today
- **No ads**, no in-app purchases, no third-party SDKs.
- A 50 dp full-width strip is reserved at the bottom of the game screen as
  layout space for a future banner. It is intentionally empty (no "AD"
  label, no SDK). Until a real ad SDK lands, the app must continue to be
  declared as ad-free in the Play Console listing.

## Planned (do not declare in store copy until shipped)
- Single fixed-height bottom banner ad (50 dp), full-width — AdMob drop-in.
- Optional rewarded ads (revive, post-run bonus, daily challenge reroll).
- Optional cosmetics.

## Store disclosure rules
- **Contains ads:** must stay OFF until a real ad SDK is integrated and the
  empty placeholder strip is replaced with a live banner view.
- **In-app purchases:** must stay OFF until cosmetics or rewarded items
  ship.
- When either ships, update `data-safety.md`, the privacy policy, and
  `full-description.txt` in the same commit.

## What to avoid (long-term policy)
- Interstitials between runs.
- Forced video ads.
- Pay-to-win boosts.
- Monetization that blocks core progression or offline play.

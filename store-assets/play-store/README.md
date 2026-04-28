# Play Store Assets

This folder contains all Play Store listing assets — text content, metadata
forms, and policy answers. Treat these files as the SOURCE OF TRUTH for what
appears on the Last Tile Play Store page.

## File map
- `app-title.txt` — App title shown on the store
- `short-description.txt` — 80-char preview shown in search
- `full-description.txt` — Full app page description
- `whats-new/current.txt` — Release notes for the next version
- `whats-new/history/v*.txt` — Archived release notes by version
- `category-content-audience.md` — Category, age, content rating answers
- `data-safety.md` — Data Safety form answers
- `tags-keywords.md` — Play Store tags and search keywords
- `graphics/` — Master graphics (icon, feature graphic, screenshots)
- `localized/` — Translated copies (one folder per locale)

## How to use
When publishing a new release on Play Console, copy the contents of these
files into the corresponding fields. The text in this folder is always the
canonical version.

## Update rules — READ THIS BEFORE EVERY COMMIT
This folder MUST stay in sync with the actual app behavior. When you change
the app, update these files in the same commit.

### Triggers and required updates

- **New gameplay mechanic added or removed**
  → Update `full-description.txt` features list
  → Update `whats-new/current.txt`
  → Update repo's main `README.md` if it describes mechanics

- **UI change visible to user**
  → Add a TODO note that screenshots may need to be re-captured

- **Version bump (versionName change in build.gradle)**
  → Move `whats-new/current.txt` content to
    `whats-new/history/v{X.Y.Z}.txt`
  → Create new empty `whats-new/current.txt` for next iteration

- **New SDK added (AdMob, Firebase, Crashlytics, etc.)**
  → Update `data-safety.md` to declare data collection/sharing
  → Update privacy policy on the marketing site
  → Update `full-description.txt` (add "Contains ads" if applicable)

- **App now requires internet (e.g. leaderboard added)**
  → Remove "fully offline" from descriptions
  → Update `data-safety.md` to declare data transmission
  → Update privacy policy

- **Permissions changed in AndroidManifest.xml**
  → Update `data-safety.md` if data collection changes
  → Verify privacy policy still accurate

## First-release rule
Until the first public Play Store release ships, every iteration is treated
as the first version: no backward-looking changelog, no version-arrow
language, no "we updated X" copy. `whats-new/history/` stays empty until
versionName is bumped after the first publish.

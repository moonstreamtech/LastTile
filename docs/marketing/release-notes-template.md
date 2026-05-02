# Release Notes Template

Use this template each time a new version of Last Tile ships. Paste each
language block directly into the corresponding “What’s new” field in
Google Play Console.

## Limits

- **500 characters per language** (hard Play Console limit).
- Plain text only — no Markdown, no HTML.
- Emoji are allowed but optional. Keep them subtle.

## Template

```
<en-US>
[One short, friendly sentence describing the headline of this release.]

- Headline feature or improvement
- Notable fix or polish detail
- (optional) Smaller polish item
</en-US>

<tr-TR>
[Optional. Mirror the EN block in Turkish when a translation is ready.
If no translated copy is available for a given release, the EN block
can be reused — Play Console accepts identical text in both locales.]

- Headline feature or improvement
- Notable fix or polish detail
- (optional) Smaller polish item
</tr-TR>
```

## Tone

- Friendly, plain language. Avoid jargon and internal terminology.
- Lead with the **player benefit**, not the implementation detail.
- 2–4 bullets. Single-line bullets are easier to scan on a phone.
- Mention bug fixes only when they were visible to players.
- No internal version numbers, commit hashes or build IDs.
- Do not mention specific AdMob, Play Games or other internal IDs.

## Examples

### Initial release (1.0)

```
<en-US>
Welcome to Last Tile. Merge tiles, fight hazards with shields, and chase the global leaderboard.

- Calm merge + survival puzzle
- Fire, Ice and Poison hazards
- Optional rewarded videos for extra shields
- Local + global leaderboards
</en-US>
```

### Polish patch

```
<en-US>
A polish pass for the shield system and a few rough edges.

- Shield drag is more forgiving on smaller phones
- Fixed a rare crash when restarting mid-cleanse
- Small balance tweak to early-game spawn rates
</en-US>
```

### Feature update

```
<en-US>
New growing-board tier and a redesigned leaderboard screen.

- New 11×11 tier with rebalanced unlocks
- Cleaner local + global leaderboard layout
- Faster app cold start
- Various small fixes
</en-US>
```

## Where to keep historical notes

The canonical history of release notes lives under
`store-assets/play-store/whats-new/` in this repository. Each release
gets its own dated file there; this template document is only the
format reference.

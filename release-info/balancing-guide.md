# Balancing Guide

## Config sources
- `GameBalanceConfig` ScriptableObject:
  - Tile tier score/effect mapping
  - Stage thresholds
  - Hazard spawn rates
  - Combo bonus scaling
  - XP curve
- `DailyChallengeCatalog` ScriptableObject:
  - Daily challenge definitions and XP rewards

## Recommended first-pass values
- Spawn chance: Tier 1 = 85%, Tier 2 = 15%
- Stage turns: 12 / 30 / 55 (Stable → RisingThreat → Critical → Collapse)
- Combo: start bonus at 2-chain
- XP gain: `max(5, score / 20)` and level curve `100 * level^1.2`

## Hazards

All three hazards freeze the tile they sit on (no movement or merge while
infected). Each kind is capped at `(board − 5) / 2 + 1` simultaneous
instances on the board (1 on the starting 7×7, 2 on 9×9, 3 on 11×11, …)
and is locked out for 3 turns after the last instance of that kind is
removed (whether by cleanse or natural expiry).

### Per-turn spawn rates

Every successful action produces **exactly one** spawn outcome —
either a Normal(2) in a random empty cell, or a hazard infection of
an existing Normal tile. The two outcomes are **mutually exclusive**:
a single `Random.nextInt(100)` roll selects exactly one bucket and
the percentages in each row sum to 100.

| Stage         | Normal | Ateş (Fire) | Buz (Ice) | Poison |
| ------------- | :----: | :---------: | :-------: | :----: |
| Stable        |  91%   |     3%      |    4%     |   2%   |
| RisingThreat  |  79%   |     7%      |    8%     |   6%   |
| Critical      |  67%   |    11%      |   12%     |  10%   |
| Collapse      |  58%   |    14%      |   15%     |  13%   |

Stages map to turn counts: Stable `turn < 8`, RisingThreat `turn < 20`,
Critical `turn < 40`, Collapse otherwise.

### Hazard target selection

When a hazard outcome wins the roll, `spawnHazardOnNormal` picks the
victim Normal tile so the spawn lands on the player's most painful
loss:

1. Filter to Normals that have at least one same-value Normal
   neighbour (the player's pending merges).
2. Among that pool — or, if empty, all Normals — pick one of the
   highest-value tiles uniformly at random.

The infected tile keeps its value: Fire/Ice/Poison inherit
`victim.value`. Per-kind board-size cap and the 3-turn post-death
respawn cooldown still apply; when either blocks the spawn, or no
Normal exists at all, the hazard branch falls back to a fresh
Normal(2) in a random empty cell so the turn isn't a no-op.

### Lifetimes and on-expiry behavior

| Hazard | Fuse     | What happens when the fuse runs out                                                                                           |
| ------ | -------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Buz    | 3 turns  | Tile returns to Normal with its original value.                                                                               |
| Poison | 7 turns  | Tile returns to Normal with its value **halved**. A value-2 poison releases the cell as empty (halving would drop it to 1).   |
| Ateş   | 5 turns  | **Never dies naturally.** Jumps to the highest-value Normal tile that has a same-value Normal neighbor (the player's most valuable pending merge), inherits that tile's value, and burns there for another full cycle. Falls back to a random Normal tile if no mergeable pair exists. The original spot is released as Normal carrying the fire's previous value. |

Hazards spawn by infecting an existing Normal tile and inherit its
value. Fire's jump mechanic also inherits the target's value when it
relocates. If fire can't find any Normal tile on a jump it stays in
place and restarts its 5-turn timer.

### Shield (Kalkan) economy

Players start with `1` shield (`SHIELD_INITIAL` in `GameState`).
Tapping the SHIELD HUD card arms it; the next tap on a hazard tile
consumes one shield and converts the hazard back to a Normal carrying
the hazard's current value. Long-pressing the card starts a drag-and-
drop interaction with the same one-shield-cures-one-hazard cost.

When `shieldCount == 0`, tapping the card opens a rewarded-video
dialog. A successful watch grants `SHIELD_REWARD_GRANT = 3` shields.
The shield count is persisted under SharedPreferences key
`shield_count` and survives Restart — only `Settings → Apps → Clear
data` resets it.

### Cleanse — global, board-wide

Triggered by the **value of a merge result**, regardless of where the
merge happens on the board:

| Merge result | Effect                                  |
| -----------: | --------------------------------------- |
|         16   | Clear the **oldest** ice                |
|         32   | Clear the **oldest** fire               |
|         64   | Clear the **oldest** poison             |
|    ≥ 128     | Wipe **every** hazard at once           |

"Oldest" = the hazard with the highest age (least time left). Cleanse
counts as a death — the 3-turn respawn cooldown for that kind starts
fresh. Successful cleanses also award a score bonus of
`hazard_count * merged_value / 2`.

Tiers shift up with the board size the same way `unlockBase` does
(`shl tierShift()`), so on a 9×9 board the ice cleanse triggers at
value 32, fire at 64, poison at 128, and the universal wipe at 256.

## Unlock economy

Reaching a value on the unlock ladder (`base`, `base×2`, … `base×32`,
where `base = 64 << (size − 7)`) opens 1–6 frame cells:

| Merged value | Cells unlocked |
| -----------: | :------------: |
|       base   |       1        |
|     base×2   |       2        |
|     base×4   |       3        |
|     base×8   |       4        |
|    base×16   |       5        |
|    base×32   |       6        |

**Anti-farm rule (split debt).** Splitting a tile whose value sits on
the unlock ladder charges an `unlockDebt` equal to the cells that value
would normally open. The next threshold-merges drain that debt before
any cells actually open, so the classic split → re-merge loop can no
longer be used to farm extra frame cells. The debt carries across
board growth (the underlying `base` shifts but the debt stays in
"cells owed", so the rule survives expansions). Splits below the
unlock ladder don't charge any debt.

Worked example with `base = 32` (illustrative — at `base = 64` shift
every threshold up one): the player double-taps a 64 tile (2 cells of
unlock potential) → `unlockDebt = +2`. They re-merge 32+32 = 64 → that
merge would normally open 1 cell, debt absorbs 1, no cells open,
`unlockDebt = 1`. They then merge 64+64 = 128 → that would normally
open 2 cells, debt absorbs 1 → 1 cell opens, `unlockDebt = 0`. Net
gain across the whole sequence: zero extra cells.

## Tuning process
1. Record run length percentile (P25, P50, P90).
2. Keep first-session median run around 1–3 minutes.
3. Ensure advanced players can extend runs through tactical merges.
4. Avoid runaway score inflation by capping combo multipliers.

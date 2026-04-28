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

Rates are evaluated per spawn roll, by stage. Curves are linear-ish
between the start and end values:

| Stage         | Buz (Ice) | Ateş (Fire) | Poison |
| ------------- | :-------: | :---------: | :----: |
| Stable        |    4%     |     3%      |   2%   |
| RisingThreat  |    8%     |     7%      |   6%   |
| Critical      |   12%     |    11%      |  10%   |
| Collapse      |   15%     |    14%      |  13%   |

### Lifetimes and on-expiry behavior

| Hazard | Fuse     | What happens when the fuse runs out                                                                                           |
| ------ | -------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Buz    | 3 turns  | Tile returns to Normal with its original value.                                                                               |
| Poison | 7 turns  | Tile returns to Normal with its value **halved**. Poison never spawns on a value-2 tile (would drop it to 1).                 |
| Ateş   | 5 turns  | **Never dies naturally.** Jumps to the highest-value Normal tile that has a same-value Normal neighbor (the player's most valuable pending merge), inherits that tile's value, and burns there for another full cycle. Falls back to a random Normal tile if no mergeable pair exists. The original spot is released as Normal carrying the fire's previous value. |

If poison can't find an eligible target on a roll (e.g. every Normal
tile is value 2 or already infected) it falls back once to a random
fire/ice spawn. If fire can't find any Normal tile at all on a jump it
stays in place and restarts its 5-turn timer.

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

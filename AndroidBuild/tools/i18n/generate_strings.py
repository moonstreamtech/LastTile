#!/usr/bin/env python3
# Generates AndroidBuild/app/src/main/res/values-*/strings.xml from a single
# source-of-truth translation table. The default (English) values/strings.xml
# stays untouched and is the canonical master; this generator only writes the
# localised siblings so they always stay key-aligned with master.

import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from data import T

REPO_ROOT = Path(__file__).resolve().parents[3]
RES_DIR = REPO_ROOT / "AndroidBuild" / "app" / "src" / "main" / "res"
MASTER_PATH = RES_DIR / "values" / "strings.xml"

# ---------------------------------------------------------------------------
# Master key list — order matches values/strings.xml. Tuple form:
#   (key, kind)  where kind is "string" or "plurals"
# Strings with translatable="false" in master are NOT regenerated for locales
# (Android falls back to default), so they are excluded from this list.
# ---------------------------------------------------------------------------
MASTER_KEYS = [
    ("tagline", "string"),
    ("stat_score", "string"),
    ("stat_turn", "string"),
    ("stat_combo", "string"),
    ("stat_shield", "string"),
    ("shield_dialog_title", "string"),
    ("shield_dialog_body", "string"),
    ("shield_dialog_watch", "string"),
    ("shield_dialog_cancel", "string"),
    ("shield_ad_unavailable", "string"),
    ("shield_ad_loading", "string"),
    ("shield_ad_skipped", "string"),
    ("shield_ad_rewarded", "string"),
    ("btn_play_again", "string"),
    ("btn_restart", "string"),
    ("btn_leaderboard", "string"),
    ("btn_close", "string"),
    ("btn_clear", "string"),
    ("leaderboard_title", "string"),
    ("leaderboard_tab_global", "string"),
    ("leaderboard_tab_local", "string"),
    ("leaderboard_local_subtitle", "string"),
    ("leaderboard_empty", "string"),
    ("leaderboard_powered_by", "string"),
    ("leaderboard_load_failed", "string"),
    ("leaderboard_empty_global", "string"),
    ("leaderboard_retry", "string"),
    ("leaderboard_loading", "string"),
    ("leaderboard_updated_ago", "string"),
    ("leaderboard_refresh", "string"),
    ("status_game_over", "string"),
    ("status_split", "string"),
    ("status_frame_opened", "string"),
    ("status_hint_match_or_split", "string"),
    ("status_hint_match", "string"),
    ("hazard_fire_subtitle", "string"),
    ("hazard_ice_subtitle", "string"),
    ("hazard_poison_subtitle", "string"),
    ("cleansed_hazards", "plurals"),
    ("leaderboard_rank_format", "string"),
    ("leaderboard_entry_meta", "string"),
    ("combo_value_format", "string"),
    ("btn_tutorial", "string"),
    ("tutorial_step_counter", "string"),
    ("tutorial_cta_got_it", "string"),
    ("tutorial_cta_skip", "string"),
    ("tutorial_step_merge_instruction", "string"),
    ("tutorial_step_frame_instruction", "string"),
    ("tutorial_step_hazards_and_shield_initial", "string"),
    ("tutorial_step_hazards_and_shield_action", "string"),
    ("tutorial_step_shield_subhint", "string"),
    ("tutorial_step_leaderboard_instruction", "string"),
    ("tutorial_hint_drag_to_merge", "string"),
    ("tutorial_success_merge", "string"),
    ("tutorial_success_frame", "string"),
    ("tutorial_success_shield", "string"),
    ("tutorial_step_leaderboard_instruction_v2", "string"),
    ("tutorial_step_username_instruction", "string"),
    ("username_dialog_title_first", "string"),
    ("username_dialog_title_change", "string"),
    ("username_input_hint", "string"),
    ("username_too_short", "string"),
    ("username_too_long", "string"),
    ("username_checking", "string"),
    ("username_available", "string"),
    ("username_taken", "string"),
    ("username_network_error", "string"),
    ("username_save", "string"),
    ("username_cancel", "string"),
    ("username_cooldown", "string"),
    ("username_change_failed", "string"),
    ("username_change_saved", "string"),
    ("update_available", "string"),
    ("update_downloaded", "string"),
]

# Translation tables.
# Each language code maps to:
#   { key: value }                  for kind="string"
#   { key: {"one": "...", "other": "..."} }  for kind="plurals"
# %1$d / %2$d / %d placeholders MUST be preserved verbatim.

# Section headers used inside Android XML for readability when a translator
# inspects a single locale file.

# ---------------------------------------------------------------------------
# en (default values/) — KEPT IN PYTHON ONLY for reference; this script does
# not write values/strings.xml. The tuple is the canonical English source
# used to produce notes and for diff/QA tooling.
# ---------------------------------------------------------------------------
T["en"] = {
    "tagline": "merge · split · expand",
    "stat_score": "SCORE",
    "stat_turn": "TURN",
    "stat_combo": "COMBO",
    "stat_shield": "SHIELD",
    "shield_dialog_title": "Earn shields",
    "shield_dialog_body": "Watch a short video to earn 3 shields. Use them to cleanse Fire, Ice or Poison tiles.",
    "shield_dialog_watch": "Watch",
    "shield_dialog_cancel": "Not now",
    "shield_ad_unavailable": "Ad isn't available right now. Please try again later.",
    "shield_ad_loading": "Loading ad…",
    "shield_ad_skipped": "Watch the full video to earn shields.",
    "shield_ad_rewarded": "3 shields added.",
    "btn_play_again": "Play Again",
    "btn_restart": "Restart",
    "btn_leaderboard": "Leaderboard",
    "btn_close": "Close",
    "btn_clear": "Clear",
    "leaderboard_title": "LEADERBOARD",
    "leaderboard_tab_global": "Global",
    "leaderboard_tab_local": "Local",
    "leaderboard_local_subtitle": "Top 10 · local device",
    "leaderboard_empty": "No runs yet. Survive one full run to chart.",
    "leaderboard_powered_by": "Global rankings",
    "leaderboard_load_failed": "Couldn't load global scores.",
    "leaderboard_empty_global": "No scores yet. Play a round to be the first.",
    "leaderboard_retry": "Retry",
    "leaderboard_loading": "Loading…",
    "leaderboard_updated_ago": "Updated %1$d min ago",
    "leaderboard_refresh": "Refresh",
    "status_game_over": "GAME  OVER",
    "status_split": "Split into halves.",
    "status_frame_opened": "Frame opened!",
    "status_hint_match_or_split": "Tap match · double-tap to split",
    "status_hint_match": "Tap an adjacent match",
    "hazard_fire_subtitle": "FIRE %1$dt",
    "hazard_ice_subtitle": "ICE %1$dt",
    "hazard_poison_subtitle": "POIS %1$dt",
    "cleansed_hazards": {"one": "Cleansed %d hazard!", "other": "Cleansed %d hazards!"},
    "leaderboard_rank_format": "#%1$d",
    "leaderboard_entry_meta": "t%1$d · %2$d×%2$d",
    "combo_value_format": "×%1$d",
    "btn_tutorial": "How to play",
    "tutorial_step_counter": "%1$d / %2$d",
    "tutorial_cta_got_it": "Got it",
    "tutorial_cta_skip": "Skip Tutorial",
    "tutorial_step_merge_instruction": "Drag one tile onto the matching tile to merge them.",
    "tutorial_step_frame_instruction": "Merge these tiles to unlock new frame cells.",
    "tutorial_step_hazards_and_shield_initial": "These locked tiles are dangerous. Tap to learn how to clear them.",
    "tutorial_step_hazards_and_shield_action": "Use your shield to clear a hazard.",
    "tutorial_step_shield_subhint": "Tap the shield card to watch a video and earn 3 more shields.",
    "tutorial_step_leaderboard_instruction": "Your high scores are submitted to the global leaderboard automatically. Open it from the menu to see how you rank.",
    "tutorial_hint_drag_to_merge": "Try dragging the tiles together…",
    "tutorial_success_merge": "Nice merge!",
    "tutorial_success_frame": "Frame opened!",
    "tutorial_success_shield": "Hazard cleared!",
    "tutorial_step_leaderboard_instruction_v2": "Tap the Leaderboard button to see global rankings.",
    "tutorial_step_username_instruction": "Tap your row to pick a name.",
    "username_dialog_title_first": "Pick your name",
    "username_dialog_title_change": "Change your name",
    "username_input_hint": "Name",
    "username_too_short": "At least 2 characters",
    "username_too_long": "Max 20 characters",
    "username_checking": "Checking…",
    "username_available": "Available",
    "username_taken": "Already taken",
    "username_network_error": "Connection error",
    "username_save": "Save",
    "username_cancel": "Cancel",
    "username_cooldown": "You can change your name in %d days",
    "username_change_failed": "Save failed",
    "username_change_saved": "Name saved",
    "update_available": "Update",
    "update_downloaded": "Install",
}


# Helpers --------------------------------------------------------------------
def xml_escape(value: str) -> str:
    # Android string escaping: keep XML-safe + escape apostrophes/quotes.
    out = (
        value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )
    out = out.replace("'", "\\'").replace('"', '\\"')
    return out


VALID_QTY_ORDER = ("zero", "one", "two", "few", "many", "other")


def extract_placeholders(value: str) -> set:
    # Match Android positional placeholders %1$d, %2$d, %s, %d. Their set
    # MUST match master so format calls don't blow up at runtime.
    import re
    out = set()
    for m in re.finditer(r"%(?:\d+\$)?[ds]", value):
        out.add(m.group(0))
    return out


def render_xml(lang_code: str, table: dict) -> str:
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    master = T["en"]
    for key, kind in MASTER_KEYS:
        if kind == "string":
            value = table[key]
            # Placeholder parity check vs master.
            if extract_placeholders(value) != extract_placeholders(master[key]):
                raise SystemExit(
                    f"[{lang_code}] placeholder mismatch in '{key}': "
                    f"master={extract_placeholders(master[key])} "
                    f"local={extract_placeholders(value)}"
                )
            lines.append(f'    <string name="{key}">{xml_escape(value)}</string>')
        else:
            entry = table[key]
            # Plurals can carry a subset of CLDR categories per locale.
            qtys = [q for q in VALID_QTY_ORDER if q in entry]
            if "other" not in qtys:
                raise SystemExit(f"[{lang_code}] plurals '{key}' missing 'other'")
            master_ph = extract_placeholders(master[key]["other"])
            # The 'other' form must match master placeholders exactly. Other
            # quantity forms (zero/one/two) MAY drop the numeric placeholder
            # because the word form itself encodes the count (e.g. Arabic
            # "خطرين" = two hazards).
            if extract_placeholders(entry["other"]) != master_ph:
                raise SystemExit(
                    f"[{lang_code}] placeholder mismatch in plurals "
                    f"'{key}'/other"
                )
            for qty in qtys:
                local_ph = extract_placeholders(entry[qty])
                # No form may introduce a placeholder that isn't in master.
                if not local_ph.issubset(master_ph):
                    raise SystemExit(
                        f"[{lang_code}] plurals '{key}'/{qty} has extra "
                        f"placeholders {local_ph - master_ph}"
                    )
            lines.append(f'    <plurals name="{key}">')
            for qty in qtys:
                lines.append(
                    f'        <item quantity="{qty}">{xml_escape(entry[qty])}</item>'
                )
            lines.append("    </plurals>")
    lines.append("</resources>")
    lines.append("")
    return "\n".join(lines)


def main():
    # Resolve which languages to write (every entry except "en", which is the
    # default values/ resource and not written by this generator).
    written = []
    for lang in sorted(LOCALE_FOLDERS.keys()):
        if lang == "en":
            continue
        folder = LOCALE_FOLDERS[lang]
        out_dir = RES_DIR / folder
        out_dir.mkdir(parents=True, exist_ok=True)
        if lang not in T:
            print(f"WARN: missing translations for {lang}", file=sys.stderr)
            continue
        xml = render_xml(lang, T[lang])
        (out_dir / "strings.xml").write_text(xml, encoding="utf-8")
        written.append(lang)
    print(f"Wrote {len(written)} locale files: {', '.join(written)}")


# Locale code → Android folder name. Most are values-{code}; entries with a
# region use values-{lang}-r{REGION}; BCP-47 codes (es-419) use values-b+...
LOCALE_FOLDERS = {
    # already-shipped locales (kept aligned)
    "ar": "values-ar",
    "de": "values-de",
    "es": "values-es",
    "fr": "values-fr",
    "hi": "values-hi",
    "in": "values-in",
    "it": "values-it",
    "ja": "values-ja",
    "ko": "values-ko",
    "nl": "values-nl",
    "pl": "values-pl",
    "pt-BR": "values-pt-rBR",
    "ru": "values-ru",
    "th": "values-th",
    "tr": "values-tr",
    "uk": "values-uk",
    "vi": "values-vi",
    "zh-CN": "values-zh-rCN",
    "zh-TW": "values-zh-rTW",
    # new locales (FAZ 3)
    "es-419": "values-b+es+419",
    "pt-PT": "values-pt-rPT",
    "fr-CA": "values-fr-rCA",
    "da": "values-da",
    "sv": "values-sv",
    "nb": "values-nb",
    "fi": "values-fi",
    "el": "values-el",
    "hu": "values-hu",
    "cs": "values-cs",
    "sk": "values-sk",
    "ro": "values-ro",
    "bg": "values-bg",
    "hr": "values-hr",
    "sr": "values-sr",
    "sl": "values-sl",
    "iw": "values-iw",  # Hebrew (Android legacy code)
    "fa": "values-fa",
    "ur": "values-ur",
    "bn": "values-bn",
    "ta": "values-ta",
    "te": "values-te",
    "mr": "values-mr",
    "ml": "values-ml",
    "gu": "values-gu",
    "kn": "values-kn",
    "pa": "values-pa",
    "fil": "values-fil",
    "ms": "values-ms",
    "sw": "values-sw",
}


if __name__ == "__main__":
    # The translation tables are populated by importing the per-language
    # modules. Each module appends to the global T dict.
    import translations  # noqa: F401
    import translations_part2  # noqa: F401
    import translations_part3  # noqa: F401
    import translations_part4  # noqa: F401
    main()

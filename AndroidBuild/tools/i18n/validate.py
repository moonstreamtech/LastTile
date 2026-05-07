#!/usr/bin/env python3
# Geçiş 1: key consistency + placeholder + non-empty + no-TODO across every
# values-*/strings.xml. Master is values/strings.xml.

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[3]
RES_DIR = REPO_ROOT / "AndroidBuild" / "app" / "src" / "main" / "res"
MASTER = RES_DIR / "values" / "strings.xml"

PLACEHOLDER_RE = re.compile(r"%(?:\d+\$)?[ds]")


def parse(path):
    tree = ET.parse(path)
    root = tree.getroot()
    strings = {}
    plurals = {}
    translatable_false = set()
    for el in root:
        name = el.get("name")
        if el.tag == "string":
            if el.get("translatable") == "false":
                translatable_false.add(name)
                continue
            strings[name] = el.text or ""
        elif el.tag == "plurals":
            forms = {}
            for item in el:
                forms[item.get("quantity")] = item.text or ""
            plurals[name] = forms
    return strings, plurals, translatable_false


def main():
    master_strings, master_plurals, master_nontrans = parse(MASTER)
    master_keys = set(master_strings) | set(master_plurals)
    print(f"Master: {len(master_strings)} strings, {len(master_plurals)} plurals, "
          f"{len(master_nontrans)} non-translatable")

    failures = []
    seen = []
    for d in sorted(RES_DIR.glob("values-*")):
        lang = d.name[len("values-"):]
        f = d / "strings.xml"
        if not f.exists():
            continue
        seen.append(lang)
        s, p, nt = parse(f)
        if nt:
            failures.append(f"{lang}: contains translatable=false keys: {nt}")
        local_keys = set(s) | set(p)
        missing = master_keys - local_keys
        extra = local_keys - master_keys
        if missing:
            failures.append(f"{lang}: missing {sorted(missing)}")
        if extra:
            failures.append(f"{lang}: extra {sorted(extra)}")
        for k, v in s.items():
            if not v.strip():
                failures.append(f"{lang}:{k} empty")
            if "TODO" in v or "TRANSLATE" in v.upper() or "PLACEHOLDER" in v.upper():
                failures.append(f"{lang}:{k} has placeholder marker")
            if k in master_strings:
                m_ph = set(PLACEHOLDER_RE.findall(master_strings[k]))
                l_ph = set(PLACEHOLDER_RE.findall(v))
                if m_ph != l_ph:
                    failures.append(
                        f"{lang}:{k} placeholder mismatch master={m_ph} local={l_ph}"
                    )
        for k, forms in p.items():
            if k not in master_plurals:
                continue
            if "other" not in forms:
                failures.append(f"{lang}:{k} plurals missing 'other'")
                continue
            m_ph = set(PLACEHOLDER_RE.findall(master_plurals[k]["other"]))
            for q, val in forms.items():
                if not val.strip():
                    failures.append(f"{lang}:{k}/{q} empty")
                if "TODO" in val:
                    failures.append(f"{lang}:{k}/{q} has TODO")
                l_ph = set(PLACEHOLDER_RE.findall(val))
                if not l_ph.issubset(m_ph):
                    failures.append(
                        f"{lang}:{k}/{q} extra placeholders {l_ph - m_ph}"
                    )
            # The 'other' form must be placeholder-equal to master.
            o_ph = set(PLACEHOLDER_RE.findall(forms["other"]))
            if o_ph != m_ph:
                failures.append(
                    f"{lang}:{k}/other placeholder mismatch master={m_ph} local={o_ph}"
                )
    print(f"Checked {len(seen)} locales: {', '.join(seen)}")
    if failures:
        print("\nFAIL:")
        for f in failures:
            print(f"  {f}")
        sys.exit(1)
    print("OK: all locales consistent with master")


if __name__ == "__main__":
    main()

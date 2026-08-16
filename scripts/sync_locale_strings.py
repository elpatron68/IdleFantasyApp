#!/usr/bin/env python3
"""Locale hygiene + stub sync for Android string resources.

Run from the repo root after adding new strings to app/src/main/res/values/:
    python3 scripts/sync_locale_strings.py

Per string entry (base files first, then every locale file):
  1. Multi-line <string> entries are joined to one line, real newlines
     becoming literal \\n (Android collapses raw newlines to a space).
  2. Percent canonicalization: %-runs are collapsed (%% -> % until stable,
     positional specifiers like %1$d untouched), then a lone literal % is
     re-escaped to %% only when the BASE string carries positional args
     (formatted string). No-arg strings keep single % (they are consumed
     raw, where %% would display doubled).
  3. Locale lines carrying <!-- untranslated --> are rewritten to the
     canonical base text (stubs must mirror base exactly).
  4. Keys present in base but absent from a locale are inserted as stubs
     at the base-order position. translatable="false" keys are skipped
     (locales do not carry them).

Plurals blocks and comments are left untouched. Prints a change report.
"""
import glob
import os
import re

BASE_DIR = "app/src/main/res/values"
STR_OPEN_RE = re.compile(r'<string name="([^"]+)"([^>]*)>')
FULL_RE = re.compile(r'(<string name="([^"]+)"[^>]*>)(.*)(</string>)')
ARG_RE = re.compile(r"%\d+\$")
MARKER = "<!-- untranslated -->"

report = []


# Full positional Java format specifier: %1$d, %1$s, but also flag/width/
# precision forms like %1$.2f or %1$,d. A letter-only tail check would
# misread those as literal percents and corrupt them into %%.
SPEC_RE = re.compile(r"%\d+\$[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")


def canon_percents(text, formatted):
    """Collapse %% runs to literal %, then re-escape iff formatted."""
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if c != "%":
            out.append(c)
            i += 1
            continue
        m = SPEC_RE.match(text, i)
        if m:
            out.append(m.group(0))
            i = m.end()
            continue
        # A %-run that is not a specifier means literal percents. Consume the
        # run pairwise so a specifier straight after an escape (%%%1$d)
        # survives; no game string legitimately shows consecutive % signs, so
        # any leftover odd % merges into the same single literal.
        j = i
        while j < n and text[j] == "%":
            if SPEC_RE.match(text, j):
                break
            j += 1
        out.append("%%" if formatted else "%")
        i = j
    return "".join(out)


def ensure_formatted_false(line, key):
    """Mark no-arg strings containing a literal % as formatted=\"false\" so
    lint doesn't parse sequences like '% b' as format conversions."""
    if 'formatted="false"' in line:
        return line
    new = line.replace(f'<string name="{key}"', f'<string name="{key}" formatted="false"', 1)
    if new != line:
        report.append(f"  formatted=false: {key}")
    return new


def join_multiline(lines):
    """Merge <string> entries spanning several physical lines."""
    out, i = [], 0
    while i < len(lines):
        line = lines[i]
        if "<string " in line and "</string>" not in line:
            buf = [line]
            while "</string>" not in buf[-1]:
                i += 1
                buf.append(lines[i])
            joined = "\\n".join(p.strip() if k else p.rstrip() for k, p in enumerate(buf))
            out.append(joined)
            report.append(f"  joined multi-line: {STR_OPEN_RE.search(line).group(1)}")
        else:
            out.append(line)
        i += 1
    return out


def parse_base(path):
    entries = {}  # key -> (canonical_text, formatted, translatable)
    order = []
    with open(path, encoding="utf-8") as f:
        lines = join_multiline(f.read().split("\n"))
    for line in lines:
        m = FULL_RE.search(line)
        if not m:
            continue
        key, attrs = m.group(2), m.group(1)
        formatted = bool(ARG_RE.search(m.group(3)))
        translatable = 'translatable="false"' not in attrs
        entries[key] = [canon_percents(m.group(3), formatted), formatted, translatable]
        order.append(key)
    return entries, order, lines


def fix_base(path):
    entries, order, lines = parse_base(path)
    out = []
    for line in lines:
        m = FULL_RE.search(line)
        if m:
            key = m.group(2)
            new = entries[key][0]
            if new != m.group(3):
                line = line.replace(m.group(1) + m.group(3) + m.group(4), m.group(1) + new + m.group(4))
                report.append(f"  base %-fix: {key}")
            if not entries[key][1] and "%" in new:
                line = ensure_formatted_false(line, key)
        out.append(line)
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(out))
    return entries, order


def fix_locale(path, base_entries, base_order):
    with open(path, encoding="utf-8") as f:
        lines = join_multiline(f.read().split("\n"))
    present = set()
    out = []
    for line in lines:
        m = FULL_RE.search(line)
        if not m:
            out.append(line)
            continue
        key, text = m.group(2), m.group(3)
        present.add(key)
        if key not in base_entries:
            report.append(f"  EXTRA key kept (not in base): {key}")
            out.append(line)
            continue
        base_text, formatted, _ = base_entries[key]
        if MARKER in line:
            new = base_text
        else:
            new = canon_percents(text, formatted)
        if new != text:
            line = line.replace(m.group(1) + text + m.group(4), m.group(1) + new + m.group(4))
            report.append(f"  fix ({os.path.basename(os.path.dirname(path))}): {key}")
        if not formatted and "%" in new:
            line = ensure_formatted_false(line, key)
        out.append(line)
    # insert missing translatable keys as stubs at base-order position
    missing = [k for k in base_order if k not in present and base_entries[k][2]]
    for key in missing:
        idx = base_order.index(key)
        anchor = None
        for prev in reversed(base_order[:idx]):
            if prev in present:
                anchor = prev
                break
        text, formatted, _ = base_entries[key]
        attr = ' formatted="false"' if (not formatted and "%" in text) else ""
        stub = f'    <string name="{key}"{attr}>{text}</string> {MARKER}'
        if anchor is None:
            for i, l in enumerate(out):
                if "<resources" in l:
                    out.insert(i + 1, stub)
                    break
        else:
            for i, l in enumerate(out):
                mm = FULL_RE.search(l)
                if mm and mm.group(2) == anchor:
                    out.insert(i + 1, stub)
                    break
        present.add(key)
        report.append(f"  stub added ({os.path.basename(os.path.dirname(path))}): {key}")
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(out))


for fname in sorted(os.path.basename(p) for p in glob.glob(BASE_DIR + "/strings*.xml")):
    report.append(f"== {fname}")
    base_entries, base_order = fix_base(f"{BASE_DIR}/{fname}")
    for d in sorted(glob.glob("app/src/main/res/values-*/")):
        loc = os.path.join(d, fname)
        if os.path.exists(loc):
            fix_locale(loc, base_entries, base_order)

for r in report:
    print(r)

#!/usr/bin/env python3
"""
Check the game's translations (see doc/tools/translations.md): for each
language, XFTL's own strings (src/main/resources/assets/lang/<lang>.xml,
translating en.xml) and the Chonkestrel mod's text
(mods/chonkestrel/data/text-<lang>.xml.append, translating the mod's English
text files).

    python3 tools/check_translations.py              # every language
    python3 tools/check_translations.py fr ru        # only these
    python3 tools/check_translations.py fr --list    # and list what's missing
    python3 tools/check_translations.py fr --todo    # print the missing ones, in English, to translate
    python3 tools/check_translations.py --strict     # missing ones are errors too

For each, it lists:

- the strings that have no translation yet (they're shown in English);
- the translations of strings that aren't in English (a typo in the name,
  or a string that's gone);
- placeholders (\\1, \\2...) that aren't the same as the English's;
- characters FTL's fonts can't draw (they'd be shown as question marks),
  from the fonts in ftl.dat (the --dat path, XFTL_DATAFILE, or ./ftl.dat);
- empty translations, ones given twice, and files that aren't valid XML;
- as warnings, line breaks (\\n) that aren't the same as the English's.

It exits with 1 if there are any errors (and with --strict, any missing
strings), or 0.
"""
import argparse
import os
import re
import struct
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from xml.sax.saxutils import unescape

ROOT = Path(__file__).resolve().parent.parent
LANG_DIR = ROOT / "src" / "main" / "resources" / "assets" / "lang"
MOD_DATA = ROOT / "mods" / "chonkestrel" / "data"

LANGUAGES = ["fr", "de", "es", "it", "ru"]

# The mod's English text files (see tools/mod_text.py)
MOD_ENGLISH = ["text_events.xml.append", "text_blueprints.xml.append", "text_achievements.xml.append",
               "text_misc.xml.append", "text_sectorname.xml.append"]

# XFTL's strings shown before there's an ftl.dat (the datafile selection and
# screen-too-small screens), in a font with only English letters, which
# stay in English
ENGLISH_ONLY = ("xftl_select_datafile_", "xftl_screen_small_")

# The fonts the game's text is drawn in (the others are for numbers): a
# character must be in all of them, as the same text can be shown in several
FONTS = ["JustinFont8", "JustinFont10", "JustinFont11Bold", "JustinFont12Bold", "HL1", "HL2"]

# What to write instead of the characters the fonts most often lack
SUGGESTIONS = {
    "‘": "'", "’": "'", "“": '"', "”": '"', "„": '"',
    "–": "-", "—": "-", "…": "...", " ": "a space", " ": "a space",
}

PLACEHOLDER = re.compile(r"\\[1-9]")
ENTRY = re.compile(r'<text name="([^"]+)"\s*>(.*?)</text>'
                   r'|<mod:findName type="text" name="([^"]+)"\s*>\s*<mod:setValue>(.*?)</mod:setValue>', re.S)


def unescaped(text):
    return unescape(text, {"&quot;": '"', "&apos;": "'"}).strip()


def english_texts(path):
    """[path]'s keys and English, in order (as plain <text>s, or vanilla's changed with mod:findName)."""
    text = re.sub(r"<!--.*?-->", "", path.read_text(encoding="utf-8"), flags=re.S)
    result = {}
    for match in ENTRY.finditer(text):
        key = match.group(1) or match.group(3)
        raw = match.group(2) if match.group(1) else match.group(4)
        result[key] = raw.strip()
    return result


def parse_translations(path, fragment):
    """
    [path]'s translations, as (key, text, line) in order, or raises
    ET.ParseError. A mod's .append file is a fragment, with no root.
    """
    text = path.read_text(encoding="utf-8")
    if fragment:
        text = '<wrap xmlns:mod="mod">' + text + "</wrap>"
    root = ET.fromstring(text)
    # (the <text>s' lines, in the same order, as ElementTree hasn't got them)
    lines = [text[:m.start()].count("\n") + 1 for m in re.finditer(r"<text\b", re.sub(r"<!--.*?-->", lambda c: re.sub(r"\S", " ", c.group(0)), text, flags=re.S))]
    result = []
    for i, elem in enumerate(root.iter("text")):
        result.append((elem.get("name"), (elem.text or "").strip(), lines[i] if i < len(lines) else 0))
    for elem in root.iter("{mod}findName"):
        value = elem.find("{mod}setValue")
        result.append((elem.get("name"), (value.text or "").strip() if value is not None else "", 0))
    return result


# --- Fonts -----------------------------------------------------------------

def read_dat(path):
    """The files in ftl.dat at [path], by name, as a function to read one."""
    data = Path(path).read_bytes()
    header, entry, count, names_size = struct.unpack(">HHII", data[4:16])
    table = data[header + count * entry:header + count * entry + names_size]
    files = {}
    for i in range(count):
        e = data[header + i * entry:header + (i + 1) * entry]
        name_offset = int.from_bytes(e[5:8], "big")
        offset, _, size = struct.unpack(">III", e[8:20])
        files[table[name_offset:table.index(b"\0", name_offset)].decode()] = (offset, size)
    return lambda name: data[files[name][0]:files[name][0] + files[name][1]]


def font_chars(font):
    """The characters in a .font file (see SILFontLoader)."""
    offset, = struct.unpack(">I", font[8:12])
    count, size = struct.unpack(">HH", font[12:16])
    return {chr(struct.unpack(">I", font[offset + i * size:offset + i * size + 4])[0]) for i in range(count)}


def drawable_chars(dat_path):
    """The characters every one of FONTS has, or None if there's no ftl.dat."""
    if not dat_path or not Path(dat_path).is_file():
        return None
    read = read_dat(dat_path)
    chars = None
    for name in FONTS:
        have = font_chars(read("fonts/%s.font" % name))
        chars = have if chars is None else chars & have
    return chars


# --- Checks ----------------------------------------------------------------

class Report:
    def __init__(self, lang, part, path):
        self.lang = lang
        self.part = part
        self.path = path
        self.translated = 0
        self.total = 0
        self.missing = []
        self.errors = []
        self.warnings = []


def check(lang, part, path, english, fragment, chars):
    report = Report(lang, part, path)
    report.total = len(english)
    if not path.exists():
        report.errors.append("%s is missing" % path.relative_to(ROOT))
        report.missing = list(english)
        return report
    try:
        entries = parse_translations(path, fragment)
    except ET.ParseError as error:
        report.errors.append("not valid XML: %s" % error)
        report.missing = list(english)
        return report

    seen = set()
    for key, text, line in entries:
        where = "%s:%d" % (path.name, line) if line else path.name
        if key in seen:
            report.errors.append("%s: %s is translated twice" % (where, key))
        seen.add(key)
        if key not in english:
            report.errors.append("%s: %s isn't in English" % (where, key))
            continue
        if not text:
            report.errors.append("%s: %s is empty" % (where, key))
            continue
        source = unescaped(english[key])
        if sorted(set(PLACEHOLDER.findall(text))) != sorted(set(PLACEHOLDER.findall(source))):
            report.errors.append("%s: %s has placeholders %s, but the English has %s" % (
                where, key, " ".join(sorted(set(PLACEHOLDER.findall(text)))) or "none",
                " ".join(sorted(set(PLACEHOLDER.findall(source)))) or "none"))
        if text.count("\\n") != source.count("\\n"):
            report.warnings.append("%s: %s has %d line breaks (\\n), the English %d" % (
                where, key, text.count("\\n"), source.count("\\n")))
        if chars is not None:
            bad = sorted({c for c in text if c not in chars and not c.isspace()})
            if bad:
                report.errors.append("%s: %s has characters the fonts can't draw: %s" % (where, key, ", ".join(
                    "%s (U+%04X%s)" % (c, ord(c), ", use %s" % SUGGESTIONS[c] if c in SUGGESTIONS else "")
                    for c in bad)))
    report.missing = [key for key in english if key not in seen]
    report.translated = len([key for key in english if key in seen])
    return report


def engine_english():
    texts = english_texts(LANG_DIR / "en.xml")
    return {k: v for k, v in texts.items() if not k.startswith(ENGLISH_ONLY)}


def mod_english():
    """The mod's English, by file, and all of it."""
    by_file = {name: english_texts(MOD_DATA / name) for name in MOD_ENGLISH}
    merged = {}
    for texts in by_file.values():
        merged.update(texts)
    return by_file, merged


def todo(lang, engine, mod_by_file, reports):
    """Print the missing strings, in English, ready to translate into each file."""
    for report in reports:
        missing = set(report.missing)
        if not missing:
            continue
        print("<!-- Missing from %s -->" % report.path.relative_to(ROOT))
        if report.part == "engine":
            for key, text in engine.items():
                if key in missing:
                    print('    <text name="%s">%s</text>' % (key, text))
        else:
            for name, texts in mod_by_file.items():
                keys = [k for k in texts if k in missing]
                if keys:
                    print("<!-- From %s -->" % name)
                    for key in keys:
                        print('<text name="%s">%s</text>' % (key, texts[key]))
                    missing -= set(keys)
        print()


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("languages", nargs="*", default=LANGUAGES, help="the languages to check (all of them by default)")
    parser.add_argument("--list", action="store_true", help="list the missing strings' names")
    parser.add_argument("--todo", action="store_true", help="print the missing strings in English, to translate")
    parser.add_argument("--only", choices=["engine", "mod"], help="only XFTL's own strings, or only the mod's")
    parser.add_argument("--strict", action="store_true", help="missing strings are errors too")
    parser.add_argument("--dat", default=os.environ.get("XFTL_DATAFILE") or str(ROOT / "ftl.dat"),
                        help="FTL's ftl.dat, for its fonts")
    args = parser.parse_args()

    for lang in args.languages:
        if lang not in LANGUAGES:
            parser.error("unknown language %s (they're %s)" % (lang, ", ".join(LANGUAGES)))

    engine = engine_english()
    mod_by_file, mod = mod_english()
    chars = drawable_chars(args.dat)

    failed = False
    for lang in args.languages:
        reports = []
        if args.only != "mod":
            reports.append(check(lang, "engine", LANG_DIR / ("%s.xml" % lang), engine, False, chars))
        if args.only != "engine":
            reports.append(check(lang, "mod", MOD_DATA / ("text-%s.xml.append" % lang), mod, True, chars))

        if args.todo:
            todo(lang, engine, mod_by_file, reports)
            continue

        print("%s:" % lang)
        for report in reports:
            name = "XFTL's strings" if report.part == "engine" else "the mod's text"
            print("  %s (%s): %d of %d translated, %d missing" % (
                name, report.path.relative_to(ROOT), report.translated, report.total, len(report.missing)))
            if args.list:
                for key in report.missing:
                    print("    missing: %s" % key)
            for error in report.errors:
                print("    ERROR %s" % error)
            for warning in report.warnings:
                print("    warning: %s" % warning)
            if report.errors or (args.strict and report.missing):
                failed = True

    if chars is None and not args.todo:
        print("(no ftl.dat at %s, so the characters weren't checked: pass --dat)" % args.dat)
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()

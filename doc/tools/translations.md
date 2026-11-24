# Translations

The game can be played in English, French, German, Spanish, Italian and
Russian, picked in the options window (Options, then Language), or with
`XFTL_LANG` (see [parameters.md](parameters.md)). This is where its text
is, how to translate it, and how to check a translation.

## Where the text is

The game's text comes in three layers, each over the last, so anything a
language hasn't got yet is shown in English (see `Translator`):

| Text | English | Translations |
|---|---|---|
| Vanilla FTL's | `data/text_*.xml` in `ftl.dat` | `data/text-<lang>.xml` in `ftl.dat`, already done |
| XFTL's own (the menus, windows and messages it adds) | `src/main/resources/assets/lang/en.xml` | `src/main/resources/assets/lang/<lang>.xml` |
| The Chonkestrel mod's (its events, ships, augments, achievements and sectors) | `mods/chonkestrel/data/text_*.xml.append` | `mods/chonkestrel/data/text-<lang>.xml.append` |

So a language is two files to fill in: `assets/lang/<lang>.xml` (about
140 strings) and `text-<lang>.xml.append` (about 2,400). Each has a header
with the rules. Each string is a `<text>` with the same `name` as the
English one:

```xml
<text name="event_XFTL_ROCK_MINE_c2_choice">Demander si quelqu'un veut quitter la mine.</text>
```

The mod's English is in five files, each appending to vanilla's file of the
same name (a mod can only append to a file vanilla has):

- `text_events.xml.append`: the events, in a section for each events file
  (`<!-- BEGIN text of events_rock.xml.append -->`), and the vanilla texts the
  mod changes (with `<mod:findName type="text">`, such as the fuel
  stations' choices, which must be translated too: otherwise vanilla's own
  translation of the old text shows);
- `text_blueprints.xml.append`: the Chonkestrel's and the augments'
  names and descriptions, and the generated ships' classes and names, in a
  block for each script that makes them;
- `text_achievements.xml.append`: the ship achievements;
- `text_sectorname.xml.append`: the sectors' names;
- `text_misc.xml.append`: the rest (the Mainframe on the map, the relay
  codes' line).

The translations of all five go in the one `text-<lang>.xml.append`,
appended to vanilla's `text-<lang>.xml`.

The strings shown before the game has found `ftl.dat`
(`xftl_select_datafile_*` and `xftl_screen_small_*`) stay in English: they're
drawn in a font with only English letters.

## The rules

- Keep the placeholders `\1`, `\2` and `\3`: the game puts a number or a
  name there. They can move within the sentence.
- `\n` is a line break. The short texts with them (the confirmation boxes,
  the warnings) are laid out by hand, so keep theirs.
- Escape `&` as `&amp;`, `<` as `&lt;` and `>` as `&gt;`. Quotes and
  apostrophes are fine as they are.
- FTL's fonts (`fonts/*.font` in `ftl.dat`) have ASCII, the Latin letters
  with accents and the Cyrillic alphabet, « », „ and ”, but not “, ‘ ’ (the
  typographic apostrophe), – —, or …: use `"`, `'`, `-` and `...`. The
  check below lists anything they can't draw.
- Buttons and titles have a fixed size: keep them about as long as the
  English. Ones in capitals are in capitals in the game.
- Use vanilla's own translation's words (its `text-<lang>.xml`) for the
  systems, races, resources and so on, and the same word for the same thing
  everywhere.

## Checking a translation

```sh
python3 tools/check_translations.py           # every language
python3 tools/check_translations.py fr        # one
python3 tools/check_translations.py fr --list # and the names of the missing strings
python3 tools/check_translations.py fr --todo # the missing strings, in English, to start from
python3 tools/check_translations.py --strict  # missing strings are errors too
```

For each language, and each of its two files, it says how many strings are
translated, and lists as errors: translations of strings that aren't in
English (a typo in the name, or a string that's gone), placeholders that
aren't the English's, characters the fonts can't draw (from `ftl.dat`:
`--dat`, `XFTL_DATAFILE` or `./ftl.dat`), empty translations, ones given
twice, and files that aren't valid XML; and as warnings, line breaks that
aren't the English's. It exits with 1 if there are errors. With nothing
translated yet it says:

```
fr:
  XFTL's strings (src/main/resources/assets/lang/fr.xml): 0 of 136 translated, 136 missing
  the mod's text (mods/chonkestrel/data/text-fr.xml.append): 0 of 2379 translated, 2379 missing
```

`--todo` prints every missing string as it is in English, under a comment
naming the file it goes in, and the mod's under the English file they're
from: translate them in place and paste them into the files.

`ModTextTest` checks that the mod's data has no text written into it,
that every key it refers to has English, and that every language loads.

## Writing new text

Write events and blueprints as usual, with the English in them, then run:

```sh
python3 tools/extract_mod_text.py           # move the text out
python3 tools/extract_mod_text.py --check   # only list what it would move
```

It moves each text the game shows (events' `<text>`, blueprints'
`<title>`, `<desc>`, `<class>` and so on, achievements' `<name>` and
`<desc>`) into the English text file, replacing it with a key
(`<text id="event_XFTL_ROCK_MINE_c2_choice"/>`), named the way vanilla's
are (see the script). Text already moved isn't touched, so running it again
changes nothing; to change some English, edit it in the text file. The new
strings then show up as missing in each language's check.

The scripts that make the mod's ships (`make art`, see
[BUILD.md](../../BUILD.md)) and `tools/hybrid_sectors.py` write keys
themselves, with their English in a block of their own in
`text_blueprints.xml.append` and `text_events.xml.append` (see
`tools/mod_text.py`), so a ship they add or rename shows up as missing in
the translations too. Their text is translated like the rest, one string
at a time, rather than made from parts in each language: names like
"Rock-Zoltan Hybrid Long Scout" have their words in a different order (and
with different endings) in other languages.

## Adding a language

1. Add it to `Language` (its code, and its name in itself), and to
   `LANGUAGES` in `tools/check_translations.py`.
2. Copy `assets/lang/fr.xml` and `text-fr.xml.append` to the new code,
   with the header changed and no strings.
3. If vanilla has no `data/text-<code>.xml` for it, the mod's file has
   nothing to append to: it'd need a translation file of its own, which
   `Translator` would load.
4. Check that the fonts have its letters (`check_translations.py` lists any
   they haven't).

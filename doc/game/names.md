# Crew names

New crew get a random name from a list, picked by their sex (for humans; other
races can get any name). XFTL can use the game's own names, or one of its own
presets instead.

## Picking a preset

The main menu's new game window has a button for each preset, in three
groups: `default` (picked to begin with), Random (one of the presets, picked
when the game starts) and `all`; then the countries' and languages' presets;
then the characters' ones. The game keeps the preset in its
save, so crew who join after it's loaded are named from it too.

Outside the menu (with `XFTL_SKIP_MENU`, or from the hangar), set the
`XFTL_NAMES` environment variable (or the `xftl.names` system
property) to a preset's name. The `Makefile` has a `NAMES` variable for this:

```sh
make run NAMES=startrek
XFTL_NAMES=japanese ./gradlew run
```

Leaving it out, or setting it to `default`, uses the game's names. The
release packages set it to `all` (`GAME_OPTIONS` in `tools/release/package.py`),
so players get every preset; `XFTL_NAMES` still overrides that. An unknown
preset prints a warning and uses the game's names too.

`all` uses every preset's names at once (3600 of them). A crew member's name
list is picked before the name, so each preset is about as likely as the
others. It's the `Makefile`'s default.

## Presets

Each preset has 100 male and 100 female names, with no repeats, and none
longer than 14 characters so they fit in the UI. They replace the game's
names entirely.

| Preset     | Names                                                       | Examples                                   |
|------------|-------------------------------------------------------------|--------------------------------------------|
| `default`  | The game's own (169 in English), plus the Chonkestrel mod's | Kirby, Jill, and from the mod Aarav, Freya |
| `american` | Common American names                                       | James, Wyatt / Mary, Savannah              |
| `english`  | Common English names, old and new                           | Rupert, Barnaby / Imogen, Harriet          |
| `irish`    | Irish names, some with a family name, without accents       | Padraig, Aidan Murphy / Niamh, Saoirse     |
| `scottish` | Scottish names, some with a family name                     | Hamish, Angus Grant / Morag, Eilidh        |
| `french`   | French names                                                | Baptiste, Gaston / Amelie, Oceane          |
| `spanish`  | Spanish and Latin American names                            | Alvaro, Cristobal / Rocio, Ximena          |
| `italian`  | Italian names                                               | Tommaso, Gianluca / Ginevra, Ornella       |
| `german`   | German names                                                | Jurgen, Konrad / Heidi, Wiebke             |
| `russian`  | Russian names, romanised                                    | Dmitri, Fyodor / Ksenia, Vasilisa          |
| `arabic`   | Arabic names, romanised                                     | Khalid, Tariq / Layla, Yasmin              |
| `turkish`  | Turkish names, without the Turkish letters                  | Emre, Yigit / Zeynep, Ozlem                |
| `indian`   | Indian names                                                | Arjun, Vikram / Priya, Ananya              |
| `chinese`  | Chinese names, romanised, family name first                  | Li Wei, Zhou Gang / Wang Fang, Lin Yu      |
| `japanese` | Japanese names, romanised                                   | Haruto, Isamu / Sakura, Momoko             |
| `korean`   | Korean names, romanised, with a hyphen                      | Min-jun, Ji-hoon / Seo-yeon, Ha-eun        |
| `starwars` | Star Wars characters, from the films, series and books       | Luke, Cad Bane / Leia, Mara Jade           |
| `startrek` | Star Trek characters, from every series                     | Kirk, La Forge / Uhura, T'Pol              |
| `masseffect` | Mass Effect characters, from the games, books and comics  | Garrus, Kai Leng / Liara, Shala'Raan       |

The Russian, Arabic, Chinese, Japanese and Korean names are in the Latin
alphabet, as the game's fonts don't have those alphabets, the Turkish names
are without the Turkish letters (c for ç, s for ş and so on), and the Irish names don't
have accents (fadas) in case the fonts don't have them either. There aren't
100 common Irish and Scottish first names of each sex, so those presets make
up the rest with first names and common family names, like the Chinese
preset. The Star Wars and Star
Trek characters are split by the character's sex, as are the Mass Effect
ones, which also use some characters' family names (Lawson, T'Soni) to make
up 100 women.

## The game's names

The game's names are in `data/names.xml`. There's two English lists each of
male and female names, along with translations into nine other languages.
The game picks a list first and then a name from it, so names in shorter
lists come up more often: for example, half of all women get one of the three
names in the second female list (Beth, Debbie and Kara).

The Chonkestrel mod adds 169 more names (140 male and 29 female, as many as
the game has) to the first English lists, in `names.xml.append`, as it has
room for sixteen crew.

## Adding a preset

Presets are in `src/main/resources/assets/data/names`, one file each, in the
same format as `names.xml`: a `nameList` element for each sex, with a `name`
element for each name. To add one, add a file there and add its name to
`CrewNameManager.PRESETS`, and its button's label (`xftl_names_<preset>`) to
`assets/lang/en.xml`. `CrewNamePresetTest` checks every preset.

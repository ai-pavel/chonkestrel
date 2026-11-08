# Crew skills

Vanilla's crew skills have three levels: none (white), half learnt (green)
and fully learnt (yellow). XFTL has six, from 0 to 5: none (white), four
green levels, and fully learnt (yellow). Each level gives a bigger bonus
than the last, and learning a skill fully takes twice as many actions as in
vanilla. The code is `SkillLevel` and `Skill` in `LivingCrew.kt`.

## The bonuses

For each level, 0 to 5 (vanilla's three in brackets):

| Skill     | What it gives                  | Levels 0-5              | Vanilla      |
|-----------|--------------------------------|-------------------------|--------------|
| Piloting  | evasion, manning the cockpit   | 5, 7, 9, 11, 13, 15 %   | 5, 7, 10 %   |
| Engines   | evasion, manning the engines   | 5, 7, 9, 11, 13, 15 %   | 5, 7, 10 %   |
| Shields   | faster shield recharge         | 10, 16, 22, 28, 34, 40 % | 10, 20, 30 % |
| Weapons   | faster weapon charging         | 10, 13, 16, 19, 22, 25 % | 10, 15, 20 % |
| Repairs   | faster repairs                 | 0, 8, 16, 24, 32, 40 %  | 0, 10, 20 %  |
| Combat    | more damage in a fight         | 0, 8, 16, 24, 32, 40 %  | 0, 10, 20 %  |

The manning icon on a system is white with no skill, green at levels 1-4,
and yellow when fully learnt. The skills panel's bars fill up green, with a
white mark between each level, and go all yellow at the top.

## Learning

A skill's progress is saved from 0 to 1, as in vanilla, and each level is a
fifth of it. Vanilla takes `actionsPerLevel` actions (such as 15 dodges for
piloting) for each of its two colour changes; XFTL takes `TIME_FACTOR` (2)
times as many in all, so each of its five levels takes 4/5 of
`actionsPerLevel`. Older saves keep their progress: vanilla's green (half
way) is level 2, and yellow is level 5.

Events' `<crew>` skills (`all_skills="1"`, `pilot="2"` and so on) are still
vanilla's: 1 is half way, and 2 fully learnt.

## Crew for hire

A store's crew have more of their skills learnt the further into the game it
is: every skill is at about level 3 by the end (each a level more or not, at
random), on top of which the more dangerous sectors' stores (see
[stores.md](stores.md)) have some skills two levels further on, and some fully
learnt (`StoreData.trainCrew`).

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
| Drones    | faster drones and hacking, manning them | 10, 13, 16, 19, 22, 25 % | (none) |
| Life Support | faster mind control and oxygen, manning them | 10, 13, 16, 19, 22, 25 % | (none) |

**Manning the drones** (an XFTL extension: vanilla's drones system has no
console, and vanilla has no Drones skill): while a drone's out, someone at
the drones system's console (put at the room's edge, as for any mannable
system without one in its layout) speeds its drones up by their Drones
skill, as much as the Weapons skill speeds up a weapons system's weapons:
combat drones' weapons charge, defence drones reload and shield drones
recharge 10 to 25% faster, and a drone that's shot down can go out again as
much sooner (`Drones.manningSpeed`, `Drones.SKILL_BONUSES`). Each shot a
combat or defence drone fires trains the skill, as a weapon's does the
Weapons skill. The crew's AI mans it after the weapons (the enemies' too),
and the system's tooltip shows the bonus.

**Manning the hacking** (an XFTL extension, by the Drones skill, as its
probe's a drone): someone at the hacking system's console makes its pulse
recharge 10 to 25% sooner. Each pulse trains the Drones skill as much as
eight drone shots (`Manning.HACKING_PULSE_ACTIONS`).

**The Life Support skill** (an XFTL extension, `Skill.LIFE_SUPPORT`, see
`Manning.kt`) is for manning the mind control and oxygen systems, which
vanilla's crew can't: someone at the mind control's console makes it
recharge 10 to 25% sooner, and at the oxygen's makes it refill the air as
much faster (`Manning.speed`). Each crewmember mind controlled trains it,
and so does every 15 seconds at the oxygen's console while it's refilling
any room's air (`Manning.OXYGEN_SECONDS_PER_ACTION`). It takes 10 actions
for each of vanilla's colour changes, so 8 for each of XFTL's levels, as
these come far less often than a shot or a dodge. The cloaking, the
backup battery and the teleporter still can't be manned.

The mind control's and the hacking's cooldowns run on the
ion timer, faster for a manned system (`AbstractSystem.cooldownSpeed`),
and a system on its cooldown (its timer running without ion damage) can
still be manned, so the bonus does something; real ion damage still sends
its crew off, as in vanilla. The consoles go at a room's edge, as the
drones', and `tools/wide_passages.py` keeps a wall free for them. The
crew's AI mans the hacking and the oxygen with the drones, and the mind
control with the doors (`ManningTask`).

**Manning the Artillery Beam** (an XFTL extension too): someone at the
artillery's console speeds its charge up by half what their Weapons skill
does for a weapons system, 5 to 12.5% (`Artillery.manningBonus`,
`Artillery.MANNING_SHARE`), and each shot it fires trains the Weapons
skill. The crew's AI mans it as it does the weapons (the enemies' too, as
the Rebel flagship's).

The Drones skill's icon is vanilla's drones icon from the top bar, and the
Life Support skill's a medical monitor with a heartbeat across it, both
made by
`tools/skill_icons.py` (see [art](../tools/art.md)). They're the last
skills in `Skill` (the game records keep each crewmember's levels in that
order, so older records' stay where they were, and their crew have no
Drones or Life Support skill), but the skills panels show them after the Weapons
skill (`Skill.DISPLAY_ORDER`): the crew box's column has eight rows (194
pixels tall rather than vanilla's 146), and the statistics screen's crew
tooltip four rows of two. The crew's info panel (Crew Skills, in the ship
window and the stores) keeps vanilla's six in vanilla's three rows, and
has the Drones and Life Support skills in a fourth at the bottom
(`InfoPanel.CREW_PANEL_ORDER`), each row as tall as its longer
description needs (vanilla's 66 pixels for two lines, 15 more for each
line past that).

The manning icon on a system is white with no skill, green at levels 1-4,
and yellow when fully learnt. The skills panel's bars fill up green, with a
white mark between each level, and go all yellow at the top.

## Learning

A skill's progress is saved from 0 to 1, as in vanilla, and each level is a
fifth of it. Vanilla takes `actionsPerLevel` actions (such as 15 dodges for
piloting) for each of its two colour changes; XFTL takes `TIME_FACTOR` (2)
times as many in all, so each of its five levels takes 4/5 of
`actionsPerLevel`. Older saves keep their progress: vanilla's green (half
way) is level 2, and yellow is level 5; a crewmember saved before the
Drones skill hasn't learnt it.

The Drones skill takes 80 shots for each of vanilla's colour changes, so
64 for each of XFTL's levels: more than the Weapons skill's 65 (52), as a
defence drone fires several times a volley, far more often than most
weapons.

Events' `<crew>` skills (`all_skills="1"`, `pilot="2"` and so on) are still
vanilla's: 1 is half way, and 2 fully learnt. `all_skills` takes in the
Drones and Life Support skills too, and a `drones="1"` or `life="1"`
attribute sets one alone.

## Crew for hire

A store's crew have more of their skills learnt the further into the game it
is: every skill is at about level 3 by the end (each a level more or not, at
random), on top of which the more dangerous sectors' stores (see
[stores.md](stores.md)) have some skills two levels further on, and some fully
learnt (`StoreData.trainCrew`).

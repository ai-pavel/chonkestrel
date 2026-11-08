# Slipstream

The part of the [Slipstream Mod Manager](https://github.com/Vhati/Slipstream-Mod-Manager)
that XFTL uses to apply Slipstream mods (`.ftl` and `.zip` mods, their
`.xml.append` files and XML patches) as it loads the game's files: the XML
patcher and parser, and the mod metadata classes it needs (see
`xyz.znix.xftl.modding.SlipstreamPatcher`, which drives them).

Slipstream is by Vhati, and is under the GNU General Public License,
version 2, as XFTL is (see [../LICENCE.txt](../LICENCE.txt)). This copy is
from [blizzarchon's fork](https://github.com/blizzarchon/Slipstream-Mod-Manager),
at the commit in [version.txt](version.txt), with only what XFTL needs
(and a Gradle build of its own), added to XFTL by Campbell Suter.

.PHONY: art run test debug rich randomize window icon release

# `ftl.dat`'s location: XFTL_DATAFILE if it's set, or else a copy (or a
# link) in this directory, which git ignores
DATAFILE ?= $(or $(XFTL_DATAFILE),$(CURDIR)/ftl.dat)

# The window size for `make window` (by default the window is 1920x1080)
WIDTH = 1920
HEIGHT = 1020

# Where to keep the saved game instead of the usual one,
# to play a separate game without touching it
SAVE_DIR = /tmp/chonkestrel

# Runner shortcut
PARAM1 = XFTL_DATAFILE=$(DATAFILE)
PARAM2 = XFTL_SAVE_DIR=$(if $(SAVE_DIR),$(abspath $(SAVE_DIR)))
PARAMS = $(PARAM1) $(PARAM2)
RUNNER = $(PARAMS) ./gradlew run

# The mods the game run from the repo uses, in its saves directory (cache/
# on macOS): if there's no list yet, every target that runs the game makes
# one with the Chonkestrel mod. A relative path in it is relative to
# cache/mods. See README.md.
MOD_ORDER = cache/mods/order.txt
$(MOD_ORDER):
	mkdir -p $(@D)
	echo ../../mods/chonkestrel > $@

# The mod's generated art (see BUILD.md and doc/tools/art.md), which isn't
# kept in git: made from ftl.dat by the scripts in tools/, in this order (it
# decides the order of their blocks in the mod's autoBlueprints.xml.append).
# Every target that runs the game makes it first, if it's missing or a
# script's changed since; `make art` makes it again regardless.
ART = mods/chonkestrel/img/.generated
ART_TOOLS = ship_variants hybrid_ships boss_ships weapon_sizes system_buttons drone_sizes chonkestrel_art
$(ART): $(ART_TOOLS:%=tools/%.py)
	$(MAKE) art

art:
	$(foreach tool,$(ART_TOOLS),python3 tools/$(tool).py $(DATAFILE) &&) true
	touch $(ART)

randomize debug rich run window icon: $(MOD_ORDER) $(ART)

randomize:
	XFTL_DEBUG=1 XFTL_RANDOMIZE=1 $(RUNNER)

debug:
	XFTL_DEBUG=1 $(RUNNER)

# Debug mode with every system installed and the debug loadout, but the
# systems and reactor at the ship's starting levels
rich:
	XFTL_DEBUG=2 $(RUNNER)

run:
	$(RUNNER)

# Run the tests (see doc/tools/tests.md), or only some of them:
# make test TESTS='*CrewTest'. The totals are in build/test-results/test/.
TESTS =
test:
	$(PARAM1) ./gradlew test $(if $(TESTS),--tests '$(TESTS)')

# Play in a window of a fixed size: make window WIDTH=1600 HEIGHT=1000
window:
	XFTL_WINDOW_SIZE=$(WIDTH)x$(HEIGHT) XFTL_DEBUG=1 $(RUNNER)

# Render the Chonkestrel as it looks in the hangar, and make the game's
# icons from it (tools/release/icon/, and the window icon). See doc/tools/release.md.
ICON_RENDER = $(CURDIR)/tools/release/chonkestrel-hangar.png
icon:
	XFTL_ICON=$(ICON_RENDER) XFTL_WINDOW_SIZE=900x800 $(RUNNER)
	python3 tools/release/make_icon.py $(ICON_RENDER)

# The release's version, as players see it (the build number the OSes see
# is the date, see tools/release/package.py). It's set in gradle.properties, as
# the game shows it too.
VERSION = $(shell sed -n 's/^xftlVersion=//p' gradle.properties)

# Package the game for players, with its own Java: a .dmg for macOS (made on
# a Mac), a .zip for Windows and a .tar.gz for Linux, in dist/release/.
# See doc/tools/release.md.
# The app's icons, which aren't kept in git either: `make icon` makes them
ICONS = tools/release/icon/chonkestrel.icns tools/release/icon/chonkestrel.ico
$(ICONS):
	$(MAKE) icon

release: $(ART) $(ICONS)
	./gradlew fatJar -PxftlVersion=$(VERSION)
	python3 tools/release/package.py $(VERSION)

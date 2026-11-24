#!/usr/bin/env python3
"""
Run the art scripts one after the other, for `make art` (see the Makefile
and BUILD.md), showing how far it's got: a progress bar, the command
running, and the last line it printed (on a terminal, the bar on one line and the
command under it). Their whole output goes in a log,
and if one fails, the end of the log is shown and it stops there.

    python3 tools/make_art.py ftl.dat cache/art.log ship_variants hybrid_ships ...

Each script is run with the same Python as this one, as tools/<name>.py
with ftl.dat. On a terminal, the two progress lines are redrawn in place;
otherwise (as in a CI log) each command gets a line of its own.
"""
import os
import shlex
import shutil
import subprocess
import sys
import time
from pathlib import Path

# How wide the bar is, and how many of the log's last lines a failure shows
BAR_WIDTH = 30
FAILURE_LINES = 30


class Progress:
    """
    The progress: on a terminal, two lines redrawn in place, the bar (and
    how many are done) and under it the command running and the last line
    it printed; otherwise a line for each command.
    """

    def __init__(self, total):
        self.total = total
        self.tty = sys.stdout.isatty()
        self.drawn = False

    def show(self, done, command, last=None):
        filled = BAR_WIDTH * done // self.total
        bar = "[%s] %d/%d" % ("#" * filled + "-" * (BAR_WIDTH - filled), done, self.total)
        if not self.tty:
            if last is None:
                print("%s %s" % (bar, command), flush=True)
            return
        # (each cut to the terminal's width, so redrawing them in place works)
        width = shutil.get_terminal_size((100, 20)).columns - 1
        line = command + ("  > " + last if last else "")
        up = "\033[1A" if self.drawn else ""
        print("\r%s\033[K%s\n\033[K%s" % (up, bar[:width], line[:width]), end="", flush=True)
        self.drawn = True

    def end(self):
        if self.tty and self.drawn:
            print()


def main():
    if len(sys.argv) < 4:
        sys.exit(__doc__)
    datafile, log_path, tools = sys.argv[1], Path(sys.argv[2]), sys.argv[3:]
    log_path.parent.mkdir(parents=True, exist_ok=True)
    progress = Progress(len(tools))
    started = time.monotonic()

    with log_path.open("w") as log:
        for i, tool in enumerate(tools):
            args = [sys.executable, "tools/%s.py" % tool, datafile]
            command = " ".join(shlex.quote(a) for a in ["python3"] + args[1:])
            log.write("$ %s\n" % command)
            log.flush()
            progress.show(i, command)
            # (unbuffered, so its lines come as it prints them)
            env = dict(os.environ, PYTHONUNBUFFERED="1")
            process = subprocess.Popen(args, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env=env)
            last = None
            for line in process.stdout:
                log.write(line)
                line = line.strip()
                # (Pillow's deprecation warnings aren't news)
                if line and "Warning" not in line and not line.startswith(("Image.", "block =", "return ")):
                    last = line
                progress.show(i, command, last)
            if process.wait() != 0:
                progress.end()
                print("%s failed (exit code %d); the end of %s:" % (command, process.returncode, log_path))
                log.flush()
                lines = log_path.read_text().splitlines()
                print("\n".join(lines[-FAILURE_LINES:]))
                sys.exit(process.returncode)

    finished = "Made the art: %d scripts in %.0f seconds (the output's in %s)" % (
        len(tools), time.monotonic() - started, log_path)
    progress.show(len(tools), "done")
    progress.end()
    print(finished)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Fail if a UI source file breaks a package boundary.

Rules keep the UI layers pointing one way:

reverse-import      A file under a ui/theme or ui/component directory must
                    not import me.bmax.apatch.ui.screen.*, so the token and
                    component layers stay usable without pulling in a screen.

hardcoded-color     ui/theme is the only layer allowed a colour literal (the
                    palette data and fallbacks live there). Everywhere else a
                    colour must come from the active scheme, apart from the
                    named palette sources pinned in the allowlist.

core-depends-on-app A :core build script must never depend on :app, so the
                    core modules stay a one-way dependency leaf.

The UI source trees of the app and of every :core module are scanned, so the
rules hold after the module split. Exceptions live in a shrink-only allowlist
so new code cannot opt out.
"""
import argparse
import os
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
UI_PREFIX = "app/src/main/java/me/bmax/apatch/ui"
CORE_DIR = "core"

REVERSE_IMPORT = re.compile(r"^\s*import\s+me\.bmax\.apatch\.ui\.screen(?:\.|$)", re.M)
HARDCODED_COLOR = re.compile(r"\bColor\(0x[0-9A-Fa-f]+\)")
CORE_TO_APP = re.compile(r"""project\(\s*["']:app["']\s*\)""")

SKIP_DIRS = {
    "build",
    ".git",
    ".gradle",
    ".idea",
    ".kotlin",
    ".cxx",
    "node_modules",
    "__pycache__",
    ".research",
    "KernelPatch",
    "target",
}


def load_allowlist(path):
    """Read `rule path` lines; blanks and # comments are ignored."""
    entries = set()
    if path and path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split(None, 1)
            if len(parts) == 2:
                entries.add((parts[0], parts[1].replace("\\", "/")))
    return entries


def iter_kt_files(ui_dir):
    for dirpath, dirnames, filenames in os.walk(ui_dir):
        dirnames[:] = sorted(name for name in dirnames if name not in SKIP_DIRS)
        for name in sorted(filenames):
            if Path(name).suffix == ".kt":
                yield Path(dirpath) / name


def ui_roots(root):
    """Every `<module>/src/main/java/me/bmax/apatch/ui` tree: app + core modules."""
    roots = []
    app_ui = root / "app/src/main/java/me/bmax/apatch/ui"
    if app_ui.exists():
        roots.append(app_ui)
    core = root / CORE_DIR
    if core.exists():
        roots.extend(
            path
            for path in sorted(core.glob("*/src/main/java/me/bmax/apatch/ui"))
            if path.exists()
        )
    return roots


def find_violations(root, allowlist=None):
    """Return a sorted list of (rule, relative_path, detail)."""
    allowlist = allowlist or set()
    violations = []
    for ui_dir in ui_roots(root):
        for path in iter_kt_files(ui_dir):
            relative = path.relative_to(root).as_posix()
            parts = path.relative_to(ui_dir).parts
            top = parts[0] if parts else ""
            in_theme = top == "theme"
            in_component = top == "component"
            text = path.read_text(encoding="utf-8", errors="replace")
            if (
                (in_theme or in_component)
                and REVERSE_IMPORT.search(text)
                and ("reverse-import", relative) not in allowlist
            ):
                violations.append(("reverse-import", relative, "imports ui.screen.*"))
            if (
                not in_theme
                and HARDCODED_COLOR.search(text)
                and ("hardcoded-color", relative) not in allowlist
            ):
                violations.append(("hardcoded-color", relative, "hardcoded Color(0x...)"))
    core = root / CORE_DIR
    if core.exists():
        for build_file in sorted(core.glob("*/build.gradle.kts")):
            relative = build_file.relative_to(root).as_posix()
            if (
                CORE_TO_APP.search(build_file.read_text(encoding="utf-8", errors="replace"))
                and ("core-depends-on-app", relative) not in allowlist
            ):
                violations.append(
                    ("core-depends-on-app", relative, "core module depends on :app")
                )
    return sorted(violations)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument(
        "--allowlist",
        type=Path,
        default=ROOT / "scripts" / "package-boundary-allowlist.txt",
    )
    args = parser.parse_args(argv)

    allowlist = load_allowlist(args.allowlist)
    violations = find_violations(args.root, allowlist)
    if violations:
        for rule, relative, detail in violations:
            print(f"{relative}: {rule} ({detail})")
        print(f"\n{len(violations)} package boundary violation(s).")
        return 1
    print("OK: no package boundary violations.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

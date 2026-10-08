#!/usr/bin/env python3
"""Fail if any source file exceeds LINE_LIMIT lines.

Short files keep refactors cheap: a file close to the limit is a signal to
split it by semantic ownership before adding more. Existing offenders are
pinned in an allowlist that may only shrink, so new code cannot opt out.

Mirrors the guard used by .research/Duck-Detector-Refactoring.
"""
import argparse
import os
from pathlib import Path
import sys

LINE_LIMIT = 400
EXTENSIONS = {".kt", ".kts", ".gradle", ".py", ".sh"}
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
ROOT = Path(__file__).resolve().parents[1]


def count_lines(path):
    with path.open("rb") as handle:
        return sum(1 for _ in handle)


def load_allowlist(path):
    entries = set()
    if path and path.exists():
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            entries.add(line.replace("\\", "/"))
    return entries


def iter_source_files(root):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = sorted(name for name in dirnames if name not in SKIP_DIRS)
        for name in sorted(filenames):
            if Path(name).suffix in EXTENSIONS:
                yield Path(dirpath) / name


def find_violations(root, limit=LINE_LIMIT, allowlist=None):
    allowlist = allowlist or set()
    violations = []
    for path in iter_source_files(root):
        relative = path.relative_to(root).as_posix()
        if relative in allowlist:
            continue
        lines = count_lines(path)
        if lines > limit:
            violations.append((relative, lines))
    return violations


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument("--limit", type=int, default=LINE_LIMIT)
    parser.add_argument(
        "--allowlist",
        type=Path,
        default=ROOT / "scripts" / "source-length-allowlist.txt",
    )
    args = parser.parse_args(argv)

    allowlist = load_allowlist(args.allowlist)
    violations = find_violations(args.root, args.limit, allowlist)
    if violations:
        for relative, lines in violations:
            print(f"{relative}: {lines} lines (limit {args.limit})")
        print(f"\n{len(violations)} file(s) exceed {args.limit} lines.")
        return 1
    print(f"OK: no source file exceeds {args.limit} lines.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

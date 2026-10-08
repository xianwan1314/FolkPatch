#!/usr/bin/env python3
"""Self-test for check-source-file-length.py.

Run with `python3 scripts/test-source-file-length.py`. Uses only the standard
library and a temporary tree, so it never touches the real repository.
"""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

HERE = Path(__file__).resolve().parent


def load_checker():
    spec = importlib.util.spec_from_file_location(
        "check_source_file_length", HERE / "check-source-file-length.py"
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


checker = load_checker()


class CheckSourceFileLengthTest(unittest.TestCase):
    def make_tree(self, files):
        root = Path(tempfile.mkdtemp())
        for relative, lines in files.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("\n".join(f"line{i}" for i in range(lines)) + "\n")
        return root

    def test_under_limit_passes(self):
        root = self.make_tree({"a.kt": 3, "b/c.py": 5})
        self.assertEqual(checker.find_violations(root, limit=10), [])

    def test_over_limit_is_reported(self):
        root = self.make_tree({"a.kt": 11})
        self.assertEqual(checker.find_violations(root, limit=10), [("a.kt", 11)])

    def test_allowlist_skips_existing_offender(self):
        root = self.make_tree({"a.kt": 11, "b.kt": 12})
        self.assertEqual(
            checker.find_violations(root, limit=10, allowlist={"a.kt"}),
            [("b.kt", 12)],
        )

    def test_allowlist_only_matches_exact_path(self):
        root = self.make_tree({"nested/a.kt": 11})
        self.assertEqual(
            checker.find_violations(root, limit=10, allowlist={"a.kt"}),
            [("nested/a.kt", 11)],
        )

    def test_skipped_and_other_extensions_ignored(self):
        root = self.make_tree(
            {
                "build/generated.kt": 99,
                ".research/notes.kt": 99,
                "readme.md": 99,
            }
        )
        self.assertEqual(checker.find_violations(root, limit=10), [])


if __name__ == "__main__":
    sys.exit(0 if unittest.main(exit=False).result.wasSuccessful() else 1)

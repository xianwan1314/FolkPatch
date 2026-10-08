#!/usr/bin/env python3
"""Self-test for check-package-boundaries.py.

Run with `python3 scripts/test-package-boundaries.py`. Uses only the standard
library and a temporary tree, so it never touches the real repository.
"""
import importlib.util
from pathlib import Path
import sys
import tempfile
import unittest

HERE = Path(__file__).resolve().parent
UI = "app/src/main/java/me/bmax/apatch/ui"


def load_checker():
    spec = importlib.util.spec_from_file_location(
        "check_package_boundaries", HERE / "check-package-boundaries.py"
    )
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


checker = load_checker()


class CheckPackageBoundariesTest(unittest.TestCase):
    def make_tree(self, files):
        root = Path(tempfile.mkdtemp())
        for relative, body in files.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(body)
        return root

    def test_component_importing_screen_is_reported(self):
        root = self.make_tree(
            {
                f"{UI}/component/Foo.kt": "package me.bmax.apatch.ui.component\n"
                "import me.bmax.apatch.ui.screen.Bar\n"
            }
        )
        self.assertEqual(
            checker.find_violations(root),
            [("reverse-import", f"{UI}/component/Foo.kt", "imports ui.screen.*")],
        )

    def test_theme_importing_screen_is_reported(self):
        root = self.make_tree(
            {
                f"{UI}/theme/T.kt": "package me.bmax.apatch.ui.theme\n"
                "import me.bmax.apatch.ui.screen.Bar\n"
            }
        )
        self.assertEqual(len(checker.find_violations(root)), 1)

    def test_screen_importing_screen_is_allowed(self):
        root = self.make_tree(
            {
                f"{UI}/screen/S.kt": "package me.bmax.apatch.ui.screen\n"
                "import me.bmax.apatch.ui.screen.Bar\n"
            }
        )
        self.assertEqual(checker.find_violations(root), [])

    def test_hardcoded_color_outside_theme_is_reported(self):
        root = self.make_tree(
            {
                f"{UI}/component/C.kt": "package me.bmax.apatch.ui.component\n"
                "val c = Color(0xFF112233)\n"
            }
        )
        self.assertEqual(len(checker.find_violations(root)), 1)

    def test_hardcoded_color_inside_theme_is_allowed(self):
        root = self.make_tree(
            {
                f"{UI}/theme/tokens/P.kt": "val c = Color(0xFF112233)\n"
            }
        )
        self.assertEqual(checker.find_violations(root), [])

    def test_allowlist_skips_a_known_exception(self):
        root = self.make_tree(
            {
                f"{UI}/component/C.kt": "val c = Color(0xFF112233)\n",
                f"{UI}/component/D.kt": "val c = Color(0xFF445566)\n",
            }
        )
        allow = {("hardcoded-color", f"{UI}/component/C.kt")}
        self.assertEqual(
            checker.find_violations(root, allowlist=allow),
            [
                (
                    "hardcoded-color",
                    f"{UI}/component/D.kt",
                    "hardcoded Color(0x...)",
                )
            ],
        )

    def test_core_component_importing_screen_is_reported(self):
        core_ui = "core/ui/src/main/java/me/bmax/apatch/ui"
        root = self.make_tree(
            {
                f"{core_ui}/component/Foo.kt": "package me.bmax.apatch.ui.component\n"
                "import me.bmax.apatch.ui.screen.Bar\n"
            }
        )
        self.assertEqual(
            checker.find_violations(root),
            [("reverse-import", f"{core_ui}/component/Foo.kt", "imports ui.screen.*")],
        )

    def test_core_module_depending_on_app_is_reported(self):
        root = self.make_tree(
            {
                "core/ui/build.gradle.kts": "dependencies {\n"
                '    implementation(project(":app"))\n}\n'
            }
        )
        self.assertEqual(
            checker.find_violations(root),
            [
                (
                    "core-depends-on-app",
                    "core/ui/build.gradle.kts",
                    "core module depends on :app",
                )
            ],
        )

    def test_core_module_without_app_dependency_is_allowed(self):
        root = self.make_tree(
            {
                "core/ui/build.gradle.kts": "dependencies {\n"
                '    implementation(project(":core:designsystem"))\n}\n'
            }
        )
        self.assertEqual(checker.find_violations(root), [])

    def test_non_kotlin_files_are_ignored(self):
        root = self.make_tree({f"{UI}/component/notes.txt": "Color(0xFF112233)\n"})
        self.assertEqual(checker.find_violations(root), [])


if __name__ == "__main__":
    sys.exit(0 if unittest.main(exit=False).result.wasSuccessful() else 1)

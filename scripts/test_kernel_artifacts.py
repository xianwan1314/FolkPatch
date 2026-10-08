#!/usr/bin/env python3
"""Test the real artifact tasks in an isolated Gradle build, without Android plugins."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]


def run(build, artifacts, success=True):
    result = subprocess.run(
        [str(ROOT / "gradlew"), "-p", str(build), "downloadKpimg", "downloadKptools",
         "--configuration-cache", "--offline", "--console=plain",
         f"-PkernelPatchArtifacts={artifacts}"],
        cwd=ROOT, capture_output=True, text=True, timeout=120,
    )
    output = result.stdout + result.stderr
    if (result.returncode == 0) != success:
        raise RuntimeError(output)
    return output


def main():
    script = (ROOT / "app/build.gradle.kts").read_text()
    start = script.index("fun registerDownloadTask(")
    end = script.index("/** Download with connect/read timeouts", start)
    with tempfile.TemporaryDirectory(prefix="folkpatch-artifacts-") as directory:
        build = Path(directory)
        artifacts = build / "artifacts"
        artifacts.mkdir()
        (build / "settings.gradle.kts").write_text('rootProject.name = "artifact-regression"\n')
        (build / "build.gradle.kts").write_text(
            "import java.io.File\nimport java.net.URI\n" + script[start:end] + '''
registerDownloadTask("downloadKpimg", "http://127.0.0.1:1/kpimg", "$rootDir/out/kpimg", project, "test", "kpimg-android")
registerDownloadTask("downloadKptools", "http://127.0.0.1:1/kptools", "$rootDir/out/kptools", project, "test", "kptools-android")
'''
        )
        for name in ("kpimg-android", "kptools-android"):
            (artifacts / name).write_bytes(name.encode())
        first = run(build, artifacts)
        assert "Configuration cache entry stored" in first, first
        second = run(build, artifacts)
        assert "Reusing configuration cache" in second, second
        for name, source in (("kpimg", "kpimg-android"), ("kptools", "kptools-android")):
            assert (build / "out" / name).read_bytes() == (artifacts / source).read_bytes()
            assert (build / "out" / (name + ".version")).read_text() == "local"
        # A reused cache must still copy updated inputs and reject bad artifacts.
        (artifacts / "kpimg-android").write_bytes(b"updated")
        run(build, artifacts)
        assert (build / "out/kpimg").read_bytes() == b"updated"
        (artifacts / "kpimg-android").write_bytes(b"")
        assert "Missing local KernelPatch artifact" in run(build, artifacts, success=False)
        (artifacts / "kpimg-android").unlink()
        assert "Missing local KernelPatch artifact" in run(build, artifacts, success=False)
        print("PASS: artifact tasks reuse the configuration cache, copy updated binaries, and reject missing/empty inputs")


if __name__ == "__main__":
    main()

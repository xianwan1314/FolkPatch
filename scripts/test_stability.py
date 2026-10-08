#!/usr/bin/env python3
"""Exercise the actual JNI timeout helper with blocking and counted worker calls.

Requires Java and the Gradle wrapper compiler cache (./gradlew --version).
Use --compiler-lib for another compiler installation and --app-data to test a
different AppData.kt, or --baseline-ref to load it from Git history.
--expect-regression accepts only the extra-worker assertion failure.
"""
import argparse
import os
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
APP_DATA = ROOT / "app/src/main/java/me/bmax/apatch/util/AppData.kt"


def compiler_lib(explicit):
    if explicit:
        lib = explicit
    else:
        properties = (ROOT / "gradle/wrapper/gradle-wrapper.properties").read_text()
        match = re.search(r"gradle-([\d.]+)-(?:bin|all)\.zip", properties)
        if not match:
            raise ValueError("Cannot determine the Gradle version from gradle-wrapper.properties")
        version = match.group(1)
        home = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
        candidates = sorted(home.glob(f"wrapper/dists/gradle-{version}-*/*/gradle-{version}/lib"))
        if not candidates:
            raise ValueError("Gradle compiler cache not found; run ./gradlew --version or pass --compiler-lib")
        lib = candidates[0]
    if not list(lib.glob("kotlin-compiler-embeddable-*.jar")):
        raise ValueError(f"Kotlin compiler not found in {lib}")
    return lib


def timeout_helper(path):
    text = path.read_text()
    # Match the method's indentation, not the name of the next class or object.
    match = re.search(
        r"^(?P<indent>[ \t]+)private fun <T> runNativeWithTimeout\b", text, re.M
    )
    if not match:
        raise ValueError(f"{path}: runNativeWithTimeout helper was not found")
    end = re.search(r"^" + re.escape(match["indent"]) + r"}\s*$", text[match.end():], re.M)
    if not end:
        raise ValueError(f"{path}: cannot locate the closing brace of runNativeWithTimeout")
    return text[match.start():match.end() + end.end()]


def kotlin_tests(tmp, app_data, lib, expect_regression):
    stdlibs = list(lib.glob("kotlin-stdlib-*.jar"))
    if not stdlibs:
        raise ValueError(f"Kotlin standard library not found in {lib}")
    stdlib = stdlibs[0]
    body = timeout_helper(app_data)
    call = (
        'runNativeWithTimeout(10, defaultValue, "query-$query", permits[query], block)'
        if "permit: Semaphore" in body else "runNativeWithTimeout(10, defaultValue, block)"
    )
    source = tmp / "Main.kt"
    source.write_text('''import java.util.concurrent.Semaphore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
object Log { fun w(tag: String, msg: String) {} }
object Probe {
    private const val TAG = "test"
    private val nativeCallPermit = Semaphore(1) // For the original PR helper.
    private val permits = arrayOf(Semaphore(1), Semaphore(1))
''' + body + '''
    fun call(query: Int = 0, defaultValue: Int = -1, block: () -> Int): Int = ''' + call + '''
}
fun main() {
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
    val workers = AtomicInteger()
    check(Probe.call { started.countDown(); release.await(); 7 } == -1)
    check(started.await(1, TimeUnit.SECONDS))
    try {
        var wrongFallbacks = 0
        repeat(1000) {
            if (Probe.call(defaultValue = 9) { workers.incrementAndGet(); 123 } != 9) {
                wrongFallbacks++
            }
        }
        check(workers.get() == 0) { "started ${workers.get()} extra workers" }
        check(wrongFallbacks == 0) { "lost cached count on $wrongFallbacks calls" }
        check(Probe.call(query = 1) { 84 } == 84) { "unrelated query was blocked" }
    } finally {
        // Also release the blocked baseline worker when an assertion fails.
        release.countDown()
    }
    var result = -1
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
    while (result == -1 && System.nanoTime() < deadline) {
        Thread.sleep(5)
        result = Probe.call { 42 }
    }
    check(result == 42) { "query did not resume after completion" }
    println("PASS: 1000 retries started zero extra workers, retained the fallback, allowed an independent query, and resumed after completion")
}
''')
    out = tmp / "kotlin-out"
    subprocess.run(
        ["java", "-cp", str(lib / "*"), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
         "-no-stdlib", "-no-reflect", "-classpath", str(stdlib), "-d", str(out), str(source)],
        check=True, timeout=60,
    )
    result = subprocess.run(
        ["java", "-cp", str(out) + os.pathsep + str(stdlib), "MainKt"],
        capture_output=True, text=True, timeout=60,
    )
    if expect_regression:
        match = re.search(r"started ([1-9][0-9]*) extra workers", result.stderr)
        if result.returncode == 0 or not match:
            raise RuntimeError(f"Expected an extra-worker assertion failure, got:\n{result.stdout}{result.stderr}")
        print(f"EXPECTED FAILURE: baseline started {match[1]} extra workers (exit {result.returncode})")
    else:
        if result.returncode:
            raise RuntimeError(f"Regression failed (exit {result.returncode}):\n{result.stdout}{result.stderr}")
        print(result.stdout, end="")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    sources = parser.add_mutually_exclusive_group()
    sources.add_argument("--app-data", type=Path, default=APP_DATA)
    sources.add_argument("--baseline-ref")
    parser.add_argument("--compiler-lib", type=Path)
    parser.add_argument("--expect-regression", action="store_true")
    args = parser.parse_args()
    try:
        lib = compiler_lib(args.compiler_lib)
        with tempfile.TemporaryDirectory(prefix="folkpatch-tests-") as directory:
            tmp = Path(directory)
            app_data = args.app_data
            if args.baseline_ref:
                path = APP_DATA.relative_to(ROOT).as_posix()
                source = subprocess.run(
                    ["git", "show", f"{args.baseline_ref}:{path}"], cwd=ROOT,
                    capture_output=True, text=True, check=True, timeout=30,
                )
                app_data = tmp / "AppData.kt"
                app_data.write_text(source.stdout)
            kotlin_tests(tmp, app_data, lib, args.expect_regression)
    except (ValueError, OSError, subprocess.SubprocessError, RuntimeError) as error:
        parser.exit(1, f"{error}\n")

"""Small, no-install host verifier. No phone, emulator, pip, network or APK replacement.

Run: python -I tools/phone-free.py
Temporary runtime copy is removed afterward; local reports remain under the approved temp root.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import socket
import struct
import subprocess
import sys
import tempfile
import tomllib
import xml.etree.ElementTree as ET
import zipfile

sys.dont_write_bytecode = True
PACKAGE = "io.github.muzammilawan.clipharbor"
CERTIFICATE = "66b04ef4d990c10cd6d5b9126cfd3e3ceb214c390f63eb6c09595ff9aa391658"
INPUTS = ("app", "gradle", "gradlew", "gradlew.bat", "build.gradle.kts", "settings.gradle.kts", "gradle.properties", "contracts")
PERMISSION_BUDGET = {"android.permission." + name for name in (
    "INTERNET", "ACCESS_NETWORK_STATE", "POST_NOTIFICATIONS", "FOREGROUND_SERVICE", "FOREGROUND_SERVICE_DATA_SYNC")}
DEVICE_CHECKS = ["Manual in-place APK installation and stamped startup on Android 10",
    "Picker interaction/screenshots, M4A/WebM playback with sound and saved-file visibility",
    "Second video preset: selected dimensions, picture and sound; earlier failing long sample while visible",
    "Complete post-result Share session logs; sustained background/lock/notification Cancel remain separate checks"]


class CheckFailure(ValueError):
    """Only fixed, locally-authored messages may use this type (never raw tool output)."""


def report_counts(root):
    files = list((root / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
    lint = root / "app/build/reports/lint-results-debug.xml"
    if not files or not lint.is_file(): raise ValueError("Missing test/lint reports")
    counts = dict(total=0, passed=0, skipped=0, failures=0, errors=0, lint_errors=0, lint_warnings=0)
    for path in files:
        suite = ET.parse(path).getroot()
        if suite.tag != "testsuite": raise ValueError("Unexpected unit report")
        for output, attribute in (("total", "tests"), ("skipped", "skipped"), ("failures", "failures"), ("errors", "errors")):
            value = int(suite.attrib[attribute])
            if value < 0: raise ValueError("Invalid unit count")
            counts[output] += value
        if sum(int(suite.attrib[name]) for name in ("skipped", "failures", "errors")) > int(suite.attrib["tests"]):
            raise CheckFailure("Invalid unit report counts")
    counts["passed"] = counts["total"] - counts["skipped"] - counts["failures"] - counts["errors"]
    issues = ET.parse(lint).getroot()
    if issues.tag != "issues": raise CheckFailure("Unexpected lint document")
    for issue in issues.findall("issue"):
        if issue.get("severity") not in ("Error", "Fatal", "Warning", "Information", "Informational", "Ignore"):
            raise CheckFailure("Unknown lint severity; review local lint report")
        if issue.get("severity") in ("Error", "Fatal"): counts["lint_errors"] += 1
        if issue.get("severity") == "Warning": counts["lint_warnings"] += 1
    if counts["passed"] <= 0 or counts["failures"] or counts["errors"] or counts["lint_errors"]:
        raise CheckFailure("Unit tests or lint are not green; inspect app/build/reports/tests and lint-results-debug.xml")
    return counts


def build_config(data):
    try:
        return _build_config(data)
    except (IndexError, struct.error, ValueError):
        raise CheckFailure("Malformed APK DEX metadata; use an intact stamped tester") from None


def _build_config(data):
    # Decode only our own BuildConfig static metadata, never third-party method bodies.
    if not data.startswith(b"dex\n") or len(data) < 112: raise ValueError("Invalid DEX")
    u32 = lambda off: struct.unpack_from("<I", data, off)[0]
    def uleb(off):
        value = 0
        for shift in range(0, 35, 7):
            if not 0 <= off < len(data): raise ValueError("Truncated DEX integer")
            byte = data[off]; off += 1
            value |= (byte & 127) << shift
            if byte < 128: return value, off
        raise ValueError("Invalid DEX integer")
    def count(off):
        size = u32(off)
        if size > len(data) // 4: raise ValueError("Invalid DEX table")
        return size
    strings = []
    for index in range(count(56)):
        off = u32(u32(60) + 4 * index)
        _, off = uleb(off)
        strings.append(data[off:data.index(b"\0", off)].decode("utf-8", errors="replace"))
    types = [strings[u32(u32(68) + 4 * index)] for index in range(count(64))]
    fields = [struct.unpack_from("<HHI", data, u32(84) + 8 * index) for index in range(count(80))]
    for index in range(count(96)):
        off = u32(100) + 32 * index
        if types[u32(off)] != f"L{PACKAGE.replace('.', '/')}/BuildConfig;": continue
        cur, values_off = u32(off + 24), u32(off + 28)
        sizes = []
        for _ in range(4):
            size, cur = uleb(cur); sizes.append(size)
        if sizes[0] > len(fields): raise ValueError("Invalid static field count")
        names, field_index = [], 0
        for _ in range(sizes[0]):
            delta, cur = uleb(cur); field_index += delta
            _, cur = uleb(cur)
            names.append(strings[fields[field_index][2]])
        size, cur = uleb(values_off)
        if size > len(names): raise ValueError("Invalid static value count")
        values = []
        for _ in range(size):
            header = data[cur]; cur += 1
            kind, arg = header & 31, header >> 5
            if kind in (0x1e, 0x1f): value = arg if kind == 0x1f else None
            elif kind in (0x00, 0x02, 0x03, 0x04, 0x06, 0x10, 0x11, 0x17):
                if cur + arg + 1 > len(data): raise ValueError("Truncated static value")
                value = int.from_bytes(data[cur:cur + arg + 1], "little"); cur += arg + 1
                if kind == 0x17: value = strings[value]
            else: raise ValueError("Unsupported BuildConfig static value")
            values.append(value)
        return dict(zip(names, values))
    return None


def check_copy(apk, built):
    digest = sha256(apk)
    if digest != sha256(built): raise CheckFailure("Built/copied APK mismatch; recopy a verified stamped build")
    raw = (apk.parent / "SHA256SUMS.txt").read_bytes()
    lines = raw.decode("utf-16" if raw.startswith((b'\xff\xfe', b'\xfe\xff')) else "utf-8-sig").splitlines()
    matches = [line.split() for line in lines if len(line.split()) == 2 and line.split()[1] == apk.name]
    if len(matches) != 1 or matches[0][0].lower() != digest: raise CheckFailure("APK checksum entry mismatch; verify dist/SHA256SUMS.txt")
    return digest.upper()


def check_assets(root, archive):
    source = root / "app/src/main/assets"
    expected = {"assets/" + path.relative_to(source).as_posix(): path for path in source.rglob("*") if path.is_file()}
    actual = {name for name in archive.namelist() if name.startswith("assets/") and not name.endswith("/")}
    if not expected or actual != set(expected): raise ValueError("Packaged asset set mismatch")
    for name, path in expected.items():
        if archive.read(name) != path.read_bytes(): raise ValueError("Packaged asset bytes mismatch")
    return len(expected)


def check_badging(text, permissions, min_sdk, target_sdk):
    actual = set(re.findall(r"^uses-permission: name='([^']+)'", text, re.M))
    local = PACKAGE + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
    if permissions - PERMISSION_BUDGET or actual != permissions:
        # AndroidX's pre-existing signature-only local receiver permission is the one merged addition.
        if permissions - PERMISSION_BUDGET or actual != permissions | {local}:
            raise ValueError("APK permission budget/source mismatch")
    required = [f"package: name='{PACKAGE}'", f"minSdkVersion:'{min_sdk}'", f"targetSdkVersion:'{target_sdk}'",
                "application-label:'YTDLP'", "application-debuggable", "native-code: 'arm64-v8a'"]
    if any(value not in text for value in required): raise ValueError("APK identity/SDK/ABI mismatch")
    return sorted(actual)


def sha256(path):
    with path.open("rb") as stream: return hashlib.file_digest(stream, "sha256").hexdigest()


def remove_run_copy(path, expected_hash):
    if path.is_file() and sha256(path) == expected_hash: path.unlink()


def command(args, root, env=None, gradle=False, with_stderr=False):
    result = subprocess.run(args, cwd=root, env=env, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if gradle:
        # Do not persist arbitrary compiler/test/source text or environment/credentials in our report.
        for line in (result.stdout + result.stderr).splitlines():
            if re.fullmatch(r"BUILD (?:SUCCESSFUL|FAILED) in [0-9hms ]+", line): print(line, flush=True)
    if result.returncode:
        if gradle: raise CheckFailure("Offline Gradle failed; run local testDebugUnitTest lintDebug --offline --console=plain for details; no packages installed")
        raise CheckFailure("Local command failed; check existing Git/Python/SDK tools and artifact inputs")
    return result.stdout + result.stderr if with_stderr else result.stdout


def batch(args):
    values = list(map(str, args))
    # Use a raw CreateProcess command line: list2cmdline escaping is not CMD quoting.
    # Reject expansion/control characters even inside quotes. Spaces are supported.
    if any(re.search(r'["%!&|<>^\r\n\x00]', value) for value in values):
        raise CheckFailure("Unsafe Windows batch argument/path; use a path without shell expansion/control characters")
    if os.name != "nt": return values
    return 'cmd.exe /d /v:off /s /c "' + ' '.join('"' + value + '"' for value in values) + '"'


def source_matches(root, stamp):
    if not re.fullmatch(r"[0-9a-f]{7,40}", stamp): raise ValueError("Missing/invalid stamped APK")
    command(["git", "rev-parse", "--verify", stamp + "^{commit}"], root)
    command(["git", "merge-base", "--is-ancestor", stamp, "HEAD"], root)
    if command(["git", "diff", stamp, "--", *INPUTS], root).strip():
        raise CheckFailure("App/build inputs changed since APK stamp; build a new tester first")
    untracked = command(["git", "ls-files", "--others", "--exclude-standard"], root).splitlines()
    if any(path == item or path.startswith(item + "/") for path in untracked for item in INPUTS):
        raise CheckFailure("Untracked app/build inputs; commit and build a new tester first")


def engine_pin(root):
    text = (root / "app/src/main/java/io/github/muzammilawan/clipharbor/engine/YoutubeDlEngine.kt").read_text(encoding="utf-8")
    pin = {key: re.search(r'const val ' + key + r' = "([^"\r\n]+)"', text).group(1)
           for key in ("ASSET", "VERSION", "EJS_VERSION", "SHA256")}
    pin["BYTES"] = int(re.search(r"const val BYTES = ([0-9_]+)L", text).group(1).replace("_", ""))
    if not re.fullmatch(r"engine/yt-dlp-[0-9.]+", pin["ASSET"]) or not re.fullmatch(r"[0-9a-f]{64}", pin["SHA256"]):
        raise ValueError("Invalid bundled engine pin")
    return pin


def artifact(root, apk, certificate, sdk, baseline=None):
    digest = check_copy(apk, root / "app/build/outputs/apk/debug/app-debug.apk")
    pin = engine_pin(root)
    with zipfile.ZipFile(apk) as archive:
        if len(archive.namelist()) != len(set(archive.namelist())): raise ValueError("Duplicate APK entries")
        configs = [build_config(archive.read(name)) for name in archive.namelist() if re.fullmatch(r"classes\d*\.dex", name)]
        configs = [config for config in configs if config is not None]
        if len(configs) != 1: raise ValueError("Missing/ambiguous own BuildConfig")
        config = configs[0]
        if config.get("APPLICATION_ID") != PACKAGE or config.get("BUILD_TYPE") != "debug": raise CheckFailure("Wrong APK build; use the stamped YTDLP debug tester")
        stamp = config.get("GIT_SHA", "")
        source_matches(root, stamp)
        assets = check_assets(root, archive)
        payload = archive.read("assets/" + pin["ASSET"])
        if len(payload) != pin["BYTES"] or hashlib.sha256(payload).hexdigest() != pin["SHA256"]:
            raise ValueError("Unverified packaged engine")
        with zipfile.ZipFile(io.BytesIO(payload)) as engine:
            if f"__version__ = '{pin['VERSION']}'".encode() not in engine.read("yt_dlp/version.py"):
                raise ValueError("Packaged engine version mismatch")
            if f"version = '{pin['EJS_VERSION']}'".encode() not in engine.read("yt_dlp_ejs/_version.py"):
                raise ValueError("Packaged EJS version mismatch")
        native = sorted(name for name in archive.namelist() if name.startswith("lib/"))
        if len(native) != 7 or any(not name.startswith("lib/arm64-v8a/") for name in native):
            raise ValueError("Unexpected native payload/ABI")
        baseline_result = "NOT_REQUESTED"
        if baseline:
            with zipfile.ZipFile(baseline) as old:
                names = [name for name in archive.namelist() if name.startswith(("lib/", "assets/")) or name == "AndroidManifest.xml" or
                         (name.startswith("res/") and ("ytdlp_icon" in name or "ic_launcher" in name))]
                old_names = [name for name in old.namelist() if name.startswith(("lib/", "assets/")) or name == "AndroidManifest.xml" or
                             (name.startswith("res/") and ("ytdlp_icon" in name or "ic_launcher" in name))]
                if set(names) != set(old_names) or any(archive.read(name) != old.read(name) for name in names):
                    raise ValueError("Baseline assets/native/manifest/icon changed; review deliberately")
            baseline_result = {"status": "IDENTICAL", "sha256": sha256(baseline).upper()}
    tools = sorted((path for path in (sdk / "build-tools").iterdir() if re.fullmatch(r"\d+\.\d+\.\d+", path.name)),
                   key=lambda path: tuple(map(int, path.name.split("."))), reverse=True)
    tools = next((path for path in tools if (path / "aapt2.exe").is_file() and (path / "apksigner.bat").is_file()), None)
    if tools is None: raise ValueError("Existing Windows SDK build-tools required; nothing will be installed")
    signed = command(batch([tools / "apksigner.bat", "verify", "--verbose", "--print-certs", apk]), root)
    certificates = re.findall(r"certificate SHA-256 digest: ([0-9a-f]{64})", signed)
    if certificates != [certificate]: raise CheckFailure("APK signing certificate differs from expected owner key; do not install over the previous tester")
    versions = tomllib.loads((root / "gradle/libs.versions.toml").read_text(encoding="utf-8"))["versions"]
    manifest = ET.parse(root / "app/src/main/AndroidManifest.xml").getroot()
    permissions = {item.attrib["{http://schemas.android.com/apk/res/android}name"] for item in manifest.findall("uses-permission")}
    badging = command([str(tools / "aapt2.exe"), "dump", "badging", str(apk)], root)
    permissions = check_badging(badging, permissions, int(versions["minSdk"]), int(versions["targetSdk"]))
    if f"versionCode='{config['VERSION_CODE']}'" not in badging or f"versionName='{config['VERSION_NAME']}'" not in badging:
        raise ValueError("APK version disagrees with own BuildConfig")
    return dict(stamp=stamp, bytes=apk.stat().st_size, sha256=digest, certificate=certificate, permissions=permissions,
                engine=pin["VERSION"], ejs=pin["EJS_VERSION"], assets=assets, native_entries=len(native), baseline=baseline_result)


def offline_selectors(root, pin):
    asset = root / "app/src/main/assets" / pin["ASSET"]
    if sha256(asset) != pin["SHA256"]: raise ValueError("Unverified engine source; refusing execution")
    # No extractor/media processing; the exact bundled zipapp chooses synthetic format dictionaries only.
    def blocked(*args, **kwargs): raise ValueError("Network forbidden in offline engine checks")
    original_connect, original_connection = socket.socket.connect, socket.create_connection
    socket.socket.connect, socket.create_connection = blocked, blocked
    sys.path.insert(0, str(asset))
    try:
        from yt_dlp import YoutubeDL
        from yt_dlp.version import __version__
        if __version__ != pin["VERSION"]: raise ValueError("Offline engine pin mismatch")
        values = {entry.attrib["key"]: entry.text or "" for entry in ET.parse(root / "app/build/reports/quality-selectors.xml").getroot().findall("entry")}
        count = int(values["count"])
        if not 0 < count <= 10000: raise ValueError("Invalid video selector fixture count")
        with YoutubeDL(dict(quiet=True, no_warnings=True, cachedir=False)) as engine:
            for index in range(count):
                prefix = f"case.{index}."
                formats = []
                for row in values[prefix + "formats"].splitlines():
                    ident, ext, video, audio, width, height = row.split("\t")
                    fmt = dict(format_id=ident, ext=ext, vcodec=video, acodec=audio, url="https://example.invalid/synthetic", protocol="https")
                    if width != "NA": fmt["width"] = int(width)
                    if height != "NA": fmt["height"] = int(height)
                    formats.append(fmt)
                engine.sort_formats(dict(formats=formats))
                selected = engine._select_formats(formats, engine.build_format_selector(values[prefix + "selector"]))
                expected = values[prefix + "expected"]
                if [fmt["format_id"] for fmt in selected] != ([expected] if expected else []): raise ValueError("Video selector regression")
                if any(min(fmt["width"], fmt["height"]) > int(values[prefix + "cap"]) or fmt.get("acodec") in (None, "none") for fmt in selected):
                    raise ValueError("Video selector violated cap/audio contract")
            audio_cases = 0
            for ident, ext, codec in (("140", "m4a", "mp4a.40.2"), ("251", "webm", "opus"), ("audio.test-1", "webm", "opus")):
                original = dict(format_id=ident, ext=ext, vcodec="none", acodec=codec)
                other = dict(format_id="other", ext="webm", vcodec="none", acodec="opus")
                muxed = dict(format_id=ident, ext="mp4", vcodec="avc1", acodec="mp4a", width=1280, height=720)
                for formats, expected in (([original, other], [ident]), ([other], []), ([muxed], [])):
                    for fmt in formats: fmt.update(url="https://example.invalid/synthetic", protocol="https")
                    engine.sort_formats(dict(formats=formats))
                    selected = engine._select_formats(formats, engine.build_format_selector(f"ba[format_id='{ident}']"))
                    if [fmt["format_id"] for fmt in selected] != expected: raise ValueError("Exact original audio selector regression")
                    audio_cases += 1
        return dict(video_cases=count, audio_cases=audio_cases, network="socket connections blocked", media_download="NOT_RUN")
    finally:
        sys.path.remove(str(asset))
        socket.socket.connect, socket.create_connection = original_connect, original_connection


def cached_wrapper(root):
    text = (root / "gradle/wrapper/gradle-wrapper.properties").read_text(encoding="utf-8")
    match = re.search(r"/gradle-([0-9.]+)-bin\.zip", text)
    if not match: raise CheckFailure("Unsupported wrapper distribution; no download allowed")
    version = match.group(1)
    home = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
    cached = home / "wrapper/dists" / f"gradle-{version}-bin"
    binaries = sorted(cached.glob(f"*/gradle-{version}/bin/gradle.bat"))
    if not binaries:
        raise CheckFailure("Gradle distribution not already cached; no download allowed")
    # Invoke the already-extracted distribution directly. --offline alone cannot prevent
    # the wrapper from downloading its own distribution when a URL/cache key differs.
    return binaries[0]


def write_report(directory, report):
    (directory / "summary.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    text = ["# Phone-free verification", "", "Status: **" + report["status"] + "**", "", "Only host checks; NOT Android/device verification.", ""]
    for key in ("artifact", "python_tests", "android_tests", "offline_selectors", "build_lines", "runtime_copy"):
        if key in report: text += ["## " + key, "```json", json.dumps(report[key], indent=2), "```", ""]
    if "failure" in report: text += ["Failure: " + report["failure"], ""]
    text += ["## Owner device checks still required", *["- " + item for item in DEVICE_CHECKS], ""]
    (directory / "summary.md").write_text("\n".join(text), encoding="utf-8")


def worker(args):
    root, directory = Path(args.repo).resolve(), Path(args.run_dir).resolve()
    report = dict(status="FAILED_HOST_CHECKS", started=datetime.now(timezone.utc).isoformat(), device_verification="NOT_RUN")
    stage = "preflight/artifact"
    try:
        if os.name != "nt": raise ValueError("This small helper is currently Windows-only")
        sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "missing-sdk")
        gradle = cached_wrapper(root)
        apk = Path(args.apk).resolve() if args.apk else root / "dist/app-debug.apk"
        report["artifact"] = artifact(root, apk, args.certificate, sdk, Path(args.baseline).resolve() if args.baseline else None)
        stage = "helper regression tests"
        env = dict(os.environ, TEMP=str(directory), TMP=str(directory))
        tested = command([sys.executable, "-I", "tools/test_phone_free.py"], root, env, with_stderr=True)
        ran = re.search(r"Ran (\d+) tests? in", tested)
        if not ran or int(ran.group(1)) <= 0: raise ValueError("No helper regression tests ran")
        report["python_tests"] = dict(tests=int(ran.group(1)), status="PASS")
        stage = "fresh Android JVM tests/lint"
        env = dict(os.environ)  # Build-tool scratch stays in its normal temp location, not the summary directory.
        env["GRADLE_OPTS"] = env.get("GRADLE_OPTS", "") + " -Dorg.gradle.daemon=false"
        started = datetime.now().timestamp()
        output = command(batch([gradle, "testDebugUnitTest", "lintDebug", "--offline", "--rerun-tasks", "--console=plain",
                                "-PgitSha=" + report["artifact"]["stamp"]]), root, env, gradle=True)
        report["build_lines"] = [line for line in output.splitlines() if re.fullmatch(r"BUILD SUCCESSFUL in [0-9hms ]+", line)]
        if not report["build_lines"]: raise CheckFailure("No successful Gradle BUILD line; refusing a passing report")
        paths = list((root / "app/build/test-results/testDebugUnitTest").glob("TEST-*.xml")) + [
            root / "app/build/reports/lint-results-debug.xml", root / "app/build/reports/quality-selectors.xml"]
        if any(path.stat().st_mtime < started for path in paths): raise CheckFailure("Stale test/lint/selector reports refused; rerun checks")
        report["android_tests"] = report_counts(root)
        stage = "offline engine selectors"
        report["offline_selectors"] = offline_selectors(root, engine_pin(root))
        stage = "final artifact fence"
        if sha256(apk).upper() != report["artifact"]["sha256"]: raise ValueError("APK changed during verification")
        source_matches(root, report["artifact"]["stamp"])
        report["status"] = "PASS_HOST_CHECKS_DEVICE_REQUIRED"
    except Exception as error:
        # A failed command/parser must not expose arbitrary engine/source/env text in the report.
        detail = str(error) if isinstance(error, CheckFailure) else "Check existing local tools/files and intact stamped APK; raw error text is not retained"
        report["failure"] = stage + " failed (" + type(error).__name__ + "): " + detail + ". No device result is inferred."
    report["finished"] = datetime.now(timezone.utc).isoformat()
    try:
        write_report(directory, report)
    except OSError:
        print("FAILED_HOST_CHECKS: cannot write local reports; check temporary-folder access/free space", flush=True)
        return 1
    print(report["status"], flush=True)
    print("Report: " + str(directory / "summary.md"), flush=True)
    return 0 if report["status"] == "PASS_HOST_CHECKS_DEVICE_REQUIRED" else 1


def finish_run(directory, runtime, digest, code):
    try:
        remove_run_copy(runtime, digest)  # Only our exact copy; leave reports/user changes intact.
        cleanup = "RETAINED_CHANGED_COPY" if runtime.exists() else "REMOVED"
    except OSError:
        cleanup = "RETAINED_CLEANUP_FAILED"
    try:
        report_file = directory / "summary.json"
        report = json.loads(report_file.read_text(encoding="utf-8")) if report_file.is_file() else dict(
            status="FAILED_HOST_CHECKS", device_verification="NOT_RUN", failure="Worker did not produce a complete report")
        report["runtime_copy"] = cleanup
        if code != 0 or cleanup != "REMOVED" or report.get("status") != "PASS_HOST_CHECKS_DEVICE_REQUIRED":
            report["status"] = "FAILED_HOST_CHECKS"
            code = 1
        write_report(directory, report)
    except (OSError, ValueError):
        print("FAILED_HOST_CHECKS: final report could not be written", flush=True)
        code = 1
    print("Temporary runtime copy: " + cleanup, flush=True)
    return code


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", help="Existing stamped APK; default dist/app-debug.apk (never replaced)")
    parser.add_argument("--baseline", help="Optional previously verified APK: compare full assets/native/manifest/icon bytes")
    parser.add_argument("--certificate", default=CERTIFICATE, help="Expected owner signer SHA-256; default documented local debug key")
    parser.add_argument("--repo", default=str(Path(__file__).resolve().parents[1]), help=argparse.SUPPRESS)
    parser.add_argument("--run-dir", help=argparse.SUPPRESS)
    args = parser.parse_args()
    if not re.fullmatch(r"[0-9a-f]{64}", args.certificate): parser.error("Certificate must be a lowercase SHA-256 digest")
    if args.run_dir: return worker(args)
    if os.name != "nt": parser.error("Currently tested/supported on Windows only")
    temporary = Path(os.environ["LOCALAPPDATA"]) / "Temp/opencode"
    if not temporary.is_dir(): parser.error("Approved temporary root is absent; nothing installed")
    directory = Path(tempfile.mkdtemp(prefix="ytdlp-phone-free-", dir=temporary))
    runtime = directory / "runner.py"
    shutil.copyfile(__file__, runtime)
    digest = sha256(runtime)
    argv = [sys.executable, "-I", str(runtime), "--repo", str(Path(args.repo).resolve()), "--run-dir", str(directory), "--certificate", args.certificate]
    if args.apk: argv += ["--apk", str(Path(args.apk).resolve())]
    if args.baseline: argv += ["--baseline", str(Path(args.baseline).resolve())]
    code = 1
    try:
        code = subprocess.run(argv, cwd=args.repo).returncode
    except (OSError, KeyboardInterrupt):
        print("FAILED_HOST_CHECKS: worker could not finish; no passing result inferred", flush=True)
    finally:
        code = finish_run(directory, runtime, digest, code)
    return code


if __name__ == "__main__":
    sys.exit(main())

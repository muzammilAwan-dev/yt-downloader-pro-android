"""Offline stdlib regressions for the small phone-free verifier."""
import hashlib
import importlib.util
import os
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
import unittest
import zipfile
from unittest.mock import patch

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("phone_free", Path(__file__).with_name("phone-free.py"))
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


def synthetic_dex(stamp="abcdef1"):
    # Minimal own BuildConfig metadata only; no executable methods or third-party code.
    strings = ["Lio/github/muzammilawan/clipharbor/BuildConfig;", "Ljava/lang/String;", "GIT_SHA", stamp]
    data = bytearray(200)
    data[:8] = b"dex\n035\0"
    struct.pack_into("<II", data, 56, len(strings), 112)
    struct.pack_into("<II", data, 64, 2, 128)
    struct.pack_into("<II", data, 80, 1, 136)
    struct.pack_into("<II", data, 96, 1, 144)
    struct.pack_into("<II", data, 128, 0, 1)
    struct.pack_into("<HHI", data, 136, 0, 1, 2)
    for index, text in enumerate(strings):
        struct.pack_into("<I", data, 112 + index * 4, len(data))
        raw = text.encode()
        data.extend(bytes([len(raw)]) + raw + b"\0")
    class_data = len(data)
    data.extend(b"\x01\x00\x00\x00\x00\x19")
    values = len(data)
    data.extend(b"\x01\x17\x03")
    struct.pack_into("<I", data, 144, 0)
    struct.pack_into("<II", data, 168, class_data, values)
    return bytes(data)


class PhoneFreeTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def reports(self, failures=0, errors=0, skipped=1, tests=4, severity="Warning"):
        directory = self.root / "app/build/test-results/testDebugUnitTest"
        directory.mkdir(parents=True)
        (directory / "TEST-fixture.xml").write_text(
            f'<testsuite tests="{tests}" failures="{failures}" errors="{errors}" skipped="{skipped}"/>')
        lint = self.root / "app/build/reports/lint-results-debug.xml"
        lint.parent.mkdir(parents=True)
        lint.write_text(f'<issues><issue severity="{severity}"/></issues>')

    def test_report_counts_keep_skips_distinct_from_passes(self):
        self.reports()
        self.assertEqual({"total": 4, "passed": 3, "skipped": 1, "failures": 0, "errors": 0,
                          "lint_errors": 0, "lint_warnings": 1}, verifier.report_counts(self.root))

    def test_failed_tests_are_not_reported_green(self):
        self.reports(failures=1)
        with self.assertRaises(ValueError): verifier.report_counts(self.root)

    def test_all_skipped_is_not_a_passing_suite(self):
        self.reports(skipped=4)
        with self.assertRaises(ValueError): verifier.report_counts(self.root)

    def test_missing_reports_fail_closed(self):
        with self.assertRaises(ValueError): verifier.report_counts(self.root)

    def test_fatal_lint_is_an_error(self):
        self.reports(severity="Fatal")
        with self.assertRaises(ValueError): verifier.report_counts(self.root)

    def test_extracts_actual_own_dex_stamp_not_an_unrelated_string(self):
        dex = synthetic_dex()
        self.assertEqual({"GIT_SHA": "abcdef1"}, verifier.build_config(dex))
        self.assertIsNone(verifier.build_config(dex.replace(b"/BuildConfig;", b"/OtherClassX;")))

    def test_truncated_dex_is_rejected(self):
        with self.assertRaises((ValueError, struct.error)): verifier.build_config(synthetic_dex()[:140])

    def test_checksum_file_must_identify_the_exact_apk(self):
        apk = self.root / "app-debug.apk"
        apk.write_bytes(b"synthetic")
        digest = hashlib.sha256(apk.read_bytes()).hexdigest().upper()
        (self.root / "SHA256SUMS.txt").write_text(digest + "  another.apk\n", encoding="utf-8-sig")
        with self.assertRaises(ValueError): verifier.check_copy(apk, apk)
        (self.root / "SHA256SUMS.txt").write_text(digest + "  app-debug.apk\n", encoding="utf-8-sig")
        self.assertEqual(digest, verifier.check_copy(apk, apk))

    def test_copy_mismatch_is_rejected(self):
        apk = self.root / "app-debug.apk"; apk.write_bytes(b"one")
        built = self.root / "built.apk"; built.write_bytes(b"two")
        with self.assertRaises(ValueError): verifier.check_copy(apk, built)

    def test_checksum_accepts_powershell_utf16_bom(self):
        apk = self.root / "app-debug.apk"
        apk.write_bytes(b"synthetic")
        digest = verifier.sha256(apk).upper()
        (self.root / "SHA256SUMS.txt").write_text(digest + "  app-debug.apk\n", encoding="utf-16")
        self.assertEqual(digest, verifier.check_copy(apk, apk))

    def test_packaged_assets_must_match_source_bytes_and_set(self):
        source = self.root / "app/src/main/assets/licenses"
        source.mkdir(parents=True)
        (source / "notice.txt").write_bytes(b"complete notice")
        apk = self.root / "fixture.apk"
        with zipfile.ZipFile(apk, "w") as z: z.writestr("assets/licenses/notice.txt", b"truncated")
        with zipfile.ZipFile(apk) as z:
            with self.assertRaises(ValueError): verifier.check_assets(self.root, z)
        with zipfile.ZipFile(apk, "w") as z: z.writestr("assets/licenses/notice.txt", b"complete notice")
        with zipfile.ZipFile(apk) as z: self.assertEqual(1, verifier.check_assets(self.root, z))

    def test_permission_and_identity_contracts_fail_on_added_storage_permission(self):
        badging = "\n".join(["package: name='io.github.muzammilawan.clipharbor' versionCode='2' versionName='preview'",
            "minSdkVersion:'24'", "targetSdkVersion:'37'", "application-label:'YTDLP'", "application-debuggable",
            "native-code: 'arm64-v8a'", "uses-permission: name='android.permission.INTERNET'",
            "uses-permission: name='android.permission.WRITE_EXTERNAL_STORAGE'"])
        with self.assertRaises(ValueError): verifier.check_badging(badging, {"android.permission.INTERNET"}, 24, 37)
        badging = badging.replace("\nuses-permission: name='android.permission.WRITE_EXTERNAL_STORAGE'", "")
        self.assertEqual(["android.permission.INTERNET"], verifier.check_badging(badging, {"android.permission.INTERNET"}, 24, 37))

    def test_only_own_guarded_run_copy_can_be_removed(self):
        worker = self.root / "runner.py"
        worker.write_bytes(b"owned")
        digest = verifier.sha256(worker)
        worker.write_bytes(b"user changed it")
        verifier.remove_run_copy(worker, digest)
        self.assertTrue(worker.exists())
        verifier.remove_run_copy(worker, verifier.sha256(worker))
        self.assertFalse(worker.exists())

    def test_invalid_counts_cannot_cancel_out_across_suites(self):
        self.reports(skipped=5, tests=4)
        directory = self.root / "app/build/test-results/testDebugUnitTest"
        (directory / "TEST-other.xml").write_text('<testsuite tests="10" failures="0" errors="0" skipped="0"/>')
        with self.assertRaises(ValueError): verifier.report_counts(self.root)

    def test_wrong_lint_document_is_not_green(self):
        self.reports()
        (self.root / "app/build/reports/lint-results-debug.xml").write_text('<not-lint/>')
        with self.assertRaises(ValueError): verifier.report_counts(self.root)

    def test_malformed_dex_indexes_are_rejected(self):
        data = bytearray(synthetic_dex())
        struct.pack_into('<I', data, 128, 999999)
        with self.assertRaises(ValueError): verifier.build_config(bytes(data))

    def test_batch_rejects_shell_expansion_and_injection(self):
        for argument in ('%PATH%', 'x!y', 'x"y', 'x&y', 'x|y', 'x\ny', 'x^y'):
            with self.subTest(argument=argument):
                with self.assertRaises(ValueError): verifier.batch(['runner.bat', argument])

    @unittest.skipUnless(os.name == 'nt', 'Windows command quoting regression')
    def test_batch_handles_paths_and_arguments_with_spaces(self):
        script = self.root / 'folder with spaces' / 'own runner.bat'
        script.parent.mkdir()
        script.write_text('@echo off\r\necho [%~1]\r\n', encoding='ascii')
        result = subprocess.run(verifier.batch([script, 'value with spaces']), capture_output=True, text=True)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual('[value with spaces]', result.stdout.strip())

    def test_changed_source_or_untracked_app_inputs_refuse_old_apk(self):
        with patch.object(verifier, 'command', side_effect=['', '', 'changed']):
            with self.assertRaises(ValueError): verifier.source_matches(self.root, 'abcdef1')
        with patch.object(verifier, 'command', side_effect=['', '', '', 'app/new-input.kt\n']):
            with self.assertRaises(ValueError): verifier.source_matches(self.root, 'abcdef1')
        with patch.object(verifier, 'command', side_effect=['', '', '', 'tools/phone-free.py\n']):
            verifier.source_matches(self.root, 'abcdef1')

    def test_worker_failure_is_actionable_without_raw_exception_text(self):
        args = type('Args', (), dict(repo=str(self.root), run_dir=str(self.root), apk=None,
                                     certificate=verifier.CERTIFICATE, baseline=None))()
        with patch.object(verifier, 'cached_wrapper', side_effect=verifier.CheckFailure('No cached Gradle; no download allowed')):
            self.assertEqual(1, verifier.worker(args))
        report = verifier.json.loads((self.root / 'summary.json').read_text())
        self.assertEqual('FAILED_HOST_CHECKS', report['status'])
        self.assertEqual('NOT_RUN', report['device_verification'])
        self.assertIn('No cached Gradle', report['failure'])
        with patch.object(verifier, 'cached_wrapper', side_effect=RuntimeError('cookie=private-auth-value')):
            self.assertEqual(1, verifier.worker(args))
        self.assertNotIn('private-auth-value', (self.root / 'summary.md').read_text())

    def test_report_write_failure_never_returns_success(self):
        args = type('Args', (), dict(repo=str(self.root), run_dir=str(self.root), apk=None,
                                     certificate=verifier.CERTIFICATE, baseline=None))()
        with patch.object(verifier, 'cached_wrapper', side_effect=ValueError('blocked')), \
                patch.object(verifier, 'write_report', side_effect=OSError('private filesystem text')):
            self.assertEqual(1, verifier.worker(args))

    def test_finalizer_rejects_missing_report_even_with_zero_worker_exit(self):
        runtime = self.root / 'runner.py'
        runtime.write_bytes(b'owned copy')
        self.assertEqual(1, verifier.finish_run(self.root, runtime, verifier.sha256(runtime), 0))
        self.assertFalse(runtime.exists())
        report = verifier.json.loads((self.root / 'summary.json').read_text())
        self.assertEqual('FAILED_HOST_CHECKS', report['status'])
        self.assertEqual('REMOVED', report['runtime_copy'])

    def test_finalizer_preserves_changed_copy_and_marks_failure(self):
        runtime = self.root / 'runner.py'
        runtime.write_bytes(b'owned copy')
        digest = verifier.sha256(runtime)
        runtime.write_bytes(b'user changed copy')
        verifier.write_report(self.root, dict(status='PASS_HOST_CHECKS_DEVICE_REQUIRED', device_verification='NOT_RUN'))
        self.assertEqual(1, verifier.finish_run(self.root, runtime, digest, 0))
        self.assertEqual(b'user changed copy', runtime.read_bytes())
        report = verifier.json.loads((self.root / 'summary.json').read_text())
        self.assertEqual('FAILED_HOST_CHECKS', report['status'])
        self.assertEqual('RETAINED_CHANGED_COPY', report['runtime_copy'])

    def test_missing_cache_fails_without_invoking_any_download_command(self):
        properties = self.root / 'gradle/wrapper/gradle-wrapper.properties'
        properties.parent.mkdir(parents=True)
        properties.write_text('distributionUrl=https\\://services.gradle.org/distributions/gradle-9.6.0-bin.zip')
        with patch.dict(os.environ, GRADLE_USER_HOME=str(self.root / 'gradle-home')), patch.object(verifier, 'command') as command:
            with self.assertRaises(ValueError): verifier.cached_wrapper(self.root)
            command.assert_not_called()
            binary = self.root / 'gradle-home/wrapper/dists/gradle-9.6.0-bin/existing/gradle-9.6.0/bin/gradle.bat'
            binary.parent.mkdir(parents=True)
            binary.write_bytes(b'own synthetic cached-tool marker')
            self.assertEqual(binary, verifier.cached_wrapper(self.root))
            command.assert_not_called()


if __name__ == "__main__":
    unittest.main()

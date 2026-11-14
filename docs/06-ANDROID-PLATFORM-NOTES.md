# 06. Android platform notes

## 1. Foreground-service time limits (target API 35+)

- `dataSync` foreground services may run 6 hours in any 24-hour period; after that the system calls `Service.onTimeout(int, int)` and the
  service must call `stopSelf()` within a few seconds or the app crashes with a RemoteServiceException. Start dataSync services from direct
  user interaction (that resets the timer). If the 6 hours are used up, starting another throws `ForegroundServiceStartNotAllowedException`. [V, developer.android.com]
- New `mediaProcessing` type has the same 6h/24h rule and is intended for work like transcoding. [V]
- Test on any Android 15 device: `adb shell am compat enable FGS_INTRODUCE_TIME_LIMITS <package>`, and shorten the limit with
  `adb shell device_config put activity_manager data_sync_fgs_timeout_duration <ms>`. [V]
- Design: persist state, on `onTimeout` mark tasks PAUSED(FGS_TIMEOUT), stop, re-enqueue via WorkManager; a user tap on the notification or app
  brings it back to foreground and resumes.
- Do not start download services from a BOOT_COMPLETED receiver (Android 15 restricts launching some foreground-service types from it). [V that restrictions exist; exact list not re-checked]
- `SYSTEM_ALERT_WINDOW` no longer gives a free pass to start foreground services from the background unless an overlay is visible. [V] We don't use it.

## 2. Native binaries

- `youtubedl-android` requires `android:extractNativeLibs="true"`. [V, README]
- Executables must live in the APK's native-library directory (packaged as `lib*.so` under `jniLibs/<abi>/`), not be downloaded into writable app storage and
  executed there. [K: Android 10+ execution restrictions; verify in Phase 0.] This is why the QuickJS and aria2c binaries ship inside the library/APK.
- Handle `UnsatisfiedLinkError` and a missing ABI gracefully (offer the right APK split).

## 3. Storage

- On Android 10+ (API 30+ in the library README) scoped storage limits direct file access; the library README says apps can only write directly to a few locations. [V, README, partially truncated]
- Pattern: download into app-private storage, then publish to MediaStore (Downloads/Movies/Music) or a user-picked SAF tree. Persist the SAF permission with
  `takePersistableUriPermission`. [K]
- Check free space before each stage (doc 02).

## 4. Permissions budget

INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS (runtime on API 33+ [K]), FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC. WAKE_LOCK only if proven needed.
Snaptube declares 34 permissions [V]; none of its install/usage/overlay/all-files permissions are needed.

## 5. Clipboard

Do not poll the clipboard in the background. Offer a "paste link" chip when the app is in the foreground. (Snaptube has a `ClipMonitorService`, per the export manifest [V];
Android restricts background clipboard reads in modern versions [K].)

## 6. SDK levels

- Snaptube: minSdk 21 / targetSdk 35 [V]. YTDLnis README: Android 7.0 and above [V]; its IzzyOnDroid listing for 1.7.9.2 shows MinVer 6.0 and target 13 [V, dated Sept 2024]. The conflict shows
  numbers drift between releases.
- Start with minSdk 24 (Android 7.0), target the current API level, and let Phase 0 confirm what the library supports. [I]

## 7. Build

- JDK 17 is what the sample workflows I found use [V]; check what the current Android Gradle Plugin requires before pinning.
- Per-ABI splits; arm64-v8a first. R8 on release; add keep rules for the library if the app crashes after minification.
- Baseline Profiles and Macrobenchmark in Phase 3.

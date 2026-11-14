# Export performance benchmark

No device-level benchmark result is checked in. Use this procedure to compare
the pre-cache and cache-enabled builds on the same Android device.

## Test controls

- Use the same device, OS build, app build type, source media, project, export
  range, resolution, frame rate, bitrate, and format for both revisions.
- Copy all media to local device storage before starting. Avoid cloud-backed
  document providers.
- Keep the device unplugged from heavy workloads, close other apps, and let it
  cool to a similar temperature before each run.
- Run each scenario at least three times per revision and report the median.
  Keep the first/cold run separate from subsequent runs.
- Record the project and source-media hashes so both builds use identical input.

## Scenarios

Create and save fixed projects in Moody Editor:

1. 1-minute, 1080p30, one video clip, no effects.
2. 1-minute, 1080p30, multiple effects and adjustment layers.
3. 1-minute, 1080p30, multiple video and audio tracks.
4. 1-minute, 1080p30, text, stickers, and keyframes.
5. 5-minute, 1080p30, multiple tracks and representative effects.

Use the same export settings for every corresponding old/new project. Do not
lower bitrate or resolution between runs.

## Cache-enabled run

1. Start a fresh log capture and open the saved project.
2. Stop playback and leave the timeline unchanged until the `EXPORT_CACHE`
   `Prepared ...` message appears. It records segment count, cache hits, bytes,
   and background preparation wall time.
3. Start export and record the time from the export tap to completion. The
   `EXPORT_BENCHMARK` log records total export wall time and requested settings.
4. Repeat the preparation and export for each measured run. The app clears the
   cache when an export reaches a terminal state.
5. Also report preparation time separately. For end-to-end comparisons, add
   preparation wall time and export wall time; do not report button-to-complete
   time alone as total work.

The cache is only applicable to eligible layered projects and matching export
settings. A cache miss or `Prepared 0/...` is a valid result and must be
reported rather than silently excluded.

## Capturing logs and device metrics

With Android SDK platform tools installed:

```powershell
$adb = "C:\AndroidSDK\platform-tools\adb.exe"
& $adb logcat -c
# Run one export in the app, then:
& $adb logcat -d -s EXPORT_BENCHMARK EXPORT_CACHE EXPORT_PARALLEL EXPORT_ENCODER FFMPEG_PERF NATIVE_EXPORT |
    Out-File -Encoding utf8 .\export-run-log.txt
& $adb shell dumpsys meminfo com.moody.moodyvideoeditor |
    Out-File -Append -Encoding utf8 .\export-run-log.txt
& $adb shell dumpsys thermalservice |
    Out-File -Append -Encoding utf8 .\export-run-log.txt
```

Use Android Studio Profiler to capture CPU and memory during the same run.
Record device model, Android version, battery/thermal state, export duration,
background preparation duration, cache hit count/bytes, output size, and any
fallback or FFmpeg errors. Inspect the output duration, audio synchronization,
and representative frames for each scenario.

## Existing instrumentation

- `EXPORT_BENCHMARK`: total export wall time, outcome, settings, range, and clip
  count.
- `EXPORT_CACHE`: prepared segment count, cache hit count, cache bytes, and
  preparation wall time.
- `EXPORT_PARALLEL`: segment and concat results.
- `EXPORT_ENCODER`: runtime encoder candidate selected by capability checks.
- `FFMPEG_PERF`: individual FFmpeg session wall time and processed media time.
- `NATIVE_EXPORT`: Media3 Transformer start/progress.

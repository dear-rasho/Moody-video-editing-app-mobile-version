# Media3 export test harness

This note separates synthetic parity measurements from the connected-device
runtime smoke test; the two suites answer different questions.

## Current inputs and comparisons

[`Media3ExportParityTest.kt`](../app/src/androidTest/java/com/moody/moodyvideoeditor/Media3ExportParityTest.kt)
creates synthetic 320x240, 30 fps, 3-second sources with FFmpeg test patterns.
The harness compares sampled decoded frames using `MediaMetadataRetriever`.
It reports RGB-domain PSNR and an SSIM calculation over luma using fixed
BT.601-style weights, and saves a comparison sheet and metrics CSV.

The synthetic parity suite does not measure real-device performance or
compare Y/U/V planes. Its PSNR/SSIM values do not by themselves identify a
YUV matrix mismatch.

[`Media3ExportRuntimeTest.kt`](../app/src/androidTest/java/com/moody/moodyvideoeditor/Media3ExportRuntimeTest.kt)
contains the connected-device smoke test. It builds five `EditorState`s from
test media staged in the app-private `files/media3-inputs` directory, exports
each with the Media3 setting on and off, and persists timing/size rows in
`files/media3-generated-project-results.tsv`. The reported run used a
1920x1080 source clipped to five seconds and a local image in the multi-track
case. It checked output dimensions, duration, nonzero size, midpoint frame
decode, and sampled frames for near-solid-green corruption. This was one run
per route, not a repeated median benchmark.

## Export-route verification

The parity suite can request Media3 through `VideoExporter`, which falls back
to FFmpeg when composition building or export fails. Its requested setting is
not proof that Media3 produced the output. The runtime smoke test's observed
routes were Media3 composition for simple, filter, and mask projects, and
FFmpeg fallback for text and text-bearing multi-track projects. Verify route
logs (`EXPORT_PLAN`, `NATIVE_EXPORT`, `EXPORT_RESULT`) alongside dimensions
and timings when adding cases.

The composition builder currently rejects text clips/sequences, nonzero
rotation, and animated keyframes. Do not treat their debug-on outputs as
Media3 parity measurements.

## T4 device benchmark protocol

The initial connected-device smoke run is recorded in
[`MEDIA3_PIPELINE.md`](./MEDIA3_PIPELINE.md). It was a single five-second
run per project/route and is not a repeated median benchmark. For future
performance comparisons, use identical project files and export settings.
Record source hashes, device model/OS, app build, temperature/thermal state,
and free storage.

For each project type below, export through FFmpeg and Media3, and record
wall-clock milliseconds, output bytes, route, errors/logcat, playback
success, and visual inspection findings:

1. Simple 30-second clip, no effects.
2. Same clip with a filter.
3. Same clip with text and glow.
4. Same clip with an animated circle mask.
5. Base video plus a text overlay track.

Repeat each route at least three times under comparable thermal conditions;
report individual runs and the median. Define speedup as
`FFmpeg median export time / Media3 median export time`. Compute the eligible
project average only from cases where Media3 actually ran and output passed
quality/playback checks. Keep route failures out of the speedup denominator
and report them explicitly rather than silently dropping them.

For quality, compare representative beginning/middle/end frames between the
two routes in addition to the smoke checks, including crashes, green/black
frames, timing errors, and visible color or geometry differences. Record file
size separately because different encoders/bitrates can make it a confounder.
Keep parity, runtime, file size, route, and playback outcomes distinct in
reports.

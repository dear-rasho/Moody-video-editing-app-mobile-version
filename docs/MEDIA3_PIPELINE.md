# Media3 and FFmpeg export pipeline notes

This note records Media3 eligibility, output-dimension handling, and current
device measurements.

## Route and eligibility

`VideoExporter` attempts Media3 for MP4 when the Media3 export setting is
enabled. The default is enabled for new installs; an existing persisted
setting remains an override. It calls
`NativeVideoExporter.exportWithComposition`; if the composition is
unsupported or fails, export falls back to FFmpeg. A setting or test log label
alone therefore does not prove which exporter produced a file.

The eligibility checks are in
[`Media3CompositionBuilder.kt`](../app/src/main/java/com/moody/moodyvideoeditor/utils/Media3CompositionBuilder.kt#L237).
Static scale, scaleX/scaleY, crop, offset, and opacity are eligible. Rotation,
animated keyframes, text clips, and text sequences are rejected and use
FFmpeg. Unsupported clip types, transitions, and effects also use FFmpeg.
Supported clip-level filters, adjustments, color-wheel effects, mask
sequences, contiguous visual tracks, and eligible audio-only tracks are
represented in the composition.

## Output dimensions and encoder alignment

Media3 1.8.0's `DefaultEncoderFactory` aligns requested dimensions to the
selected encoder's supported resolution when encoder fallback is enabled.
The connected device reported 1920x1088 for a requested 1920x1080 output.
Disabling encoder fallback did not prevent that output size.

After a successful Media3 export, `NativeVideoExporter` checks the muxed
dimensions. If they exceed the request, FFmpegKit crops to the requested
top-left frame and re-encodes video, copying audio. It tries the existing
hardware/software encoder candidates and accepts a candidate only if the
result metadata has the exact requested dimensions. If the Media3 output is
smaller than requested or no crop encoder produces exact dimensions, the
Media3 export fails explicitly so `VideoExporter` can use its FFmpeg route.
Already exact-sized exports skip this extra pass.

## Current device measurements

The instrumentation test generated five `EditorState` projects from existing
device media: a real 1920x1080 video (the first five seconds of a 64.5-second
source) plus a local image used in the multi-track case. Each project was
exported at 1920x1080 with the debug Media3 setting on and with it off. Outputs
were checked for exact dimensions, 5-second duration, nonzero size, and a
decodable midpoint frame; the sampled frame check rejected near-solid-green
corruption.

| Project | Debug-on route | Debug-on ms | FFmpeg ms | Speedup | Dimensions (both) |
|---|---|---:|---:|---:|---|
| Simple clip | Media3 composition | 10,307 | 47,231 | 4.58x | 1920x1080 |
| Filter | Media3 composition | 9,434 | 46,544 | 4.93x | 1920x1080 |
| Text overlay | FFmpeg fallback | 38,332 | 39,484 | n/a | 1920x1080 |
| Mask | Media3 composition | 10,041 | 39,876 | 3.97x | 1920x1080 |
| Multi-track (video + image + text) | FFmpeg fallback | 95,054 | 95,698 | n/a | 1920x1080 |

Eligible-project mean speedup (simple, filter, mask): **4.50x**. Text clips
and text sequences remain routed to FFmpeg; the multi-track test included text
and therefore also used the conservative fallback. Timing for those cases is
the debug-on fallback path versus explicit FFmpeg, not a Media3 speedup.

The 1088 alignment fix was exercised on-device: Media3 produced 1920x1088,
then FFmpegKit's hardware crop/re-encode produced verified 1920x1080 output
while preserving audio in the dedicated regression test. The five generated
projects also all passed the exact-dimension assertion. No app crash or
instrumentation failure occurred. The device did emit unrelated OEM thermal
and Wi-Fi warnings during the long exports.

**Rollout gate passed for the tested eligible routes:** dimensions are exact,
eligible projects show a measured speedup, and unsupported text-bearing
projects fall back. The default is enabled; rotation and keyframes remain
ineligible and the previously measured colorspace divergence remains a known
issue.

The parity harness compares the top-left 1920x1080 area when Media3 returns
16-aligned padding. Its current synthetic baseline is 19.935 dB / 0.928 SSIM.
Filter, adjustment, color-wheel, fit/pad, crop, and offset deltas were within
±2 dB. Scale (+3.226 dB) and mask (+6.609 dB) were outside that strict band.
Rotation (-19.246 dB), keyframe samples (-10.599 and -7.883 dB at 0 and 1 s),
text overlay (-16.718 dB), and multi-track-with-text (-14.753 dB) showed
severe parity gaps. These cases are not cleared by the dimension fix; rotation
and keyframes are routed to FFmpeg, as are text clips/sequences.

In the earlier synthetic colorspace diagnostic, both outputs carried
TV-range BT.709 tags and neutral patches matched, while saturated colors
differed. RGB MAE was
R=0.562, G=13.179, B=4.216; this is a measured conversion divergence, not
proof that the Media3 source code uses a different tagged matrix. It remains
an accepted known issue for this rollout and is not changed here.

## Known limitations

- **Colorspace:** the synthetic baseline was 19.935 dB PSNR. The observed
  saturated-color divergence was attributed to BT.601-versus-BT.709 matrix
  selection for untagged input; this is a working diagnosis, not a confirmed
  source-level matrix mismatch.
- **Rotation:** the direct parity case measured -19.246 dB. Nonzero rotation
  remains routed to FFmpeg.
- **Keyframes:** parity deltas varied from -10.599 dB to +7.190 dB across
  the sampled times. Animated clips remain routed to FFmpeg.
- **Text overlay:** Media3 text rendering is not verified; text clips and
  text sequences use FFmpeg.
- **Multi-track with text:** Media3 composition is not verified for this
  case; text-bearing multi-track projects use FFmpeg. Static multi-visual
  tracks without text may remain eligible.
- **Other parity deltas:** the synthetic scale (+3.226 dB) and mask
  (+6.609 dB) results were outside the strict ±2 dB band. The device smoke
  test checked output integrity, not frame-by-frame parity.

## Phase 2 roadmap

1. **Colorspace normalization (P1, 1-2 days):** force FFmpeg to use BT.709
   for untagged input with an explicit colorspace conversion filter, or
   normalize source metadata during import; target baseline PSNR above 35 dB.
2. **Rotation shader (P2, 1 day):** investigate inverse rotation math in
   `TransformGlEffect` against the -19.246 dB parity case.
3. **Text overlay (P3, 1 day):** repair harness routing, verify
   `TextOverlayGlEffect`, then enable Media3 text only if parity passes.
4. **Keyframe interpolation (P4, 1 day):** investigate per-frame sampling
   against the observed -10.599 to +7.190 dB range.
5. **Multi-track text/image (P5, 1 day):** verify composition after text
   overlay is validated; then consider enabling the mixed-track route.

These are estimates, not release commitments. Keep each feature on its
current FFmpeg fallback until its route and output are independently verified.

## Rollback

For new installs, set `SettingsDefaults.DEBUG_MEDIA3_EXPORT` to `false` in
[`SettingsKeys.kt`](../app/src/main/java/com/moody/moodyvideoeditor/data/settings/SettingsKeys.kt#L71).
The exporter then uses FFmpeg for all MP4 projects. Because an explicitly
persisted DataStore value overrides the default, set that value to `false`
as well on any already-installed device that has an enabled override.

## Color conversion

### FFmpeg path in this repository

The export arguments do not select an FFmpeg hardware decoder for H.264. The
`h264_mediacodec` and `hevc_mediacodec` candidates in
[`VideoEncoderPolicy.kt`](../app/src/main/java/com/moody/moodyvideoeditor/utils/VideoEncoderPolicy.kt#L13)
are **output encoders**, not input decoders. Thus the current FFmpeg path
decodes H.264 through FFmpeg's normal decoder selection and does not use the
MediaCodec-to-SurfaceTexture decode path.

For YUV input, FFmpeg's `libswscale` graph selects colorspace coefficients
from the frame colorspace metadata. In FFmpeg n8.1, an unspecified colorspace
is marked incomplete and falls back to BT.470BG (the BT.601 family); explicit
BT.709 metadata selects BT.709 coefficients. Range normalization is also part
of the swscale pixel-format conversion. H.264 VUI metadata can populate the
frame's color range and colorspace; missing or inaccurate VUI can therefore
produce a different choice from a tagged source.

Relevant FFmpeg n8.1 sources:

- [`vf_scale.c`](https://github.com/FFmpeg/FFmpeg/blob/n8.1/libavfilter/vf_scale.c):
  scale filter matrix/range options and defaults.
- [`graph.c`](https://github.com/FFmpeg/FFmpeg/blob/n8.1/libswscale/graph.c):
  coefficient-table selection from colorspace metadata.
- [`format.c`](https://github.com/FFmpeg/FFmpeg/blob/n8.1/libswscale/format.c#L1352-L1365):
  unspecified colorspace fallback.
- [`yuv2rgb.c`](https://github.com/FFmpeg/FFmpeg/blob/n8.1/libswscale/yuv2rgb.c):
  YUV-to-RGB conversion and range arithmetic.
- [`h264_slice.c`](https://github.com/FFmpeg/FFmpeg/blob/n8.1/libavcodec/h264_slice.c):
  H.264 VUI color metadata propagation.

Some FFmpeg filter branches explicitly request BT.709 and TV-range input; for
example, the color-wheel conversion in
[`FFmpegExecutor.kt`](../app/src/main/java/com/moody/moodyvideoeditor/utils/FFmpegExecutor.kt#L3864).
That filter-specific setting should not be assumed to describe every export
branch. The final output arguments also tag the encoded output as BT.709/TV
range; output tagging is distinct from the input YUV-to-RGB conversion.

### Media3 path

For SDR video, Media3's effect pipeline samples decoded video through an
external GL texture. Android's decoder/SurfaceTexture/GPU boundary handles
the decoded YUV surface; the app's transform shader does not contain a
BT.601/BT.709 YUV conversion matrix. Media3's `DefaultShaderProgram` applies
color-transfer/working-color-space operations after sampling. Its explicit
`uYuvToRgbColorTransform` setup in `createWithExternalSampler` is in the HDR
YUV-target path and uses BT.2020 matrices.

This makes the Android decode/surface sampling boundary a plausible place for
an FFmpeg-versus-Media3 color difference, especially for absent or incorrect
source color metadata. The exact SDR matrix/range behavior is platform- and
codec-dependent and is not established by this app's source alone. Confirm it
with tagged and untagged real-device media and decoded-frame measurements.

Also, `(0.299, 0.587, 0.114)` and `(0.2126, 0.7152, 0.0722)` are RGB-to-luma
coefficient triplets, not the complete direct YUV-to-RGB conversion
coefficients. A PSNR difference by itself cannot identify which conversion
stage caused it. No specific 24 dB discrepancy or color-matrix root cause was
confirmed during this source review.

## Transform and crop math

[`TransformGlEffect.kt`](../app/src/main/java/com/moody/moodyvideoeditor/utils/TransformGlEffect.kt#L439)
maps each output canvas pixel back into the decoded source:

1. Undo translation and rotation in the order selected for the FFmpeg
   transform path.
2. Undo scale around the corresponding center/anchor.
3. Undo aspect-preserving fit and centered padding:
   `sourcePixels = (canvasPixels - pad) / fitScale`.
4. Reject coordinates outside the source and sample the texture using the
   decoder's texture-transform matrix.

For a 320x240 source fitted to a 1920x1080 canvas,
`fitScale = min(1920/320, 1080/240) = 4.5` and horizontal padding is 240
pixels. Canvas point (100,100) is in the left letterbox region: its inverse
source x coordinate is `(100 - 240) / 4.5`, which is outside the source.
The canvas center maps to source center (160,120). Thus an expectation that
(100,100) maps to a source pixel is not valid for this fit.

Crop bounds are a canvas-space alpha mask over the fitted source rectangle.
[`CropAlphaRenderer.kt`](../app/src/main/java/com/moody/moodyvideoeditor/utils/CropAlphaRenderer.kt#L100)
computes the same fitted content bounds, and FFmpeg applies the generated
crop-alpha mask after the transformed clip. This is why the shader tests the
output canvas coordinate for crop, rather than applying crop as a second
source-space scale.

The inverse rotation/scale/translation equations are consistent with the
corresponding FFmpeg perspective transform equations in
[`FFmpegExecutor.kt`](../app/src/main/java/com/moody/moodyvideoeditor/utils/FFmpegExecutor.kt#L1488).
No algebraic mismatch was found for the reviewed fit, crop, scale, rotation,
or offset examples. This does not establish pixel-identical output: the GL
texture sampling and FFmpeg's scaling/resampling can differ.

## Keyframe time and interpolation

`TransformGlEffect` computes local keyframe time as
`presentationTimeUs / 1e6 + timestampOffsetSec - clipStartSec`, clamped to
zero. The Media3 1.8.0 `VideoSampleExporter` source passes each item's
`offsetToAddUs` as the initial timestamp offset plus the accumulated duration
of preceding items in that sequence. For contiguous timeline items, this
makes a frame's sequence presentation time global; subtracting the clip's
timeline start yields item-local time.

Therefore:

- For presentation times 0, 1, and 2 seconds and a clip start of 0, local
  samples are 0, 1, and 2 seconds.
- A presentation time of 1 second with a clip start of 2 seconds is before
  that clip; clamping to 0 is appropriate. A frame from the clip itself
  should have a sequence presentation time at or after 2 seconds.

Media3's `KeyframeStore.sample` and FFmpeg's keyframe-expression generator
both use the start keyframe's easing for the interval. Scale-track composition,
default 50% anchors, and inverse rotation direction are also consistent in
the reviewed paths. No source-level keyframe arithmetic defect was confirmed.

If a parity failure persists, capture actual frame timestamps and test:

1. Two sequential clips where the second starts at 2 seconds, with a
   0-to-2-second animated position or scale track. Compare the second clip at
   sequence times 2 and 3 seconds and log the computed local time.
2. An asymmetric test card with corner markers and a center crosshair, using
   non-central anchor, scale, rotation, and translation keyframes. Compare
   FFmpeg and Media3 at identical presentation timestamps.
3. A portrait source with letterboxing plus crop, zoom, and rotation to make
   the fitted-content crop boundary visible.

Do not treat these source checks as a substitute for T4 real-device testing.

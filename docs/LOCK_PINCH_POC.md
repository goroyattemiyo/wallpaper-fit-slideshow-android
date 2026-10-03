# Lock Screen Pinch PoC

Branch:

`experiment/lock-live-pinch-poc`

Purpose:

Verify whether Redmi 12 5G / HyperOS forwards two-finger pinch events from the actual lock screen to an Android `WallpaperService.Engine`.

This is an experiment only. Do not merge into the V1 static-wallpaper implementation unless the device test succeeds.

## Why this branch exists

The normal app uses `WallpaperManager.setBitmap()` static wallpapers. A static wallpaper cannot receive touch events.

Android `WallpaperService.Engine` can request raw touch events with:

- `setTouchEventsEnabled(true)`
- `onTouchEvent(MotionEvent)`

The Android framework exposes those APIs, but an OEM lock-screen host can still consume or suppress gestures before they reach the wallpaper. Therefore Redmi / HyperOS behavior must be measured on-device.

## PoC behavior

- Reads the app's current Lock image, or the first Lock-enabled image as fallback.
- Renders it through a `WallpaperService`.
- Enables raw wallpaper touch events.
- Uses `ScaleGestureDetector` for pinch zoom.
- Saves the resulting zoom back to that item's `lockLayout` when the scale gesture ends.
- Draws a debug line at the bottom:
  - `scale=...`
  - `touch=...`
- Does not implement slideshow integration.
- Does not implement whole-wallpaper blur inside the Live Wallpaper PoC.
- Normal slideshow must be stopped before launching the PoC, otherwise static wallpaper updates could replace the Live Wallpaper.

## Device test

1. Install this branch.
2. Open the app.
3. Make sure at least one image is enabled for Lock.
4. Stop slideshow if it is running.
5. Tap **実験: ロック画面ピンチPoC**.
6. The system Live Wallpaper preview should open.
7. In the preview, perform a two-finger pinch.
8. Observe the bottom debug text.
   - If `touch` increases and the image scales, preview touch delivery works.
9. Apply the Live Wallpaper to **Lock screen only** if the system/OEM picker offers that choice.
   - If Lock-only is not offered, record that result. Do not assume it is supported.
10. Lock the device and wake to the actual lock screen.
11. Pinch in a relatively empty part of the lock screen, away from clock/buttons if possible.
12. Observe:
   - image size
   - `scale`
   - `touch`

## Result interpretation

### PASS

- Preview receives pinch events.
- Actual lock screen receives pinch events.
- `touch` increases on the real lock screen.
- Image visually zooms in/out.

This means Live Wallpaper is a viable technical path for direct lock-screen pinch on the tested Redmi / HyperOS version.

### OEM gesture block

- Preview pinch works.
- Actual lock screen does not increment `touch`.
- Image does not scale.

This strongly indicates the lock-screen host/OEM consumes the gesture before WallpaperService receives it. Do not try to hide this with retries or polling. Direct lock-screen pinch should remain unsupported for that environment.

### Picker limitation

- System preview opens, but there is no Lock-only application option.

Record this separately from touch delivery. It is a destination-selection limitation, not evidence that `onTouchEvent` itself fails.

### PoC implementation issue

- Preview itself does not react to pinch.

Then inspect service lifecycle/touch dispatch before making any conclusion about HyperOS lock-screen behavior.

## Verification status

- Android framework API behavior: checked against Android official documentation.
- Static source review: completed.
- Local Unit/Lint/assemble: not yet run for this experiment branch.
- Redmi / HyperOS real-device result: not yet verified.
- GitHub Actions: not run.

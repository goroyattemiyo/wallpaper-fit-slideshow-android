# Redmi 12 5G / HyperOS Real Device Test

## Purpose

GitHub Actionsを使う前に、Redmi 12 5G実機でV1の主要挙動を確認する。

この文書のチェックが終わるまでは、PRをReady for reviewにしない。

## 0. Preconditions

- Branch: `design/v1-foundation`
- Android Studio / Android SDK installed
- Android SDK Platform 36 installed
- USB debugging enabled on Redmi 12 5G
- Git working tree clean before pulling

## 1. Local Build

PowerShell:

```powershell
git checkout design/v1-foundation
git pull --ff-only
powershell -ExecutionPolicy Bypass -File .\scripts\local-verify.ps1 -Install
```

Expected:

- Unit tests PASS
- lintDebug PASS
- assembleDebug PASS
- APK exists at:
  `app\build\outputs\apk\debug\app-debug.apk`
- APK installs to Redmi

If any command fails, stop there and record the exact output.
Do not run GitHub Actions as a debugging substitute.

## 2. Test Images

Use at least these four types.

1. Portrait high resolution
2. Landscape high resolution
3. Square image
4. Small / low resolution image

If possible, include one JPEG that has EXIF rotation metadata.

## 3. Image Selection / Persistence

- [ ] App launches
- [ ] "画像を追加" opens Android document picker
- [ ] Multiple images can be selected at once
- [ ] Selected filenames appear in the list
- [ ] App can be killed and reopened
- [ ] Selected images remain in the list after reopening
- [ ] Images are still readable after reopening

Failure notes:

-

## 4. CONTAIN / No-upscale

For each aspect ratio:

- [ ] Open "表示調整"
- [ ] Select "全体表示"
- [ ] Entire image is visible
- [ ] Image is not distorted
- [ ] Background fills unused area
- [ ] Background color can be changed
- [ ] A low-resolution image is not automatically enlarged beyond its original logical size
- [ ] Save, leave editor, reopen editor
- [ ] Saved setting is restored

Important:
The low-resolution check is a core acceptance condition.

Failure notes:

-

## 5. CROP / Manual Focus

- [ ] Select "Crop"
- [ ] Image fills the visible preview
- [ ] Pinch zoom works
- [ ] Drag works
- [ ] Drag cannot expose blank area
- [ ] Save the zoom/position
- [ ] Reopen the editor
- [ ] The same zoom/position is restored

Failure notes:

-

## 6. Preview vs Actual Wallpaper

Test first with "ホーム".

- [ ] Adjust an image in preview
- [ ] Press "壁紙に適用"
- [ ] The same edited image becomes the wallpaper
- [ ] Subject position is close to preview
- [ ] No unexpected strong zoom occurs
- [ ] No obvious clipping that was not shown in preview

Repeat with:

- [ ] ロック
- [ ] 両方

Record any difference between preview and actual wallpaper.
HyperOS / launcher may apply additional positioning, so this difference must be measured rather than assumed.

Failure notes / screenshots:

-

## 7. Sequence / Random

With at least three images:

### Sequential
- [ ] Select an image and move it with "上へ / 下へ"
- [ ] The list order changes and remains after app restart
- [ ] Select "順番"
- [ ] Press "次へ" several times
- [ ] Images advance in order
- [ ] Last image wraps to first
- [ ] Current image is not immediately repeated when multiple images exist

### Random
- [ ] Select "ランダム"
- [ ] Press "次へ" repeatedly
- [ ] Current image is not immediately repeated when multiple images exist

Failure notes:

-

## 8. Automatic Slideshow

First verify the lightweight guard:

- [ ] With only one image, pressing "開始" does not start periodic slideshow
- [ ] Manual "壁紙に適用" still works with one image

Then add at least two images and start with 15 minutes.

- [ ] Press "開始"
- [ ] Status shows automatic slideshow ON
- [ ] Immediate first change works
- [ ] Leave app
- [ ] Device remains usable normally
- [ ] Wallpaper changes later without reopening app

WorkManager periodic work is not an exact alarm.
Do not fail the app merely because it does not execute exactly at 15:00 minutes.

If it is delayed for a long time on HyperOS:

1. Record the actual delay first
2. Check battery/background restrictions
3. Only then test whether changing HyperOS battery settings improves it

Do not add a permanent foreground service just to hide an OEM scheduling issue.

## 9. Stop / Race

- [ ] Press "停止"
- [ ] Status shows OFF
- [ ] It does not continue changing periodically
- [ ] Rapidly press "次へ" while a change is running
- [ ] App does not crash
- [ ] Duplicate rapid wallpaper changes do not occur

Failure notes:

-

## 10. Missing Source

After adding several images, make one source unavailable if practical
(move/delete it from the source provider).

- [ ] App does not crash
- [ ] Unreadable image is skipped during slideshow
- [ ] Another valid image can still be applied
- [ ] Error is visible if no usable image remains

Failure notes:

-

## 11. Memory / Large Image

Use several normal smartphone camera images.

- [ ] Adding many entries does not decode all images at once
- [ ] Editor opens without OOM
- [ ] "次へ" works repeatedly
- [ ] App remains responsive
- [ ] No obvious progressive memory growth is observed

Optional ADB check:

```powershell
adb shell dumpsys meminfo io.github.goroyattemiyo.wallpaperfitslideshow
```

Record PSS / TOTAL before and after repeated wallpaper changes if memory behavior looks suspicious.

## 12. Reboot

With slideshow enabled:

- [ ] Reboot Redmi
- [ ] Selected image URIs remain usable
- [ ] App settings remain
- [ ] Periodic work resumes without manually re-enabling the slideshow

This can take time because WorkManager scheduling is not exact.

## 13. Result

### Device

- Model: Redmi 12 5G
- Android version:
- HyperOS version:
- Screen resolution reported:
- Wallpaper desired size reported:
- Battery mode:

### Result

- [ ] PASS candidate
- [ ] FAIL - needs fix

### Issues

1.
2.
3.

### Screenshots / logs

-


## 14. Slideshow Target Selection

- [ ] Checkbox ON means slideshow target
- [ ] Checkbox OFF keeps the image in the library but excludes it from Next/automatic slideshow
- [ ] "すべてON" enables all images
- [ ] "すべてOFF" disables all images and stops automatic slideshow
- [ ] Start button shows the target image count
- [ ] Row tap selects the image for edit without changing slideshow ON/OFF
- [ ] An OFF image can still be opened in the editor and "この1枚を今すぐ壁紙にする" works

## 15. Shrink / Position

- [ ] Crop can shrink below 100%
- [ ] Crop can enlarge above 100%
- [ ] Pinch can both shrink and enlarge
- [ ] Zoom slider works from 20% to 500%
- [ ] Vertical position slider moves the image upward/downward
- [ ] Dragging works in CONTAIN and CROP
- [ ] A smaller image can be placed toward top/bottom without being forced to center
- [ ] Saved scale/position restores after closing editor

Clock/notification visibility check:

- [ ] Position a subject away from the lock-screen clock
- [ ] Apply to lock screen
- [ ] Confirm actual HyperOS clock overlap and record any preview difference

## 16. Blur Background

- [ ] Background style can switch between solid and blurred image
- [ ] Blur amount changes visibly
- [ ] Background image transparency changes visibly
- [ ] Foreground image remains sharp
- [ ] Settings survive editor reopen
- [ ] Actual wallpaper is close to preview

## 17. Folder Import

- [ ] "フォルダ" opens Android folder picker
- [ ] Selecting a folder adds image files from that tree
- [ ] Images in subfolders are also found
- [ ] App restart retains access to folder-backed images
- [ ] Non-image files are ignored

Note: Android 11+ SAF intentionally blocks selecting some protected roots such as storage root and Android/data. This is platform behavior.

## 18. ZIP Import

Use a test ZIP containing images plus non-image files.

- [ ] "ZIP" opens document picker
- [ ] Supported image files are imported
- [ ] Non-image files are ignored
- [ ] Imported images still work after app restart
- [ ] Removing an imported image does not affect the original ZIP
- [ ] Oversized ZIP/image fails safely without crashing

Current safety limits:

- up to 500 images
- up to 50 MiB per extracted image
- up to 500 MiB total extraction

## 19. Seconds Interval / Fast Mode

With at least two slideshow-target images:

- [ ] Open the compact slideshow settings dialog
- [ ] Select 10 seconds
- [ ] Start slideshow
- [ ] Ongoing fast-mode notification appears
- [ ] Wallpaper changes repeatedly around the selected interval while service remains active
- [ ] Stop button stops the changes and removes the foreground service notification
- [ ] Change to 15 minutes or longer and confirm fast-mode notification disappears
- [ ] Normal WorkManager mode remains available for 15 minutes or longer

Timing is best-effort rather than an exact-alarm guarantee. Record actual behavior on HyperOS.

## 20. Compact Settings UI

- [ ] Main screen no longer shows three large setting spinners
- [ ] Main screen shows one compact summary such as "順番 / ホーム / 30秒"
- [ ] Settings button opens order / target / interval controls
- [ ] Saved values appear in the compact summary



## 21. Gesture-first Editor UI

- [ ] Top-left "← 戻る" is always visible
- [ ] Back button saves the current layout and returns to the list
- [ ] Preview keeps the actual device aspect ratio
- [ ] Preview no longer occupies the whole screen
- [ ] Main editor actions remain visible without scrolling
- [ ] "自由調整" supports pinch to shrink/enlarge
- [ ] One-finger drag moves the image
- [ ] "全体表示" remains available as a quick fit mode
- [ ] Background blur/transparency controls are inside the Background dialog rather than always occupying the editor
- [ ] "この1枚を今すぐ壁紙にする" remains visible


## 22. Direct Gesture Editing

- [x] No "自由調整" selection is required before touching the preview
- [x] One-finger drag works immediately
- [x] Pinch works immediately from whole-image mode
- [ ] First pinch does not cause an obvious size jump
- [x] Pinch can shrink and enlarge
- [ ] "全体表示に戻す" restores centered whole-image view
- [ ] After restoring whole-image view, pinch editing can start again directly

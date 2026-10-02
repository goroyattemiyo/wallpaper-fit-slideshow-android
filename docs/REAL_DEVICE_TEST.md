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

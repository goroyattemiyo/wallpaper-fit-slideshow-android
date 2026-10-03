package io.github.goroyattemiyo.wallpaperfitslideshow.model

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {
    @Test
    fun wallpaperBlurRadiusIsIndependentForHomeAndLock() {
        val settings = AppSettings(
            homeWallpaperBlurRadius = 12,
            lockWallpaperBlurRadius = 3,
        )

        assertEquals(
            12,
            settings.wallpaperBlurRadiusFor(WallpaperTarget.HOME),
        )
        assertEquals(
            3,
            settings.wallpaperBlurRadiusFor(WallpaperTarget.LOCK),
        )
        assertEquals(
            0,
            settings.wallpaperBlurRadiusFor(WallpaperTarget.BOTH),
        )
    }
}

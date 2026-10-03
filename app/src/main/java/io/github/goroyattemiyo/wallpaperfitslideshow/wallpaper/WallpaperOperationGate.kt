package io.github.goroyattemiyo.wallpaperfitslideshow.wallpaper

import java.util.concurrent.locks.ReentrantLock

object WallpaperOperationGate {
    private val lock = ReentrantLock()

    fun <T> tryRun(block: () -> T): T? {
        if (!lock.tryLock()) {
            return null
        }
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }
}

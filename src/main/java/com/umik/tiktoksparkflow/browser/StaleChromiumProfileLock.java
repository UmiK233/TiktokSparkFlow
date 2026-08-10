package com.umik.tiktoksparkflow.browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Chromium 异常退出或容器重建后遗留的 Profile 临时锁。 */
final class StaleChromiumProfileLock {
    private static final String[] LOCK_FILES = {
            "SingletonLock", "SingletonCookie", "SingletonSocket"
    };

    private StaleChromiumProfileLock() {
    }

    static boolean clear(Path profilePath) {
        boolean removed = false;
        for (String lockFile : LOCK_FILES) {
            try {
                removed |= Files.deleteIfExists(profilePath.resolve(lockFile));
            } catch (IOException error) {
                throw new IllegalStateException("无法清除 Chromium 残留锁文件：" + lockFile, error);
            }
        }
        return removed;
    }
}

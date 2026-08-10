package com.umik.tiktoksparkflow.browser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaleChromiumProfileLockTest {
    @TempDir
    Path tempDir;

    @Test
    void clearsOnlyChromiumSingletonFiles() throws Exception {
        Files.writeString(tempDir.resolve("SingletonLock"), "lock");
        Files.writeString(tempDir.resolve("SingletonCookie"), "cookie");
        Files.writeString(tempDir.resolve("SingletonSocket"), "socket");
        Path loginData = tempDir.resolve("Cookies");
        Files.writeString(loginData, "session");

        assertTrue(StaleChromiumProfileLock.clear(tempDir));
        assertFalse(Files.exists(tempDir.resolve("SingletonLock")));
        assertFalse(Files.exists(tempDir.resolve("SingletonCookie")));
        assertFalse(Files.exists(tempDir.resolve("SingletonSocket")));
        assertTrue(Files.exists(loginData));
    }
}

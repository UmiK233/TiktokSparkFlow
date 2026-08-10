package com.umik.tiktoksparkflow.browser;

import com.umik.tiktoksparkflow.exception.ProfileBusyException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleUserProfileGuardTest {
    @Test
    void preventsAnotherThreadFromUsingTheProfile() {
        SingleUserProfileGuard guard = new SingleUserProfileGuard();
        try (SingleUserProfileGuard.Lease ignored = guard.acquire()) {
            assertTrue(guard.isBusy());
            CompletableFuture<Void> contender = CompletableFuture.runAsync(() ->
                    assertThrows(ProfileBusyException.class, guard::acquire));
            contender.join();
        }
    }
}

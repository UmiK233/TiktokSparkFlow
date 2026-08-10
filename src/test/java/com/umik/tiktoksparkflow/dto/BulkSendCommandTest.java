package com.umik.tiktoksparkflow.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BulkSendCommandTest {
    @Test
    void allowsEmptyMessageWhenOnlySelectingFriends() {
        assertDoesNotThrow(() -> new BulkSendCommand("", false));
    }

    @Test
    void rejectsEmptyMessageWhenActuallySending() {
        assertThrows(IllegalArgumentException.class,
                () -> new BulkSendCommand(" ", true));
    }
}

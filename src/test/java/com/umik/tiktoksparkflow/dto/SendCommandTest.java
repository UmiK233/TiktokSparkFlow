package com.umik.tiktoksparkflow.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SendCommandTest {
    @Test
    void trimsInput() {
        SendCommand command = new SendCommand(" 星火 ", " 续火花 ", true);
        assertEquals("星火", command.targetNickname());
        assertEquals("续火花", command.message());
    }

    @Test
    void rejectsBlankTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> new SendCommand(" ", "续火花", true));
    }

    @Test
    void permitsBlankMessageForSelectionOnly() {
        SendCommand command = new SendCommand("星火", "", false);
        assertEquals("", command.message());
    }
}

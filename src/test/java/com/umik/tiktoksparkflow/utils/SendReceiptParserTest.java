package com.umik.tiktoksparkflow.utils;

import com.umik.tiktoksparkflow.vo.SendReceiptVO;
import com.microsoft.playwright.Response;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SendReceiptParserTest {
    private final SendReceiptParser parser = new SendReceiptParser();

    @Test
    void rejectsKickEvenWhenHttpStatusIs200() {
        SendReceiptVO receipt = parser.parse(response(200, "{\"decision\":\"KICK\"}"));
        assertFalse(receipt.accepted());
    }

    @Test
    void acceptsSuccessfulTransportWithoutRejectDecision() {
        SendReceiptVO receipt = parser.parse(response(200, "binary-or-non-json-response"));
        assertTrue(receipt.accepted());
    }

    @Test
    void rejectsNon2xxTransport() {
        SendReceiptVO receipt = parser.parse(response(503, ""));
        assertFalse(receipt.accepted());
    }

    private Response response(int status, String body) {
        Response response = mock(Response.class);
        when(response.status()).thenReturn(status);
        when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(response.headerValue("x-tt-logid")).thenReturn("test-logid");
        return response;
    }
}

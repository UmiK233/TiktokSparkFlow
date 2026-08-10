package com.umik.tiktoksparkflow.utils;

import com.umik.tiktoksparkflow.vo.SendReceiptVO;
import com.microsoft.playwright.Response;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SendReceiptParser {
    private static final Pattern DECISION_PATTERN = Pattern.compile(
            "\\\"decision\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> REJECT_DECISIONS = Set.of(
            "KICK", "REJECT", "REJECTED", "DENY", "DENIED",
            "BLOCK", "BLOCKED", "FAIL", "FAILED");

    public SendReceiptVO parse(Response response) {
        String body;
        try {
            body = new String(response.body(), StandardCharsets.UTF_8);
        } catch (RuntimeException error) {
            body = "";
        }
        String decision = "";
        Matcher matcher = DECISION_PATTERN.matcher(body);
        if (matcher.find()) {
            decision = matcher.group(1).trim().toUpperCase(Locale.ROOT);
        }
        boolean transportOk = response.status() >= 200 && response.status() < 300;
        boolean accepted = transportOk && !REJECT_DECISIONS.contains(decision);
        return new SendReceiptVO(response.status(), decision, accepted, body.length(),
                response.headerValue("x-tt-logid"));
    }
}

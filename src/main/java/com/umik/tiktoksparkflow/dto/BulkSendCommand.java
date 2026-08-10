package com.umik.tiktoksparkflow.dto;

public record BulkSendCommand(String message, boolean sendMessage) {
    public BulkSendCommand {
        message = message == null ? "" : message.trim();
        if (sendMessage && message.isBlank()) {
            throw new IllegalArgumentException("执行真实群发时，消息内容不能为空");
        }
    }
}

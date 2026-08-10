package com.umik.tiktoksparkflow.dto;

public record SendCommand(String targetNickname, String message, boolean sendMessage) {
    public SendCommand {
        targetNickname = targetNickname == null ? "" : targetNickname.trim();
        message = message == null ? "" : message.trim();
        if (targetNickname.isBlank()) {
            throw new IllegalArgumentException("好友昵称不能为空");
        }
        if (sendMessage && message.isBlank()) {
            throw new IllegalArgumentException("执行真实发送时，消息内容不能为空");
        }
    }
}

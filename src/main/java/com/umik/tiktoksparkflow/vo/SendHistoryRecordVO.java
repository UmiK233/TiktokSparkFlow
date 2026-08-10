package com.umik.tiktoksparkflow.vo;

public record SendHistoryRecordVO(
        String taskId,
        String targetNickname,
        String message,
        String sentAt
) {
}

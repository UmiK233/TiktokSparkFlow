package com.umik.tiktoksparkflow.entity;

public record SendHistoryRecordEntity(
        String taskId,
        String targetNickname,
        String message,
        String sentAt
) {
}

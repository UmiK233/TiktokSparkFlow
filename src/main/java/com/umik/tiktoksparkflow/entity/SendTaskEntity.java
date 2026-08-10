package com.umik.tiktoksparkflow.entity;

import com.umik.tiktoksparkflow.enums.SendTaskStatus;

import java.util.List;

public record SendTaskEntity(
        String taskId,
        SendTaskStatus status,
        String message,
        boolean sendMessage,
        List<String> targets,
        int nextTargetIndex,
        int total,
        int completed,
        int succeeded,
        int failed,
        int skipped,
        String currentTarget,
        List<SendTaskItemEntity> results,
        String createdAt,
        String startedAt,
        String finishedAt,
        boolean cancelRequested,
        String detail
) {
    public SendTaskEntity {
        targets = targets == null ? List.of() : List.copyOf(targets);
        results = results == null ? List.of() : List.copyOf(results);
        currentTarget = currentTarget == null ? "" : currentTarget;
        detail = detail == null ? "" : detail;
    }
}

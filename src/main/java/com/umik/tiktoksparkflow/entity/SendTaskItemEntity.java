package com.umik.tiktoksparkflow.entity;

import com.umik.tiktoksparkflow.enums.SendTaskItemStatus;

public record SendTaskItemEntity(
        String targetNickname,
        SendTaskItemStatus status,
        String detail,
        String completedAt
) {
}

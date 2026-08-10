package com.umik.tiktoksparkflow.vo;

import com.umik.tiktoksparkflow.enums.SendTaskItemStatus;

public record SendTaskItemVO(
        String targetNickname,
        SendTaskItemStatus status,
        String detail,
        String completedAt
) {
}

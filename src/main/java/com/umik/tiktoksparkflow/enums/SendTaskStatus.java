package com.umik.tiktoksparkflow.enums;

public enum SendTaskStatus {
    PENDING,
    RUNNING,
    WAITING_FOR_LOGIN,
    WAITING_FOR_VERIFICATION,
    COMPLETED,
    PARTIAL_FAILED,
    FAILED,
    CANCELLED
}

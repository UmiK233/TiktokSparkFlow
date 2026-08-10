package com.umik.tiktoksparkflow.vo;

public record SendReceiptVO(
        int httpStatus,
        String decision,
        boolean accepted,
        int bodyLength,
        String logId
) {
}

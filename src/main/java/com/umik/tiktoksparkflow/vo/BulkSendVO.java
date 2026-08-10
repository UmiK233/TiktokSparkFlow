package com.umik.tiktoksparkflow.vo;

import java.util.List;

public record BulkSendVO(
        boolean success,
        int total,
        int succeeded,
        int failed,
        List<SendResultVO> results
) {
    public static BulkSendVO from(List<SendResultVO> results) {
        int succeeded = (int) results.stream().filter(SendResultVO::success).count();
        int failed = results.size() - succeeded;
        return new BulkSendVO(
                failed == 0,
                results.size(),
                succeeded,
                failed,
                List.copyOf(results));
    }
}

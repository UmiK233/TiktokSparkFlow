package com.umik.tiktoksparkflow.vo;

import java.util.List;

public record SendHistoryDayVO(
        String date,
        List<SendHistoryRecordVO> records
) {
    public SendHistoryDayVO {
        records = records == null ? List.of() : List.copyOf(records);
    }
}

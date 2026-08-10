package com.umik.tiktoksparkflow.entity;

import java.util.List;

public record SendHistoryDayEntity(
        String date,
        List<SendHistoryRecordEntity> records
) {
    public SendHistoryDayEntity {
        records = records == null ? List.of() : List.copyOf(records);
    }
}

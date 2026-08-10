package com.umik.tiktoksparkflow.service.impl;

import com.umik.tiktoksparkflow.entity.SendHistoryRecordEntity;
import com.umik.tiktoksparkflow.mapper.SendHistoryMapper;
import com.umik.tiktoksparkflow.service.SendHistoryService;
import com.umik.tiktoksparkflow.vo.SendHistoryDayVO;
import com.umik.tiktoksparkflow.vo.SendHistoryRecordVO;
import com.umik.tiktoksparkflow.utils.Gmt8Time;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Service
public class SendHistoryServiceImpl implements SendHistoryService {
    private final SendHistoryMapper historyMapper;

    public SendHistoryServiceImpl(SendHistoryMapper historyMapper) {
        this.historyMapper = historyMapper;
    }

    @Override
    public SendHistoryDayVO list(String date) {
        LocalDate targetDate = parseDate(date);
        return new SendHistoryDayVO(
                targetDate.toString(),
                historyMapper.records(targetDate).stream()
                        .map(this::toVO)
                        .toList());
    }

    private LocalDate parseDate(String date) {
        if (date == null || date.isBlank()) {
            return historyMapper.today();
        }
        try {
            return LocalDate.parse(date);
        } catch (DateTimeParseException error) {
            throw new IllegalArgumentException("日期格式必须为 yyyy-MM-dd");
        }
    }

    private SendHistoryRecordVO toVO(SendHistoryRecordEntity record) {
        return new SendHistoryRecordVO(
                record.taskId(),
                record.targetNickname(),
                record.message(),
                Gmt8Time.normalize(record.sentAt()));
    }
}

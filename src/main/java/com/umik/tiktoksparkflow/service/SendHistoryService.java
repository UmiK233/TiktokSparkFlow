package com.umik.tiktoksparkflow.service;

import com.umik.tiktoksparkflow.vo.SendHistoryDayVO;

public interface SendHistoryService {
    SendHistoryDayVO list(String date);
}

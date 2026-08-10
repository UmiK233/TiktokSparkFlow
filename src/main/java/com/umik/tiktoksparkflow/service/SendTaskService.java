package com.umik.tiktoksparkflow.service;

import com.umik.tiktoksparkflow.dto.BulkSendCommand;
import com.umik.tiktoksparkflow.vo.SendTaskVO;

import java.util.List;

public interface SendTaskService {
    SendTaskVO create(BulkSendCommand command);
    SendTaskVO get(String taskId);
    List<SendTaskVO> list();
    SendTaskVO cancel(String taskId);
    SendTaskVO retryFailed(String taskId);
}

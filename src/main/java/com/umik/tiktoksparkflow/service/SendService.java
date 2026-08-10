package com.umik.tiktoksparkflow.service;

import com.umik.tiktoksparkflow.dto.BulkSendCommand;
import com.umik.tiktoksparkflow.dto.SendCommand;
import com.umik.tiktoksparkflow.vo.BulkSendVO;
import com.umik.tiktoksparkflow.vo.SendResultVO;

public interface SendService {
    SendResultVO send(SendCommand command);
    BulkSendVO sendSelected(BulkSendCommand command);
}

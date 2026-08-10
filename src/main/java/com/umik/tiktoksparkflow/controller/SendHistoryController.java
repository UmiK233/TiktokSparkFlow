package com.umik.tiktoksparkflow.controller;

import com.umik.tiktoksparkflow.common.Result;
import com.umik.tiktoksparkflow.aspect.OperationLog;
import com.umik.tiktoksparkflow.service.SendHistoryService;
import com.umik.tiktoksparkflow.vo.SendHistoryDayVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/send-history")
public class SendHistoryController {
    private final SendHistoryService sendHistoryService;

    public SendHistoryController(SendHistoryService sendHistoryService) {
        this.sendHistoryService = sendHistoryService;
    }

    @GetMapping
    @OperationLog("查询发送历史")
    public Result<SendHistoryDayVO> list(@RequestParam(required = false) String date) {
        return Result.success("发送记录查询成功", sendHistoryService.list(date));
    }
}

package com.umik.tiktoksparkflow.controller;

import com.umik.tiktoksparkflow.dto.BulkSendCommand;
import com.umik.tiktoksparkflow.aspect.OperationLog;
import com.umik.tiktoksparkflow.common.Result;
import com.umik.tiktoksparkflow.dto.SendCommand;
import com.umik.tiktoksparkflow.service.SendService;
import com.umik.tiktoksparkflow.vo.BulkSendVO;
import com.umik.tiktoksparkflow.vo.SendResultVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/messages")
public class SendController {
    private final SendService sendService;

    public SendController(SendService sendService) {
        this.sendService = sendService;
    }

    @PostMapping("/send")
    @OperationLog("向单个好友发送消息")
    public Result<SendResultVO> send(@RequestBody SendCommand command) {
        return Result.success("单个好友处理完成", sendService.send(command));
    }

    @PostMapping("/send-selected")
    @OperationLog("向续火花名单同步发送")
    public Result<BulkSendVO> sendSelected(@RequestBody BulkSendCommand command) {
        return Result.success("同步群发处理完成", sendService.sendSelected(command));
    }
}

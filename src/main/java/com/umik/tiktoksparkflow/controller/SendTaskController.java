package com.umik.tiktoksparkflow.controller;

import com.umik.tiktoksparkflow.common.Result;
import com.umik.tiktoksparkflow.aspect.OperationLog;
import com.umik.tiktoksparkflow.dto.BulkSendCommand;
import com.umik.tiktoksparkflow.service.SendTaskService;
import com.umik.tiktoksparkflow.vo.SendTaskVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/send-tasks")
public class SendTaskController {
    private final SendTaskService sendTaskService;

    public SendTaskController(SendTaskService sendTaskService) {
        this.sendTaskService = sendTaskService;
    }

    @PostMapping
    @OperationLog("创建群发任务")
    public Result<SendTaskVO> create(@RequestBody BulkSendCommand command) {
        return Result.success("发送任务创建成功", sendTaskService.create(command));
    }

    @GetMapping
    @OperationLog("查询发送任务列表")
    public Result<List<SendTaskVO>> list() {
        return Result.success("发送任务列表查询成功", sendTaskService.list());
    }

    @GetMapping("/{taskId}")
    @OperationLog("查询发送任务详情")
    public Result<SendTaskVO> get(@PathVariable String taskId) {
        return Result.success("发送任务查询成功", sendTaskService.get(taskId));
    }

    @PostMapping("/{taskId}/cancel")
    @OperationLog("取消发送任务")
    public Result<SendTaskVO> cancel(@PathVariable String taskId) {
        return Result.success("任务取消请求已提交", sendTaskService.cancel(taskId));
    }

    @PostMapping("/{taskId}/retry-failed")
    @OperationLog("重试发送失败好友")
    public Result<SendTaskVO> retryFailed(@PathVariable String taskId) {
        return Result.success("失败好友重试任务创建成功",
                sendTaskService.retryFailed(taskId));
    }
}

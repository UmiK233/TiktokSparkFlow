package com.umik.tiktoksparkflow.job;

import com.umik.tiktoksparkflow.dto.BulkSendCommand;
import com.umik.tiktoksparkflow.enums.SendTaskStatus;
import com.umik.tiktoksparkflow.service.RuntimeSettingsService;
import com.umik.tiktoksparkflow.service.SendTaskService;
import com.umik.tiktoksparkflow.vo.RuntimeSettingsVO;
import com.umik.tiktoksparkflow.vo.SendTaskVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AutoSendJobTest {
    @Test
    void 未启用时不查询也不创建任务() {
        SendTaskService sendTaskService = mock(SendTaskService.class);
        AutoSendJob job = new AutoSendJob(settings(false, "续火花", true), sendTaskService);

        job.runDaily();

        verifyNoInteractions(sendTaskService);
    }

    @Test
    void 已有未完成任务时跳过本次自动触发() {
        SendTaskService sendTaskService = mock(SendTaskService.class);
        when(sendTaskService.list()).thenReturn(List.of(task("running", SendTaskStatus.RUNNING)));
        AutoSendJob job = new AutoSendJob(settings(true, "续火花", true), sendTaskService);

        job.runDaily();

        verify(sendTaskService, never()).create(any());
    }

    @Test
    void 启用后按配置创建发送任务() {
        SendTaskService sendTaskService = mock(SendTaskService.class);
        when(sendTaskService.list()).thenReturn(List.of());
        when(sendTaskService.create(any())).thenReturn(task("auto-task", SendTaskStatus.PENDING));
        AutoSendJob job = new AutoSendJob(settings(true, "今日续火花", true), sendTaskService);

        job.runDaily();

        ArgumentCaptor<BulkSendCommand> command = ArgumentCaptor.forClass(BulkSendCommand.class);
        verify(sendTaskService).create(command.capture());
        assertEquals("今日续火花", command.getValue().message());
        assertEquals(true, command.getValue().sendMessage());
    }

    private RuntimeSettingsService settings(boolean enabled, String message, boolean sendMessage) {
        RuntimeSettingsService service = mock(RuntimeSettingsService.class);
        when(service.get()).thenReturn(new RuntimeSettingsVO(enabled, "00:02", message, sendMessage, false, true,
                "", "", 587, "", "", "", true, "subject", "content"));
        return service;
    }

    private SendTaskVO task(String taskId, SendTaskStatus status) {
        return new SendTaskVO(
                taskId, status, "续火花", true, List.of("星火"),
                0, 1, 0, 0, 0, 0, "", List.of(),
                "2026-08-04T10:00:00+08:00", null, null, false, "");
    }
}

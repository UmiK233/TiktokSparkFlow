package com.umik.tiktoksparkflow.service;

import com.umik.tiktoksparkflow.browser.BrowserRuntime;
import com.umik.tiktoksparkflow.browser.SingleUserProfileGuard;
import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.dto.BulkSendCommand;
import com.umik.tiktoksparkflow.entity.FriendSelectionEntity;
import com.umik.tiktoksparkflow.entity.SendTaskEntity;
import com.umik.tiktoksparkflow.entity.SendTaskItemEntity;
import com.umik.tiktoksparkflow.enums.SendTaskItemStatus;
import com.umik.tiktoksparkflow.enums.SendTaskStatus;
import com.umik.tiktoksparkflow.exception.LoginRequiredException;
import com.umik.tiktoksparkflow.mapper.FriendSelectionMapper;
import com.umik.tiktoksparkflow.mapper.FriendListMapper;
import com.umik.tiktoksparkflow.mapper.SendHistoryMapper;
import com.umik.tiktoksparkflow.mapper.SendTaskMapper;
import com.umik.tiktoksparkflow.service.impl.SendTaskServiceImpl;
import com.umik.tiktoksparkflow.vo.RuntimeSettingsVO;
import com.umik.tiktoksparkflow.utils.SendReceiptParser;
import com.umik.tiktoksparkflow.vo.SendTaskVO;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SendTaskServiceImplTest {
    @Test
    void skipsFriendsAlreadySentTodayWithoutOpeningBrowser() {
        BrowserRuntime browserRuntime = mock(BrowserRuntime.class);
        FriendSelectionMapper selectionMapper = mock(FriendSelectionMapper.class);
        SendTaskMapper taskMapper = mock(SendTaskMapper.class);
        SendHistoryMapper historyMapper = mock(SendHistoryMapper.class);
        com.umik.tiktoksparkflow.service.RuntimeSettingsService runtimeSettingsService = mock(com.umik.tiktoksparkflow.service.RuntimeSettingsService.class);
        when(runtimeSettingsService.get()).thenReturn(new RuntimeSettingsVO(false, "00:00", "续火花", true, false, true,
                "", "", 587, "", "", "", true, "subject", "content"));
        when(taskMapper.loadAll()).thenReturn(List.of());
        when(selectionMapper.load()).thenReturn(new FriendSelectionEntity(List.of("星火", "南.")));
        when(historyMapper.wasSentToday(anyString())).thenReturn(true);

        SendTaskServiceImpl service = service(
                browserRuntime, selectionMapper, taskMapper, historyMapper, runtimeSettingsService);
        try {
            service.restoreTasks();
            SendTaskVO created = service.create(new BulkSendCommand("续火花", true));
            SendTaskVO completed = awaitTerminal(service, created.taskId());

            assertEquals(SendTaskStatus.COMPLETED, completed.status());
            assertEquals(2, completed.completed());
            assertEquals(2, completed.skipped());
            assertEquals(SendTaskItemStatus.SKIPPED_ALREADY_SENT,
                    completed.results().get(0).status());
            verifyNoInteractions(browserRuntime);
        } finally {
            service.destroy();
        }
    }

    @Test
    void waitsForLoginAndRetriesCurrentFriend() {
        BrowserRuntime browserRuntime = mock(BrowserRuntime.class);
        FriendSelectionMapper selectionMapper = mock(FriendSelectionMapper.class);
        SendTaskMapper taskMapper = mock(SendTaskMapper.class);
        SendHistoryMapper historyMapper = mock(SendHistoryMapper.class);
        com.umik.tiktoksparkflow.service.RuntimeSettingsService runtimeSettingsService = mock(com.umik.tiktoksparkflow.service.RuntimeSettingsService.class);
        when(runtimeSettingsService.get()).thenReturn(new RuntimeSettingsVO(false, "00:00", "续火花", true, false, true,
                "", "", 587, "", "", "", true, "subject", "content"));
        when(taskMapper.loadAll()).thenReturn(List.of());
        when(selectionMapper.load()).thenReturn(new FriendSelectionEntity(List.of("星火")));

        AtomicInteger calls = new AtomicInteger();
        when(browserRuntime.execute(any())).thenAnswer(invocation -> {
            int call = calls.getAndIncrement();
            if (call == 0) {
                throw new LoginRequiredException("登录失效");
            }
            if (call == 1) {
                return true;
            }
            return null;
        });

        SendTaskServiceImpl service = service(
                browserRuntime, selectionMapper, taskMapper, historyMapper, runtimeSettingsService);
        try {
            service.restoreTasks();
            SendTaskVO created = service.create(new BulkSendCommand("", false));
            SendTaskVO completed = awaitTerminal(service, created.taskId());

            assertEquals(SendTaskStatus.COMPLETED, completed.status());
            assertEquals(SendTaskItemStatus.SELECTED, completed.results().get(0).status());
            verify(browserRuntime, times(3)).execute(any());
        } finally {
            service.destroy();
        }
    }

    @Test
    void retryTaskContainsOnlyFailedFriends() {
        BrowserRuntime browserRuntime = mock(BrowserRuntime.class);
        FriendSelectionMapper selectionMapper = mock(FriendSelectionMapper.class);
        SendTaskMapper taskMapper = mock(SendTaskMapper.class);
        SendHistoryMapper historyMapper = mock(SendHistoryMapper.class);
        com.umik.tiktoksparkflow.service.RuntimeSettingsService runtimeSettingsService = mock(com.umik.tiktoksparkflow.service.RuntimeSettingsService.class);
        when(runtimeSettingsService.get()).thenReturn(new RuntimeSettingsVO(false, "00:00", "续火花", true, false, true,
                "", "", 587, "", "", "", true, "subject", "content"));
        SendTaskEntity source = new SendTaskEntity(
                "source-task",
                SendTaskStatus.PARTIAL_FAILED,
                "续火花",
                true,
                List.of("成功好友", "失败好友"),
                2, 2, 2, 1, 1, 0, "",
                List.of(
                        new SendTaskItemEntity("成功好友", SendTaskItemStatus.SENT,
                                "发送成功", "2026-08-04T10:00:00+08:00"),
                        new SendTaskItemEntity("失败好友", SendTaskItemStatus.FAILED,
                                "发送失败", "2026-08-04T10:01:00+08:00")),
                "2026-08-04T09:59:00+08:00",
                "2026-08-04T09:59:01+08:00",
                "2026-08-04T10:01:00+08:00",
                false,
                "存在失败好友");
        when(taskMapper.loadAll()).thenReturn(List.of(source));
        when(historyMapper.wasSentToday("失败好友")).thenReturn(true);

        SendTaskServiceImpl service = service(
                browserRuntime, selectionMapper, taskMapper, historyMapper, runtimeSettingsService);
        try {
            service.restoreTasks();
            SendTaskVO retried = service.retryFailed(source.taskId());
            SendTaskVO completed = awaitTerminal(service, retried.taskId());

            assertEquals(List.of("失败好友"), retried.targets());
            assertEquals(1, completed.total());
            assertEquals(1, completed.skipped());
            verifyNoInteractions(browserRuntime);
        } finally {
            service.destroy();
        }
    }

    private SendTaskServiceImpl service(
            BrowserRuntime browserRuntime,
            FriendSelectionMapper selectionMapper,
            SendTaskMapper taskMapper,
            SendHistoryMapper historyMapper,
            com.umik.tiktoksparkflow.service.RuntimeSettingsService runtimeSettingsService
    ) {
        TiktokSenderConfiguration configuration = new TiktokSenderConfiguration();
        configuration.setBulkSendInterval(Duration.ofMillis(1));
        FriendListMapper friendListMapper = mock(FriendListMapper.class);
        when(friendListMapper.load()).thenReturn(com.umik.tiktoksparkflow.entity.FriendListEntity.empty());
        return new SendTaskServiceImpl(
                configuration,
                new SendReceiptParser(),
                new SingleUserProfileGuard(),
                browserRuntime,
                selectionMapper,
                friendListMapper,
                taskMapper,
                historyMapper,
                runtimeSettingsService,
                mock(LoginExpiryEmailNotifier.class));
    }

    private SendTaskVO awaitTerminal(SendTaskService service, String taskId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            SendTaskVO task = service.get(taskId);
            if (task.status() == SendTaskStatus.COMPLETED
                    || task.status() == SendTaskStatus.PARTIAL_FAILED
                    || task.status() == SendTaskStatus.FAILED
                    || task.status() == SendTaskStatus.CANCELLED) {
                return task;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待任务完成时被中断", error);
            }
        }
        throw new AssertionError("发送任务未在测试时限内结束");
    }
}

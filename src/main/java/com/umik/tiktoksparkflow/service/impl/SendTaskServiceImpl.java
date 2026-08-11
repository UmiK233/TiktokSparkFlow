package com.umik.tiktoksparkflow.service.impl;

import com.umik.tiktoksparkflow.browser.BrowserRuntime;
import com.umik.tiktoksparkflow.browser.TiktokCreatorClient;
import com.umik.tiktoksparkflow.browser.SingleUserProfileGuard;
import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.dto.BulkSendCommand;
import com.umik.tiktoksparkflow.entity.SendHistoryRecordEntity;
import com.umik.tiktoksparkflow.entity.SendTaskEntity;
import com.umik.tiktoksparkflow.entity.SendTaskItemEntity;
import com.umik.tiktoksparkflow.vo.SendReceiptVO;
import com.umik.tiktoksparkflow.vo.SendTaskItemVO;
import com.umik.tiktoksparkflow.enums.SendTaskItemStatus;
import com.umik.tiktoksparkflow.vo.SendTaskVO;
import com.umik.tiktoksparkflow.enums.SendTaskStatus;
import com.umik.tiktoksparkflow.exception.LoginRequiredException;
import com.umik.tiktoksparkflow.exception.RiskVerificationRequiredException;
import com.umik.tiktoksparkflow.mapper.FriendSelectionMapper;
import com.umik.tiktoksparkflow.mapper.FriendListMapper;
import com.umik.tiktoksparkflow.enums.ConversationType;
import com.umik.tiktoksparkflow.mapper.SendHistoryMapper;
import com.umik.tiktoksparkflow.service.SendTaskService;
import com.umik.tiktoksparkflow.service.RuntimeSettingsService;
import com.umik.tiktoksparkflow.service.LoginExpiryEmailNotifier;
import com.umik.tiktoksparkflow.mapper.SendTaskMapper;
import com.umik.tiktoksparkflow.utils.SendReceiptParser;
import com.umik.tiktoksparkflow.utils.Gmt8Time;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class SendTaskServiceImpl implements SendTaskService, DisposableBean {
    private static final DateTimeFormatter TASK_ID_TIME =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final TiktokSenderConfiguration configuration;
    private final SendReceiptParser receiptParser;
    private final SingleUserProfileGuard profileGuard;
    private final BrowserRuntime browserRuntime;
    private final FriendSelectionMapper selectionMapper;
    private final FriendListMapper friendListMapper;
    private final SendTaskMapper taskMapper;
    private final SendHistoryMapper historyMapper;
    private final RuntimeSettingsService runtimeSettingsService;
    private final LoginExpiryEmailNotifier loginExpiryEmailNotifier;
    private final ZoneId zoneId;
    private final Map<String, TaskState> tasks = new LinkedHashMap<>();
    private final ExecutorService taskThread = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "tiktok-send-task");
        thread.setDaemon(false);
        return thread;
    });

    public SendTaskServiceImpl(
            TiktokSenderConfiguration configuration,
            SendReceiptParser receiptParser,
            SingleUserProfileGuard profileGuard,
            BrowserRuntime browserRuntime,
            FriendSelectionMapper selectionMapper,
            FriendListMapper friendListMapper,
            SendTaskMapper taskMapper,
            SendHistoryMapper historyMapper,
            RuntimeSettingsService runtimeSettingsService,
            LoginExpiryEmailNotifier loginExpiryEmailNotifier
    ) {
        this.configuration = configuration;
        this.receiptParser = receiptParser;
        this.profileGuard = profileGuard;
        this.browserRuntime = browserRuntime;
        this.selectionMapper = selectionMapper;
        this.friendListMapper = friendListMapper;
        this.taskMapper = taskMapper;
        this.historyMapper = historyMapper;
        this.runtimeSettingsService = runtimeSettingsService;
        this.loginExpiryEmailNotifier = loginExpiryEmailNotifier;
        this.zoneId = ZoneId.of(configuration.getTimeZone());
    }

    @PostConstruct
    public void restoreTasks() {
        for (SendTaskEntity entity : taskMapper.loadAll()) {
            TaskState state = TaskState.from(entity);
            if (isActive(state.status)) {
                state.status = SendTaskStatus.PENDING;
                state.currentTarget = "";
                state.detail = "服务重启后继续执行未完成任务";
                state.cancelRequested = false;
            }
            synchronized (tasks) {
                tasks.put(state.taskId, state);
            }
            persist(state);
            if (state.status == SendTaskStatus.PENDING) {
                submit(state.taskId);
            }
        }
    }

    @Override
    public SendTaskVO create(BulkSendCommand command) {
        List<String> targets = orderByConversationType(selectionMapper.load().selectedFriends());
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("尚未配置需要处理的好友");
        }
        return create(command, targets, "任务已经创建，等待执行");
    }

    @Override
    public SendTaskVO get(String taskId) {
        return snapshot(requireTask(taskId));
    }

    @Override
    public List<SendTaskVO> list() {
        synchronized (tasks) {
            return tasks.values().stream()
                    .map(this::snapshot)
                    .sorted(Comparator.comparing(SendTaskVO::createdAt).reversed())
                    .toList();
        }
    }

    @Override
    public SendTaskVO cancel(String taskId) {
        TaskState state = requireTask(taskId);
        synchronized (state) {
            if (isTerminal(state.status)) {
                return state.snapshot();
            }
            state.cancelRequested = true;
            state.detail = "已请求取消，将在当前好友处理完成后停止";
            persist(state);
            return state.snapshot();
        }
    }

    @Override
    public SendTaskVO retryFailed(String taskId) {
        SendTaskVO source = get(taskId);
        List<String> failedTargets = source.results().stream()
                .filter(result -> result.status() == SendTaskItemStatus.FAILED)
                .map(SendTaskItemVO::targetNickname)
                .distinct()
                .toList();
        if (failedTargets.isEmpty()) {
            throw new IllegalArgumentException("任务中没有可以重试的失败好友");
        }
        return create(
                new BulkSendCommand(source.message(), source.sendMessage()),
                failedTargets,
                "失败好友重试任务已经创建");
    }

    private SendTaskVO create(
            BulkSendCommand command,
            List<String> targets,
            String detail
    ) {
        String now = now();
        String taskId = TASK_ID_TIME.format(OffsetDateTime.now(zoneId))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
        TaskState state = TaskState.create(taskId, command, targets, now, detail);
        synchronized (tasks) {
            tasks.put(taskId, state);
        }
        persist(state);
        SendTaskVO created = snapshot(state);
        submit(taskId);
        return created;
    }

    private void submit(String taskId) {
        taskThread.submit(() -> runTask(taskId));
    }

    private void runTask(String taskId) {
        TaskState state = requireTask(taskId);
        markRunning(state);

        while (true) {
            String target;
            synchronized (state) {
                if (state.cancelRequested) {
                    finishCancelled(state);
                    return;
                }
                if (state.nextTargetIndex >= state.targets.size()) {
                    finishNormally(state);
                    return;
                }
                target = state.targets.get(state.nextTargetIndex);
                state.currentTarget = target;
                state.detail = "正在处理好友：" + target;
                persist(state);
            }

            if (state.sendMessage
                    && !runtimeSettingsService.get().allowRepeatedSend()
                    && historyMapper.wasSentToday(target)) {
                completeTarget(state, new SendTaskItemVO(
                        target,
                        SendTaskItemStatus.SKIPPED_ALREADY_SENT,
                        "今日已经确认发送成功，本次自动跳过",
                        now()));
                waitBetweenTargets(state);
                continue;
            }

            try {
                SendReceiptVO receipt = executeTargetWithLoginRecovery(state, target);
                if (state.sendMessage) {
                    String sentAt = now();
                    historyMapper.record(new SendHistoryRecordEntity(
                            state.taskId, target, state.message, sentAt));
                    completeTarget(state, new SendTaskItemVO(
                            target,
                            SendTaskItemStatus.SENT,
                            "服务端回执和己方消息气泡均已确认，状态码："
                                    + receipt.httpStatus(),
                            sentAt));
                } else {
                    completeTarget(state, new SendTaskItemVO(
                            target,
                            SendTaskItemStatus.SELECTED,
                            "仅选择模式，已找到并选中好友，没有发送消息",
                            now()));
                }
            } catch (LoginRequiredException error) {
                failCurrentAndRemainingForLogin(state, error.getMessage());
                return;
            } catch (RuntimeException error) {
                String detail = error.getMessage() == null || error.getMessage().isBlank()
                        ? "处理失败，未返回具体原因"
                        : error.getMessage();
                completeTarget(state, new SendTaskItemVO(
                        target, SendTaskItemStatus.FAILED, detail, now()));
            }
            waitBetweenTargets(state);
        }
    }

    private SendReceiptVO executeTargetWithLoginRecovery(TaskState state, String target) {
        try {
            return executeTarget(target, state.message, state.sendMessage);
        } catch (RiskVerificationRequiredException error) {
            markWaitingForVerification(state);
            if (!waitForRiskVerification()) {
                throw new RiskVerificationRequiredException(
                        "身份验证未在 " + configuration.getLoginTimeout().toMinutes() + " 分钟内完成");
            }
            markRunningAfterVerification(state);
            return executeTarget(target, state.message, state.sendMessage);
        } catch (LoginRequiredException error) {
            loginExpiryEmailNotifier.notifyLoginExpired();
            markWaitingForLogin(state);
            boolean restored = waitForLogin();
            if (!restored) {
                throw new LoginRequiredException(
                        "登录失效，并且未在 " + configuration.getLoginTimeout().toMinutes()
                                + " 分钟内恢复登录");
            }
            markRunningAfterLogin(state);
            loginExpiryEmailNotifier.markLoginRecovered();
            return executeTarget(target, state.message, state.sendMessage);
        }
    }

    private SendReceiptVO executeTarget(String target, String message, boolean sendMessage) {
        try (SingleUserProfileGuard.Lease ignored =
                     profileGuard.acquire(Duration.ofMinutes(1))) {
            return browserRuntime.execute(page -> {
                TiktokCreatorClient creator = new TiktokCreatorClient(
                        page, configuration, receiptParser);
                creator.requireAuthentication();
                creator.selectFriend(target, conversationTypeOf(target));
                return sendMessage ? creator.sendAndConfirm(message) : null;
            });
        }
    }

    private List<String> orderByConversationType(List<String> targets) {
        return targets.stream()
                .sorted(Comparator.comparingInt(target -> conversationTypeOrder(conversationTypeOf(target))))
                .toList();
    }

    private ConversationType conversationTypeOf(String target) {
        return friendListMapper.load().conversationTypes().getOrDefault(target, ConversationType.FRIEND);
    }

    private int conversationTypeOrder(ConversationType type) {
        return type == ConversationType.FRIEND ? 0 : 1;
    }

    private boolean waitForLogin() {
        try (SingleUserProfileGuard.Lease ignored =
                     profileGuard.acquire(Duration.ofMinutes(1))) {
            return browserRuntime.execute(page -> new TiktokCreatorClient(
                    page, configuration, receiptParser).waitForInteractiveLogin());
        }
    }

    private boolean waitForRiskVerification() {
        try (SingleUserProfileGuard.Lease ignored = profileGuard.acquire(Duration.ofMinutes(1))) {
            return browserRuntime.execute(page -> new TiktokCreatorClient(
                    page, configuration, receiptParser).waitForManualRiskVerification());
        }
    }

    private void completeTarget(TaskState state, SendTaskItemVO result) {
        synchronized (state) {
            state.results.add(result);
            state.nextTargetIndex++;
            state.currentTarget = "";
            state.detail = "已完成 " + state.results.size() + "/" + state.targets.size();
            persist(state);
        }
    }

    private void failCurrentAndRemainingForLogin(TaskState state, String detail) {
        synchronized (state) {
            while (state.nextTargetIndex < state.targets.size()) {
                String target = state.targets.get(state.nextTargetIndex++);
                state.results.add(new SendTaskItemVO(
                        target,
                        SendTaskItemStatus.FAILED,
                        detail,
                        now()));
            }
            state.status = SendTaskStatus.FAILED;
            state.currentTarget = "";
            state.finishedAt = now();
            state.detail = detail;
            persist(state);
        }
    }

    private void markRunning(TaskState state) {
        synchronized (state) {
            state.status = SendTaskStatus.RUNNING;
            if (state.startedAt == null || state.startedAt.isBlank()) {
                state.startedAt = now();
            }
            state.finishedAt = null;
            state.detail = "任务正在执行";
            persist(state);
        }
    }

    private void markWaitingForLogin(TaskState state) {
        synchronized (state) {
            state.status = SendTaskStatus.WAITING_FOR_LOGIN;
            state.detail = "检测到登录失效，正在等待扫码恢复登录";
            persist(state);
        }
    }

    private void markWaitingForVerification(TaskState state) {
        synchronized (state) {
            state.status = SendTaskStatus.WAITING_FOR_VERIFICATION;
            state.detail = "检测到抖音身份验证，请在浏览器实时画面中手动完成验证";
            persist(state);
        }
    }

    private void markRunningAfterLogin(TaskState state) {
        synchronized (state) {
            state.status = SendTaskStatus.RUNNING;
            state.detail = "登录已经恢复，继续执行任务";
            persist(state);
        }
    }

    private void markRunningAfterVerification(TaskState state) {
        synchronized (state) {
            state.status = SendTaskStatus.RUNNING;
            state.detail = "身份验证已完成，继续执行任务";
            persist(state);
        }
    }

    private void finishNormally(TaskState state) {
        synchronized (state) {
            long failed = state.results.stream()
                    .filter(result -> result.status() == SendTaskItemStatus.FAILED)
                    .count();
            state.status = failed == 0
                    ? SendTaskStatus.COMPLETED
                    : failed == state.targets.size()
                    ? SendTaskStatus.FAILED
                    : SendTaskStatus.PARTIAL_FAILED;
            state.currentTarget = "";
            state.finishedAt = now();
            state.detail = failed == 0 ? "任务执行完成" : "任务执行完成，存在失败好友";
            persist(state);
        }
    }

    private void finishCancelled(TaskState state) {
        synchronized (state) {
            state.status = SendTaskStatus.CANCELLED;
            state.currentTarget = "";
            state.finishedAt = now();
            state.detail = "任务已取消";
            persist(state);
        }
    }

    private void waitBetweenTargets(TaskState state) {
        synchronized (state) {
            if (state.cancelRequested || state.nextTargetIndex >= state.targets.size()) {
                return;
            }
        }
        try {
            Thread.sleep(configuration.getBulkSendInterval().toMillis());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            synchronized (state) {
                state.cancelRequested = true;
            }
        }
    }

    private TaskState requireTask(String taskId) {
        synchronized (tasks) {
            TaskState state = tasks.get(taskId);
            if (state == null) {
                throw new IllegalArgumentException("发送任务不存在：" + taskId);
            }
            return state;
        }
    }

    private SendTaskVO snapshot(TaskState state) {
        synchronized (state) {
            return state.snapshot();
        }
    }

    private void persist(TaskState state) {
        taskMapper.save(state.entity());
    }

    private String now() {
        return Gmt8Time.now();
    }

    private boolean isActive(SendTaskStatus status) {
        return status == SendTaskStatus.PENDING
                || status == SendTaskStatus.RUNNING
                || status == SendTaskStatus.WAITING_FOR_LOGIN
                || status == SendTaskStatus.WAITING_FOR_VERIFICATION;
    }

    private boolean isTerminal(SendTaskStatus status) {
        return status == SendTaskStatus.COMPLETED
                || status == SendTaskStatus.PARTIAL_FAILED
                || status == SendTaskStatus.FAILED
                || status == SendTaskStatus.CANCELLED;
    }

    @Override
    public void destroy() {
        taskThread.shutdownNow();
    }

    private static final class TaskState {
        private String taskId;
        private SendTaskStatus status;
        private String message;
        private boolean sendMessage;
        private List<String> targets;
        private int nextTargetIndex;
        private List<SendTaskItemVO> results;
        private String createdAt;
        private String startedAt;
        private String finishedAt;
        private boolean cancelRequested;
        private String currentTarget;
        private String detail;

        private static TaskState create(
                String taskId,
                BulkSendCommand command,
                List<String> targets,
                String createdAt,
                String detail
        ) {
            TaskState state = new TaskState();
            state.taskId = taskId;
            state.status = SendTaskStatus.PENDING;
            state.message = command.message();
            state.sendMessage = command.sendMessage();
            state.targets = List.copyOf(targets);
            state.results = new ArrayList<>();
            state.createdAt = createdAt;
            state.currentTarget = "";
            state.detail = detail;
            return state;
        }

        private static TaskState from(SendTaskEntity entity) {
            TaskState state = new TaskState();
            state.taskId = entity.taskId();
            state.status = entity.status();
            state.message = entity.message();
            state.sendMessage = entity.sendMessage();
            state.targets = List.copyOf(entity.targets());
            state.nextTargetIndex = entity.nextTargetIndex();
            state.results = entity.results().stream()
                    .map(result -> new SendTaskItemVO(
                            result.targetNickname(),
                            result.status(),
                            result.detail(),
                            Gmt8Time.normalize(result.completedAt())))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            state.createdAt = Gmt8Time.normalize(entity.createdAt());
            state.startedAt = Gmt8Time.normalize(entity.startedAt());
            state.finishedAt = Gmt8Time.normalize(entity.finishedAt());
            state.cancelRequested = entity.cancelRequested();
            state.currentTarget = entity.currentTarget();
            state.detail = entity.detail();
            return state;
        }

        private SendTaskEntity entity() {
            List<SendTaskItemEntity> persistedResults = results.stream()
                    .map(result -> new SendTaskItemEntity(
                            result.targetNickname(),
                            result.status(),
                            result.detail(),
                            result.completedAt()))
                    .toList();
            SendTaskVO view = snapshot();
            return new SendTaskEntity(
                    view.taskId(), view.status(), view.message(), view.sendMessage(),
                    view.targets(), view.nextTargetIndex(), view.total(), view.completed(),
                    view.succeeded(), view.failed(), view.skipped(), view.currentTarget(),
                    persistedResults, view.createdAt(), view.startedAt(), view.finishedAt(),
                    view.cancelRequested(), view.detail());
        }

        private SendTaskVO snapshot() {
            int succeeded = (int) results.stream().filter(result ->
                    result.status() == SendTaskItemStatus.SENT
                            || result.status() == SendTaskItemStatus.SELECTED).count();
            int failed = (int) results.stream()
                    .filter(result -> result.status() == SendTaskItemStatus.FAILED).count();
            int skipped = (int) results.stream().filter(result ->
                    result.status() == SendTaskItemStatus.SKIPPED_ALREADY_SENT).count();
            return new SendTaskVO(
                    taskId, status, message, sendMessage, targets, nextTargetIndex,
                    targets.size(), results.size(), succeeded, failed, skipped,
                    currentTarget, results, createdAt, startedAt, finishedAt,
                    cancelRequested, detail);
        }
    }
}

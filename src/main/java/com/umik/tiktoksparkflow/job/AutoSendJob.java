package com.umik.tiktoksparkflow.job;

import com.umik.tiktoksparkflow.dto.BulkSendCommand;
import com.umik.tiktoksparkflow.enums.SendTaskStatus;
import com.umik.tiktoksparkflow.service.RuntimeSettingsService;
import com.umik.tiktoksparkflow.service.SendTaskService;
import com.umik.tiktoksparkflow.utils.Gmt8Time;
import com.umik.tiktoksparkflow.vo.RuntimeSettingsVO;
import com.umik.tiktoksparkflow.vo.SendTaskVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class AutoSendJob {
    private final RuntimeSettingsService runtimeSettingsService;
    private final SendTaskService sendTaskService;
    private final AtomicBoolean triggering = new AtomicBoolean(false);
    /** 已触发的日期和计划分钟，防止 fixedRate 在同一分钟重复建任务。 */
    private volatile LocalDateTime lastTriggeredSchedule;

    public AutoSendJob(
            RuntimeSettingsService runtimeSettingsService,
            SendTaskService sendTaskService
    ) {
        this.runtimeSettingsService = runtimeSettingsService;
        this.sendTaskService = sendTaskService;
    }

    /** 每 15 秒读取一次本地运行配置，支持前端修改后立即生效。 */
    @Scheduled(fixedRate = 15_000)
    public synchronized void checkSchedule() {
        RuntimeSettingsVO settings = runtimeSettingsService.get();
        if (!settings.autoSendEnabled()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(Gmt8Time.ZONE);
        LocalTime scheduled = LocalTime.parse(settings.scheduleTime());
        LocalDateTime scheduledAt = LocalDateTime.of(now.toLocalDate(), scheduled);
        if (now.getHour() != scheduled.getHour() || now.getMinute() != scheduled.getMinute()
                || scheduledAt.equals(lastTriggeredSchedule)) {
            return;
        }
        lastTriggeredSchedule = scheduledAt;
        runDaily(settings);
    }

    /** 供测试和手动触发使用，始终使用当前已保存的运行配置。 */
    public void runDaily() {
        runDaily(runtimeSettingsService.get());
    }

    private void runDaily(RuntimeSettingsVO settings) {
        if (!settings.autoSendEnabled()) {
            return;
        }
        if (!triggering.compareAndSet(false, true)) {
            log.warn("自动发送任务仍在创建中，本次触发已跳过");
            return;
        }
        try {
            if (hasActiveTask()) {
                log.warn("存在未完成的发送任务，本次自动触发已跳过");
                return;
            }
            SendTaskVO task = sendTaskService.create(
                    new BulkSendCommand(settings.message(), settings.sendMessage()));
            log.info("自动发送任务已创建，任务编号：{}，真实发送：{}",
                    task.taskId(), settings.sendMessage());
        } catch (IllegalArgumentException error) {
            log.warn("自动发送任务未创建：{}", error.getMessage());
        } catch (RuntimeException error) {
            log.error("自动发送任务创建失败", error);
        } finally {
            triggering.set(false);
        }
    }

    private boolean hasActiveTask() {
        return sendTaskService.list().stream()
                .map(SendTaskVO::status)
                .anyMatch(status -> status == SendTaskStatus.PENDING
                        || status == SendTaskStatus.RUNNING
                        || status == SendTaskStatus.WAITING_FOR_LOGIN
                        || status == SendTaskStatus.WAITING_FOR_VERIFICATION);
    }
}

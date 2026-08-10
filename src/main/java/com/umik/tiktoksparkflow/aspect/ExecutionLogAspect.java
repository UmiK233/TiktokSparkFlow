package com.umik.tiktoksparkflow.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import java.util.UUID;

/**
 * 仅记录标注 {@link OperationLog} 的业务操作。
 * 不记录底层数据读写、轮询和浏览器细节，避免日志淹没真正的业务轨迹。
 */
@Aspect
@Component
public class ExecutionLogAspect {
    private static final Logger log = LoggerFactory.getLogger(ExecutionLogAspect.class);
    @Around("@annotation(operationLog)")
    public Object recordExecution(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        long startedAt = System.nanoTime();

        log.info("[{}] 开始{}{}", traceId, operationLog.value(),
                summarizeArguments(signature.getParameterNames(), joinPoint.getArgs()));
        try {
            Object result = joinPoint.proceed();
            log.info("[{}] 完成{}，耗时={}ms", traceId, operationLog.value(), elapsedMillis(startedAt));
            return result;
        } catch (Throwable error) {
            log.warn("[{}] {}失败，耗时={}ms，异常={}：{}", traceId, operationLog.value(),
                    elapsedMillis(startedAt), error.getClass().getSimpleName(),
                    summarizeError(error.getMessage()));
            throw error;
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private String summarizeArguments(String[] parameterNames, Object[] arguments) {
        if (arguments == null || arguments.length == 0) {
            return "";
        }
        if (arguments.length == 1 && arguments[0] != null && arguments[0].getClass().isRecord()) {
            return "，" + summarizeRecord(arguments[0]);
        }
        StringBuilder summary = new StringBuilder("，");
        for (int index = 0; index < arguments.length; index++) {
            if (index > 0) {
                summary.append("；");
            }
            String parameterName = parameterNames != null && index < parameterNames.length
                    ? parameterNames[index] : "参数" + (index + 1);
            summary.append(translateName(parameterName)).append('=').append(summarizeValue(arguments[index]));
        }
        return summary.toString();
    }

    private String summarizeValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof CharSequence text) {
            return text.toString().isBlank() ? "空" : "“" + abbreviate(text.toString(), 40) + "”";
        }
        if (value instanceof Number || value instanceof Boolean || value.getClass().isEnum()) {
            return String.valueOf(value);
        }
        if (value instanceof Collection<?> collection) {
            return "数量=" + collection.size();
        }
        if (value.getClass().isArray()) {
            return value.getClass().getComponentType().getSimpleName() + "数组";
        }
        if (value.getClass().isRecord()) {
            return summarizeRecord(value);
        }
        return value.getClass().getSimpleName();
    }

    private String summarizeRecord(Object record) {
        StringBuilder summary = new StringBuilder();
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            if (summary.length() > 0) summary.append('，');
            try {
                Object value = component.getAccessor().invoke(record);
                summary.append(translateName(component.getName())).append('=');
                if (value instanceof Collection<?> collection) {
                    summary.append("数量").append(collection.size());
                } else {
                    summary.append(summarizeValue(value));
                }
            } catch (ReflectiveOperationException ignored) {
                summary.append(translateName(component.getName())).append("=读取失败");
            }
        }
        return summary.toString();
    }

    private String translateName(String name) {
        return switch (name) {
            case "targetNickname" -> "好友";
            case "selectedFriends" -> "已选好友";
            case "sendMessage" -> "真实发送";
            case "scheduleTime" -> "执行时间";
            case "autoSendEnabled" -> "自动发送";
            case "taskId" -> "任务编号";
            case "date" -> "日期";
            case "message" -> "消息";
            default -> name;
        };
    }

    private String abbreviate(String text, int maxLength) {
        String normalized = text.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "…";
    }

    private String summarizeError(String message) {
        if (message == null || message.isBlank()) {
            return "无异常详情";
        }
        String normalized = message.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() <= 240 ? normalized : normalized.substring(0, 240) + "…";
    }
}

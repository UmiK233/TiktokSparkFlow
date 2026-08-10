package com.umik.tiktoksparkflow.exception;

import com.umik.tiktoksparkflow.common.Result;
import com.umik.tiktoksparkflow.enums.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public Result<Void> businessException(BusinessException error) {
        return Result.fail(error.getResultCode(), messageOf(error, error.getResultCode()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> badRequest(IllegalArgumentException error) {
        return Result.fail(ResultCode.BAD_REQUEST, messageOf(error, ResultCode.BAD_REQUEST));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> unreadableRequest(HttpMessageNotReadableException error) {
        return Result.fail(ResultCode.BAD_REQUEST, "请求 JSON 格式错误或字段类型不正确");
    }

    @ExceptionHandler(IllegalStateException.class)
    public Result<Void> illegalState(IllegalStateException error) {
        log.warn("业务状态异常", error);
        return Result.fail(ResultCode.INTERNAL_ERROR, messageOf(error, ResultCode.INTERNAL_ERROR));
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> unexpectedError(Exception error) {
        log.error("服务器处理请求时发生异常", error);
        return Result.fail(ResultCode.INTERNAL_ERROR, "服务器处理请求时发生异常");
    }

    private String messageOf(Exception error, ResultCode fallback) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? fallback.getMessage()
                : error.getMessage();
    }
}

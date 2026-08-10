package com.umik.tiktoksparkflow.common;

import com.umik.tiktoksparkflow.enums.ResultCode;

public record Result<T>(
        int code,
        String message,
        T data
) {
    public static <T> Result<T> success(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    public static Result<Void> fail(ResultCode resultCode, String message) {
        return new Result<>(resultCode.getCode(), message, null);
    }
}

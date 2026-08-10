package com.umik.tiktoksparkflow.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public enum ResultCode {
    SUCCESS(200, "操作成功"),

    // 通用错误
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "登录状态已失效"),
    FORBIDDEN(403, "权限不足，无法访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "资源冲突"),
    INTERNAL_ERROR(500, "系统错误");

    private final int code;
    private final String message;
}

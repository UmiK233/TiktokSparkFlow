package com.umik.tiktoksparkflow.exception;

import com.umik.tiktoksparkflow.enums.ResultCode;

public class LoginRequiredException extends BusinessException {
    public LoginRequiredException(String message) {
        super(ResultCode.UNAUTHORIZED, message);
    }
}

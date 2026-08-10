package com.umik.tiktoksparkflow.exception;

import com.umik.tiktoksparkflow.enums.ResultCode;

public class ProfileBusyException extends BusinessException {
    public ProfileBusyException(String message) {
        super(ResultCode.CONFLICT, message);
    }
}

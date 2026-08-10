package com.umik.tiktoksparkflow.exception;

import com.umik.tiktoksparkflow.enums.ResultCode;

/** 抖音要求账号持有人在浏览器中完成身份验证。 */
public class RiskVerificationRequiredException extends BusinessException {
    public RiskVerificationRequiredException(String message) {
        super(ResultCode.UNAUTHORIZED, message);
    }
}

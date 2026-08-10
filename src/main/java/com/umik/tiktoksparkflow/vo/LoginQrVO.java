package com.umik.tiktoksparkflow.vo;

import com.umik.tiktoksparkflow.enums.LoginStatus;

/** 当前浏览器登录页面中的二维码快照。 */
public record LoginQrVO(
        LoginStatus status,
        String imageData,
        String detail
) {
    public LoginQrVO {
        imageData = imageData == null ? "" : imageData;
        detail = detail == null ? "" : detail;
    }

    public boolean loggedIn() {
        return status == LoginStatus.LOGGED_IN;
    }
}

package com.umik.tiktoksparkflow.vo;

import com.umik.tiktoksparkflow.enums.LoginStatus;

public record LoginStatusVO(
        LoginStatus status,
        String profilePath,
        String detail
) {
    public boolean loggedIn() {
        return status == LoginStatus.LOGGED_IN;
    }
}

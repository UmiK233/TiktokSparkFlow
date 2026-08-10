package com.umik.tiktoksparkflow.service;

import com.umik.tiktoksparkflow.vo.LoginStatusVO;
import com.umik.tiktoksparkflow.vo.LoginQrVO;

public interface LoginService {
    LoginStatusVO status();
    LoginQrVO qr();
    LoginStatusVO login();
    LoginStatusVO logout();
}

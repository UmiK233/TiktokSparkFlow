package com.umik.tiktoksparkflow.controller;

import com.umik.tiktoksparkflow.common.Result;
import com.umik.tiktoksparkflow.aspect.OperationLog;
import com.umik.tiktoksparkflow.service.LoginService;
import com.umik.tiktoksparkflow.vo.LoginStatusVO;
import com.umik.tiktoksparkflow.vo.LoginQrVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
public class LoginController {
    private final LoginService loginService;

    public LoginController(LoginService loginService) {
        this.loginService = loginService;
    }

    @GetMapping("/status")
    @OperationLog("检查抖音登录状态")
    public Result<LoginStatusVO> status() {
        return Result.success("登录状态查询成功", loginService.status());
    }

    @GetMapping("/qr")
    @OperationLog("获取登录二维码")
    public Result<LoginQrVO> qr() {
        return Result.success("登录二维码获取成功", loginService.qr());
    }

    @PostMapping("/login")
    @OperationLog("准备扫码登录")
    public Result<LoginStatusVO> login() {
        return Result.success("登录二维码已准备，请轮询二维码接口", loginService.login());
    }

    @PostMapping("/logout")
    @OperationLog("退出抖音登录")
    public Result<LoginStatusVO> logout() {
        return Result.success("已退出登录", loginService.logout());
    }
}

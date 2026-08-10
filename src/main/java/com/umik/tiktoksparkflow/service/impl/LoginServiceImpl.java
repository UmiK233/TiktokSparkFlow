package com.umik.tiktoksparkflow.service.impl;

import com.umik.tiktoksparkflow.browser.BrowserRuntime;
import com.umik.tiktoksparkflow.browser.TiktokCreatorClient;
import com.umik.tiktoksparkflow.browser.SingleUserProfileGuard;
import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.enums.LoginStatus;
import com.umik.tiktoksparkflow.service.LoginService;
import com.umik.tiktoksparkflow.service.LoginExpiryEmailNotifier;
import com.umik.tiktoksparkflow.utils.SendReceiptParser;
import com.microsoft.playwright.Page;
import com.umik.tiktoksparkflow.vo.LoginStatusVO;
import com.umik.tiktoksparkflow.vo.LoginQrVO;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.io.IOException;
import java.util.Comparator;

@Service
public class LoginServiceImpl implements LoginService {
    private final TiktokSenderConfiguration configuration;
    private final SendReceiptParser receiptParser;
    private final SingleUserProfileGuard profileGuard;
    private final BrowserRuntime browserRuntime;
    private final LoginExpiryEmailNotifier loginExpiryEmailNotifier;

    public LoginServiceImpl(
            TiktokSenderConfiguration configuration,
            SendReceiptParser receiptParser,
            SingleUserProfileGuard profileGuard,
            BrowserRuntime browserRuntime,
            LoginExpiryEmailNotifier loginExpiryEmailNotifier
    ) {
        this.configuration = configuration;
        this.receiptParser = receiptParser;
        this.profileGuard = profileGuard;
        this.browserRuntime = browserRuntime;
        this.loginExpiryEmailNotifier = loginExpiryEmailNotifier;
    }

    @Override
    public LoginStatusVO status() {
        if (!Files.isDirectory(configuration.profilePath())) {
            return result(LoginStatus.NOT_INITIALIZED, "本地浏览器资料不存在，请扫码登录");
        }
        if (profileGuard.isBusy()) {
            return result(LoginStatus.BUSY, "浏览器资料正在被其他任务使用");
        }
        try (SingleUserProfileGuard.Lease ignored = profileGuard.acquire()) {
            boolean loggedIn = browserRuntime.execute(page -> client(page).checkAuthentication());
            if (loggedIn) {
                loginExpiryEmailNotifier.markLoginRecovered();
                return result(LoginStatus.LOGGED_IN, "浏览器页面已确认登录有效");
            }
            loginExpiryEmailNotifier.notifyLoginExpired();
            return result(LoginStatus.LOGIN_REQUIRED, "浏览器页面显示账号尚未登录");
        }
    }

    @Override
    public LoginQrVO qr() {
        // BrowserRuntime 会把全部 Playwright 操作串行放到同一线程执行。
        // 二维码读取不再抢占 Profile 锁，避免状态检查或发送任务短暂持锁时
        // 连续返回 BUSY，前端只需等待当前页面操作结束即可获取二维码。
        return browserRuntime.execute(page -> client(page).captureLoginQr());
    }

    @Override
    public LoginStatusVO login() {
        LoginQrVO qr = qr();
        return result(qr.status(), qr.detail());
    }

    @Override
    public LoginStatusVO logout() {
        try (SingleUserProfileGuard.Lease ignored = profileGuard.acquire()) {
            browserRuntime.closeCurrentSession();
            deleteProfileDirectory();
        }
        return result(LoginStatus.NOT_INITIALIZED, "已退出登录并清除本地浏览器资料，请重新扫码登录");
    }

    private void deleteProfileDirectory() {
        if (!Files.exists(configuration.profilePath())) {
            return;
        }
        try (var paths = Files.walk(configuration.profilePath())) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    throw new IllegalStateException("清除浏览器资料失败：" + path, error);
                }
            });
        } catch (IOException error) {
            throw new IllegalStateException("读取浏览器资料目录失败：" + configuration.profilePath(), error);
        }
    }

    private TiktokCreatorClient client(Page page) {
        return new TiktokCreatorClient(page, configuration, receiptParser);
    }

    private LoginStatusVO result(LoginStatus status, String detail) {
        return new LoginStatusVO(status, configuration.profilePath().toString(), detail);
    }
}

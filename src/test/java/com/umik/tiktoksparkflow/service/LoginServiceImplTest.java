package com.umik.tiktoksparkflow.service;

import com.umik.tiktoksparkflow.browser.BrowserRuntime;
import com.umik.tiktoksparkflow.browser.SingleUserProfileGuard;
import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.enums.LoginStatus;
import com.umik.tiktoksparkflow.vo.LoginStatusVO;
import com.umik.tiktoksparkflow.vo.LoginQrVO;
import com.umik.tiktoksparkflow.service.impl.LoginServiceImpl;
import com.umik.tiktoksparkflow.utils.SendReceiptParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginServiceImplTest {
    @TempDir
    Path tempDir;

    @Test
    void statusUsesBrowserToConfirmLoggedIn() throws Exception {
        BrowserRuntime browserRuntime = mock(BrowserRuntime.class);
        when(browserRuntime.<Boolean>execute(any())).thenReturn(true);
        Files.createDirectories(tempDir.resolve("浏览器资料"));
        LoginService service = service(browserRuntime, new SingleUserProfileGuard());

        LoginStatusVO result = service.status();

        assertEquals(LoginStatus.LOGGED_IN, result.status());
        assertTrue(result.loggedIn());
        verify(browserRuntime).execute(any());
    }

    @Test
    void statusUsesBrowserToConfirmLoginRequired() throws Exception {
        BrowserRuntime browserRuntime = mock(BrowserRuntime.class);
        when(browserRuntime.<Boolean>execute(any())).thenReturn(false);
        Files.createDirectories(tempDir.resolve("浏览器资料"));
        LoginService service = service(browserRuntime, new SingleUserProfileGuard());

        LoginStatusVO result = service.status();

        assertEquals(LoginStatus.LOGIN_REQUIRED, result.status());
        assertFalse(result.loggedIn());
        verify(browserRuntime).execute(any());
    }

    @Test
    void statusReportsBusyWithoutCheckingBrowser() throws Exception {
        BrowserRuntime browserRuntime = mock(BrowserRuntime.class);
        SingleUserProfileGuard profileGuard = new SingleUserProfileGuard();
        Files.createDirectories(tempDir.resolve("浏览器资料"));
        LoginService service = service(browserRuntime, profileGuard);

        try (SingleUserProfileGuard.Lease ignored = profileGuard.acquire()) {
            LoginStatusVO result = service.status();
            assertEquals(LoginStatus.BUSY, result.status());
        }
    }

    @Test
    void profileMissingReportsNotInitializedWithoutOpeningBrowser() {
        BrowserRuntime browserRuntime = mock(BrowserRuntime.class);
        LoginService service = service(browserRuntime, new SingleUserProfileGuard());

        LoginStatusVO result = service.status();

        assertEquals(LoginStatus.NOT_INITIALIZED, result.status());
    }

    @Test
    void qrReturnsCurrentBrowserQrSnapshot() {
        BrowserRuntime browserRuntime = mock(BrowserRuntime.class);
        LoginQrVO expected = new LoginQrVO(
                LoginStatus.LOGIN_REQUIRED,
                "data:image/png;base64,test",
                "请使用抖音 App 扫码登录");
        when(browserRuntime.<LoginQrVO>execute(any())).thenReturn(expected);
        LoginService service = service(browserRuntime, new SingleUserProfileGuard());

        LoginQrVO result = service.qr();

        assertEquals(expected, result);
        verify(browserRuntime).execute(any());
    }

    @Test
    void qrDoesNotRejectWhenProfileLockIsHeld() {
        BrowserRuntime browserRuntime = mock(BrowserRuntime.class);
        when(browserRuntime.<LoginQrVO>execute(any())).thenReturn(new LoginQrVO(
                LoginStatus.LOGIN_REQUIRED, "data:image/png;base64,test", "请使用抖音 App 扫码登录"));
        SingleUserProfileGuard profileGuard = new SingleUserProfileGuard();
        LoginService service = service(browserRuntime, profileGuard);

        try (SingleUserProfileGuard.Lease ignored = profileGuard.acquire()) {
            LoginQrVO result = service.qr();
            assertEquals(LoginStatus.LOGIN_REQUIRED, result.status());
        }
    }

    @Test
    void loginDoesNotBlockAndReturnsCurrentQrStatus() {
        BrowserRuntime browserRuntime = mock(BrowserRuntime.class);
        when(browserRuntime.<LoginQrVO>execute(any())).thenReturn(new LoginQrVO(
                LoginStatus.LOGIN_REQUIRED, "data:image/png;base64,test", "请使用抖音 App 扫码登录"));
        LoginService service = service(browserRuntime, new SingleUserProfileGuard());

        LoginStatusVO result = service.login();

        assertEquals(LoginStatus.LOGIN_REQUIRED, result.status());
        assertEquals("请使用抖音 App 扫码登录", result.detail());
        verify(browserRuntime).execute(any());
    }

    @Test
    void logoutClosesBrowserAndDeletesProfile() throws Exception {
        BrowserRuntime browserRuntime = mock(BrowserRuntime.class);
        Path profile = tempDir.resolve("浏览器资料");
        Files.createDirectories(profile);
        Files.writeString(profile.resolve("Cookies"), "session");
        LoginService service = service(browserRuntime, new SingleUserProfileGuard());

        LoginStatusVO result = service.logout();

        assertEquals(LoginStatus.NOT_INITIALIZED, result.status());
        assertFalse(Files.exists(profile));
        verify(browserRuntime).closeCurrentSession();
    }

    private LoginService service(
            BrowserRuntime browserRuntime,
            SingleUserProfileGuard profileGuard
    ) {
        TiktokSenderConfiguration configuration = new TiktokSenderConfiguration();
        configuration.setProfileDir(tempDir.resolve("浏览器资料").toString());
        return new LoginServiceImpl(
                configuration,
                new SendReceiptParser(),
                profileGuard,
                browserRuntime,
                mock(LoginExpiryEmailNotifier.class)
        );
    }
}

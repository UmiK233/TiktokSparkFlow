package com.umik.tiktoksparkflow.browser;

import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public final class BrowserSession implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(BrowserSession.class);
    private final Playwright playwright;
    private final BrowserContext context;
    private final Page page;
    private final boolean headless;

    private BrowserSession(Playwright playwright, BrowserContext context, Page page, boolean headless) {
        this.playwright = playwright;
        this.context = context;
        this.page = page;
        this.headless = headless;
    }

    public Page page() { return page; }

    public boolean isUsable() {
        return !page.isClosed();
    }

    public boolean isHeadless() { return headless; }

    public static BrowserSession open(TiktokSenderConfiguration properties, boolean headless) {
        Playwright playwright = Playwright.create();
        try {
            return launch(playwright, properties, headless);
        } catch (RuntimeException error) {
            if (isStaleRemoteProfileLock(error) && StaleChromiumProfileLock.clear(properties.profilePath())) {
                log.warn("检测到旧容器遗留的 Chromium Profile 锁，已清除临时锁文件并重试启动浏览器");
                try {
                    return launch(playwright, properties, headless);
                } catch (RuntimeException retryError) {
                    error = retryError;
                }
            }
            playwright.close();
            String detail = error.getMessage() == null ? "" : error.getMessage();
            if (detail.contains("Missing X server") || detail.contains("$DISPLAY")) {
                throw new IllegalStateException(
                        "当前使用有头浏览器，但 Linux 环境未启动图形显示服务。"
                                + "请使用 xvfb-run 启动应用，例如："
                                + "xvfb-run -a -s \"-screen 0 1600x1000x24\" java -jar app.jar。"
                                + "不要改为无头模式，否则可能导致抖音会话被拒绝。",
                        error);
            }
            throw new IllegalStateException(
                    "无法打开 Chromium 浏览器资料：" + properties.profilePath()
                            + "。请确认没有其他 Chromium 进程正在使用该目录。原因："
                            + detail, error);
        }
    }

    private static BrowserSession launch(Playwright playwright, TiktokSenderConfiguration properties, boolean headless) {
        BrowserContext context = playwright.chromium().launchPersistentContext(
                properties.profilePath(),
                new BrowserType.LaunchPersistentContextOptions()
                        .setHeadless(headless)
                        .setViewportSize(1600, 1000)
                        .setArgs(List.of("--disable-dev-shm-usage", "--no-sandbox"))
        );
        context.setDefaultTimeout(properties.getDefaultTimeout().toMillis());
        context.setDefaultNavigationTimeout(properties.getNavigationTimeout().toMillis());
        Page page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
        page.setViewportSize(1600, 1000);
        return new BrowserSession(playwright, context, page, headless);
    }

    private static boolean isStaleRemoteProfileLock(RuntimeException error) {
        String detail = error.getMessage() == null ? "" : error.getMessage();
        return detail.contains("The profile appears to be in use by another Chromium process")
                && detail.contains("on another computer");
    }

    @Override
    public void close() {
        try {
            context.close();
        } finally {
            playwright.close();
        }
    }
}

package com.umik.tiktoksparkflow.browser;

import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.config.BrowserSettingsChangedEvent;
import com.umik.tiktoksparkflow.mapper.RuntimeSettingsMapper;
import com.microsoft.playwright.Page;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * 持有单用户的长期浏览器会话，并保证全部 Playwright 操作都在同一个线程执行。
 * 状态检查、扫码登录和消息发送共享同一个浏览器环境及持久化资料。
 */
@Component
public class BrowserRuntime implements DisposableBean {
    private final TiktokSenderConfiguration configuration;
    private final RuntimeSettingsMapper runtimeSettingsMapper;
    private final ExecutorService browserThread = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "tiktok-browser-runtime");
        thread.setDaemon(false);
        return thread;
    });
    private BrowserSession session;
    /** Accessed only by the browser executor. Keeps one session across a complete send task. */
    private int taskSessionLeases;
    /** Keeps Chromium alive while the user is scanning an interactive login QR code. */
    private boolean loginSessionRetained;

    public BrowserRuntime(TiktokSenderConfiguration configuration, RuntimeSettingsMapper runtimeSettingsMapper) {
        this.configuration = configuration;
        this.runtimeSettingsMapper = runtimeSettingsMapper;
    }

    public <T> T execute(Function<Page, T> operation) {
        Future<T> future = browserThread.submit(() -> {
            try {
                return operation.apply(currentPage());
            } finally {
                if (shouldCloseAfterOperation()) {
                    closeSession();
                }
            }
        });
        try {
            return future.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待浏览器操作时线程被中断", error);
        } catch (ExecutionException error) {
            Throwable cause = error.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("浏览器操作执行失败", cause);
        }
    }

    /** Starts or reuses the short-lived session needed for QR-code login. */
    public <T> T executeForLogin(Function<Page, T> operation) {
        Future<T> future = browserThread.submit(() -> {
            loginSessionRetained = true;
            return operation.apply(currentPage());
        });
        try {
            return future.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("获取登录二维码时线程被中断", error);
        } catch (ExecutionException error) {
            throw new IllegalStateException("获取登录二维码失败", error.getCause());
        }
    }

    /** Releases a QR-login session after login succeeds or the QR request fails. */
    public void releaseLoginSession() {
        waitFor(browserThread.submit(() -> {
            loginSessionRetained = false;
            if (shouldCloseAfterOperation()) {
                closeSession();
            }
        }), "释放登录浏览器会话失败");
    }

    /**
     * Retains the browser only for one send task when "open only for tasks" is enabled.
     * The lease does not open Chromium by itself; the first operation in the task does.
     */
    public TaskSession retainForTask() {
        waitFor(browserThread.submit(() -> taskSessionLeases++), "保留浏览器任务会话失败");
        return new TaskSession();
    }

    private boolean openBrowserOnlyForTasks() {
        return runtimeSettingsMapper.load()
                .map(settings -> Boolean.TRUE.equals(settings.openBrowserOnlyForTasks()))
                .orElse(false);
    }

    private void releaseTaskSession() {
        waitFor(browserThread.submit(() -> {
            taskSessionLeases = Math.max(0, taskSessionLeases - 1);
            if (shouldCloseAfterOperation()) {
                closeSession();
            }
        }), "释放浏览器任务会话失败");
    }

    private boolean shouldCloseAfterOperation() {
        return openBrowserOnlyForTasks() && taskSessionLeases == 0 && !loginSessionRetained;
    }

    private void waitFor(Future<?> future, String errorMessage) {
        try {
            future.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(errorMessage, error);
        } catch (ExecutionException error) {
            throw new IllegalStateException(errorMessage, error.getCause());
        }
    }

    public final class TaskSession implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                releaseTaskSession();
            }
        }
    }

    /** 关闭当前持久化浏览器会话，供退出登录等需要独占浏览器资料的操作调用。 */
    public void closeCurrentSession() {
        Future<?> future = browserThread.submit(() -> {
            loginSessionRetained = false;
            closeSession();
        });
        try {
            future.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("关闭浏览器会话时线程被中断", error);
        } catch (ExecutionException error) {
            throw new IllegalStateException("关闭浏览器会话失败", error.getCause());
        }
    }

    private Page currentPage() {
        boolean headless = runtimeSettingsMapper.load()
                .map(settings -> Boolean.TRUE.equals(settings.headless()))
                .orElse(false);
        if (session == null || !session.isUsable() || session.isHeadless() != headless) {
            closeSession();
            session = BrowserSession.open(configuration, headless);
        }
        return session.page();
    }

    @EventListener
    public void resetSessionAfterSettingsChange(BrowserSettingsChangedEvent ignored) {
        Future<?> future = browserThread.submit(() -> {
            loginSessionRetained = false;
            closeSession();
        });
        try {
            future.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException error) {
            throw new IllegalStateException("关闭旧浏览器会话失败", error.getCause());
        }
    }

    private void closeSession() {
        if (session == null) {
            return;
        }
        try {
            session.close();
        } catch (RuntimeException ignored) {
            // 浏览器已经异常退出时，关闭失败不应阻止后续重新创建会话。
        } finally {
            session = null;
        }
    }

    @Override
    public void destroy() {
        Future<?> future = browserThread.submit(() -> {
            loginSessionRetained = false;
            closeSession();
        });
        try {
            future.get();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException ignored) {
            // 应用退出时不再传播浏览器清理异常。
        } finally {
            browserThread.shutdownNow();
        }
    }
}

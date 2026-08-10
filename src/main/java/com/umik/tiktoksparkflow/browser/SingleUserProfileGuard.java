package com.umik.tiktoksparkflow.browser;

import com.umik.tiktoksparkflow.exception.ProfileBusyException;
import org.springframework.stereotype.Component;

import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

@Component
public class SingleUserProfileGuard {
    private final ReentrantLock lock = new ReentrantLock();

    public Lease acquire() {
        if (!lock.tryLock()) {
            throw new ProfileBusyException("单用户 Chromium 浏览器资料正在被其他任务使用");
        }
        return new Lease(lock);
    }

    public Lease acquire(Duration timeout) {
        try {
            if (!lock.tryLock(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ProfileBusyException(
                        "等待单用户 Chromium 浏览器资料超时：" + timeout.toSeconds() + " 秒");
            }
            return new Lease(lock);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new ProfileBusyException("等待单用户 Chromium 浏览器资料时线程被中断");
        }
    }

    public boolean isBusy() {
        return lock.isLocked();
    }

    public static final class Lease implements AutoCloseable {
        private final ReentrantLock lock;
        private boolean closed;

        private Lease(ReentrantLock lock) {
            this.lock = lock;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                lock.unlock();
            }
        }
    }
}

package com.umik.tiktoksparkflow.service;

import com.umik.tiktoksparkflow.vo.RuntimeSettingsVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Properties;

/** Sends at most one reminder during each continuous login-expiry period. */
@Service
public class LoginExpiryEmailNotifier {
    private static final Logger log = LoggerFactory.getLogger(LoginExpiryEmailNotifier.class);
    private final RuntimeSettingsService runtimeSettingsService;
    private final AtomicBoolean notified = new AtomicBoolean(false);

    public LoginExpiryEmailNotifier(
            RuntimeSettingsService runtimeSettingsService
    ) {
        this.runtimeSettingsService = runtimeSettingsService;
    }

    public void notifyLoginExpired() {
        RuntimeSettingsVO settings = runtimeSettingsService.get();
        String recipient = settings.loginExpiryNotificationEmail();
        if (recipient == null || recipient.isBlank()) {
            return;
        }
        if (settings.smtpHost().isBlank() || settings.smtpUsername().isBlank()
                || settings.smtpPassword().isBlank()) {
            log.warn("登录已失效，但 SMTP 配置不完整；未发送邮件提醒至 {}", recipient);
            return;
        }
        if (!notified.compareAndSet(false, true)) {
            return;
        }
        try {
            send(settings, settings.loginExpiryEmailSubject(), settings.loginExpiryEmailContent());
            log.info("已发送登录失效邮件提醒至 {}", recipient);
        } catch (RuntimeException error) {
            notified.set(false);
            log.warn("发送登录失效邮件提醒失败：{}", error.getMessage());
        }
    }

    /** Sends a user-requested SMTP test mail and reports failures to the caller. */
    public void sendTestEmail() {
        RuntimeSettingsVO settings = runtimeSettingsService.get();
        try {
            send(settings, "TiktokSparkFlow：SMTP 测试邮件",
                    "这是一封 SMTP 测试邮件。若你收到此邮件，登录失效提醒的邮件配置已生效。");
        } catch (RuntimeException error) {
            throw new IllegalStateException("测试邮件发送失败：" + safeMessage(error), error);
        }
    }

    private void send(RuntimeSettingsVO settings, String subject, String content) {
        if (settings.loginExpiryNotificationEmail().isBlank()
                || settings.smtpHost().isBlank()
                || settings.smtpUsername().isBlank()
                || settings.smtpPassword().isBlank()) {
            throw new IllegalStateException("请先完整填写收件邮箱和 SMTP 配置");
        }
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(settings.smtpHost());
        mailSender.setPort(settings.smtpPort());
        mailSender.setUsername(settings.smtpUsername());
        mailSender.setPassword(settings.smtpPassword());
        Properties properties = mailSender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", Boolean.toString(settings.smtpStarttls()));
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "10000");
        properties.put("mail.smtp.ssl.trust", settings.smtpHost());
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(settings.loginExpiryNotificationEmail());
        mail.setFrom(settings.smtpFromEmail().isBlank()
                ? settings.smtpUsername()
                : settings.smtpFromEmail());
        mail.setSubject(subject);
        mail.setText(content);
        mailSender.send(mail);
    }

    private String safeMessage(RuntimeException error) {
        String message = error.getMessage();
        return message == null || message.isBlank() ? "SMTP 服务未返回具体原因" : message;
    }

    public void markLoginRecovered() {
        notified.set(false);
    }
}

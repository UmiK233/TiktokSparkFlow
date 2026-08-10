package com.umik.tiktoksparkflow.entity;

/** 本地持久化的运行配置，修改后立即影响自动任务。 */
public record RuntimeSettingsEntity(
        boolean autoSendEnabled,
        String scheduleTime,
        String message,
        boolean sendMessage,
        Boolean allowRepeatedSend,
        Boolean headless,
        String loginExpiryNotificationEmail,
        String smtpHost,
        Integer smtpPort,
        String smtpUsername,
        String smtpPassword,
        String smtpFromEmail,
        Boolean smtpStarttls,
        String loginExpiryEmailSubject,
        String loginExpiryEmailContent
) {
    public RuntimeSettingsEntity {
        scheduleTime = scheduleTime == null ? "00:00" : scheduleTime;
        message = message == null ? "续火花" : message;
        // 兼容旧版 runtime-settings.json 中尚未保存该字段的情况。
        allowRepeatedSend = Boolean.TRUE.equals(allowRepeatedSend);
        // 兼容旧版 runtime-settings.json；默认使用有头模式，由 Xvfb 提供虚拟显示环境。
        headless = Boolean.TRUE.equals(headless);
        loginExpiryNotificationEmail = loginExpiryNotificationEmail == null ? "" : loginExpiryNotificationEmail.trim();
        smtpHost = smtpHost == null ? "" : smtpHost.trim();
        smtpPort = smtpPort == null || smtpPort < 1 || smtpPort > 65535 ? 587 : smtpPort;
        smtpUsername = smtpUsername == null ? "" : smtpUsername.trim();
        smtpPassword = smtpPassword == null ? "" : smtpPassword;
        smtpFromEmail = smtpFromEmail == null ? "" : smtpFromEmail.trim();
        smtpStarttls = smtpStarttls == null || smtpStarttls;
        loginExpiryEmailSubject = loginExpiryEmailSubject == null || loginExpiryEmailSubject.isBlank()
                ? "TiktokSparkFlow：抖音登录已失效" : loginExpiryEmailSubject.trim();
        loginExpiryEmailContent = loginExpiryEmailContent == null || loginExpiryEmailContent.isBlank()
                ? "自动任务检测到抖音登录已失效。为避免自动续火花失效，请尽快打开 TiktokSparkFlow 进行登录。"
                : loginExpiryEmailContent.trim();
    }
}

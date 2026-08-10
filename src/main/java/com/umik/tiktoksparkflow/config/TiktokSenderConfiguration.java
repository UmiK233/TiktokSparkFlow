package com.umik.tiktoksparkflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "tiktok.sender")
public class TiktokSenderConfiguration {
    private String profileDir = "data/single-user-profile";
    private String friendListFile = "data/friends.json";
    private String friendSelectionFile = "data/friend-selection.json";
    private String sendTaskDir = "data/send-tasks";
    private String sendHistoryDir = "data/send-history";
    private String runtimeSettingsFile = "data/runtime-settings.json";
    private String timeZone = "GMT+8";
    private Duration defaultTimeout = Duration.ofSeconds(30);
    private Duration navigationTimeout = Duration.ofMinutes(2);
    private Duration loginTimeout = Duration.ofMinutes(5);
    private Duration authenticationCheckTimeout = Duration.ofSeconds(10);
    private Duration friendScanTimeout = Duration.ofMinutes(3);
    private Duration visibleConfirmationTimeout = Duration.ofSeconds(10);
    private Duration bulkSendInterval = Duration.ofSeconds(1);

    public Path profilePath() {
        return Path.of(profileDir).toAbsolutePath().normalize();
    }

    public Path friendSelectionPath() {
        return Path.of(friendSelectionFile).toAbsolutePath().normalize();
    }

    public Path friendListPath() {
        return Path.of(friendListFile).toAbsolutePath().normalize();
    }

    public Path sendTaskPath() {
        return Path.of(sendTaskDir).toAbsolutePath().normalize();
    }

    public Path sendHistoryPath() {
        return Path.of(sendHistoryDir).toAbsolutePath().normalize();
    }

    public Path runtimeSettingsPath() {
        return Path.of(runtimeSettingsFile).toAbsolutePath().normalize();
    }

    public String getProfileDir() { return profileDir; }
    public void setProfileDir(String profileDir) { this.profileDir = profileDir; }
    public String getFriendListFile() { return friendListFile; }
    public void setFriendListFile(String friendListFile) { this.friendListFile = friendListFile; }
    public String getFriendSelectionFile() { return friendSelectionFile; }
    public void setFriendSelectionFile(String friendSelectionFile) { this.friendSelectionFile = friendSelectionFile; }
    public String getSendTaskDir() { return sendTaskDir; }
    public void setSendTaskDir(String sendTaskDir) { this.sendTaskDir = sendTaskDir; }
    public String getSendHistoryDir() { return sendHistoryDir; }
    public void setSendHistoryDir(String sendHistoryDir) { this.sendHistoryDir = sendHistoryDir; }
    public String getRuntimeSettingsFile() { return runtimeSettingsFile; }
    public void setRuntimeSettingsFile(String runtimeSettingsFile) { this.runtimeSettingsFile = runtimeSettingsFile; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public Duration getDefaultTimeout() { return defaultTimeout; }
    public void setDefaultTimeout(Duration defaultTimeout) { this.defaultTimeout = defaultTimeout; }
    public Duration getNavigationTimeout() { return navigationTimeout; }
    public void setNavigationTimeout(Duration navigationTimeout) { this.navigationTimeout = navigationTimeout; }
    public Duration getLoginTimeout() { return loginTimeout; }
    public void setLoginTimeout(Duration loginTimeout) { this.loginTimeout = loginTimeout; }
    public Duration getAuthenticationCheckTimeout() { return authenticationCheckTimeout; }
    public void setAuthenticationCheckTimeout(Duration authenticationCheckTimeout) { this.authenticationCheckTimeout = authenticationCheckTimeout; }
    public Duration getFriendScanTimeout() { return friendScanTimeout; }
    public void setFriendScanTimeout(Duration friendScanTimeout) { this.friendScanTimeout = friendScanTimeout; }
    public Duration getVisibleConfirmationTimeout() { return visibleConfirmationTimeout; }
    public void setVisibleConfirmationTimeout(Duration visibleConfirmationTimeout) { this.visibleConfirmationTimeout = visibleConfirmationTimeout; }
    public Duration getBulkSendInterval() { return bulkSendInterval; }
    public void setBulkSendInterval(Duration bulkSendInterval) { this.bulkSendInterval = bulkSendInterval; }
}

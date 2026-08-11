package com.umik.tiktoksparkflow.enums;

public enum ConversationType {
    FRIEND("朋友私信"),
    GROUP("群消息");

    private final String tabText;

    ConversationType(String tabText) {
        this.tabText = tabText;
    }

    public String tabText() {
        return tabText;
    }
}

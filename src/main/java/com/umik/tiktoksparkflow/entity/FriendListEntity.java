package com.umik.tiktoksparkflow.entity;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** 本地保存的最近一次完整好友列表。 */
public record FriendListEntity(
        List<String> friends,
        Map<String, String> avatars,
        String refreshedAt
) {
    public FriendListEntity {
        friends = friends == null ? List.of() : List.copyOf(friends);
        avatars = normalizeAvatars(avatars);
        refreshedAt = refreshedAt == null ? "" : refreshedAt;
    }

    /** 兼容旧版仅保存昵称列表的缓存文件。 */
    public FriendListEntity(List<String> friends, String refreshedAt) {
        this(friends, Map.of(), refreshedAt);
    }

    public static FriendListEntity empty() {
        return new FriendListEntity(List.of(), Map.of(), "");
    }

    private static Map<String, String> normalizeAvatars(Map<String, String> avatars) {
        if (avatars == null || avatars.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        avatars.forEach((nickname, url) -> {
            if (nickname != null && url != null && !url.isBlank()) {
                normalized.put(nickname, url.startsWith("//") ? "https:" + url : url);
            }
        });
        return Map.copyOf(normalized);
    }

}

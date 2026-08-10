package com.umik.tiktoksparkflow.browser;

import java.util.List;
import java.util.Map;

/** 浏览器页面中读取到的好友昵称与头像地址。 */
public record FriendListSnapshot(
        List<String> friends,
        Map<String, String> avatars
) {
    public FriendListSnapshot {
        friends = friends == null ? List.of() : List.copyOf(friends);
        avatars = avatars == null ? Map.of() : Map.copyOf(avatars);
    }
}

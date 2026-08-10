package com.umik.tiktoksparkflow.vo;

import java.util.List;
import java.util.Map;

public record FriendListVO(
        List<String> friends,
        Map<String, String> avatars,
        List<String> selectedFriends,
        String refreshedAt
) {
    public FriendListVO {
        friends = friends == null ? List.of() : List.copyOf(friends);
        avatars = avatars == null ? Map.of() : Map.copyOf(avatars);
        selectedFriends = selectedFriends == null ? List.of() : List.copyOf(selectedFriends);
        refreshedAt = refreshedAt == null ? "" : refreshedAt;
    }
}

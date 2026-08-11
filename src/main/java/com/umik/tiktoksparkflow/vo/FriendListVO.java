package com.umik.tiktoksparkflow.vo;

import java.util.List;
import java.util.Map;
import com.umik.tiktoksparkflow.enums.ConversationType;

public record FriendListVO(
        List<String> friends,
        Map<String, String> avatars,
        Map<String, ConversationType> conversationTypes,
        List<String> selectedFriends,
        String refreshedAt
) {
    public FriendListVO {
        friends = friends == null ? List.of() : List.copyOf(friends);
        avatars = avatars == null ? Map.of() : Map.copyOf(avatars);
        conversationTypes = conversationTypes == null ? Map.of() : Map.copyOf(conversationTypes);
        selectedFriends = selectedFriends == null ? List.of() : List.copyOf(selectedFriends);
        refreshedAt = refreshedAt == null ? "" : refreshedAt;
    }
}

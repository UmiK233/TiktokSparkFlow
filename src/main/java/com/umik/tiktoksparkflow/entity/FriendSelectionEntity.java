package com.umik.tiktoksparkflow.entity;

import java.util.List;

public record FriendSelectionEntity(List<String> selectedFriends) {
    public FriendSelectionEntity {
        selectedFriends = selectedFriends == null ? List.of() : List.copyOf(selectedFriends);
    }

    public static FriendSelectionEntity empty() {
        return new FriendSelectionEntity(List.of());
    }
}

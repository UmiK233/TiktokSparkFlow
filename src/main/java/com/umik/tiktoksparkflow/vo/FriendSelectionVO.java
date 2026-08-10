package com.umik.tiktoksparkflow.vo;

import java.util.List;

public record FriendSelectionVO(List<String> selectedFriends) {
    public FriendSelectionVO {
        selectedFriends = selectedFriends == null ? List.of() : List.copyOf(selectedFriends);
    }

    public static FriendSelectionVO empty() {
        return new FriendSelectionVO(List.of());
    }
}

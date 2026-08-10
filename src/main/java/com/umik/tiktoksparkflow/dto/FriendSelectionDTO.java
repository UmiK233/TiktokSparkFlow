package com.umik.tiktoksparkflow.dto;

import java.util.LinkedHashSet;
import java.util.List;

public record FriendSelectionDTO(List<String> selectedFriends) {
    public FriendSelectionDTO {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        if (selectedFriends != null) {
            selectedFriends.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(String::trim)
                    .forEach(normalized::add);
        }
        selectedFriends = List.copyOf(normalized);
    }
}

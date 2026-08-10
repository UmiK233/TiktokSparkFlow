package com.umik.tiktoksparkflow.service;

import com.umik.tiktoksparkflow.dto.FriendSelectionDTO;
import com.umik.tiktoksparkflow.vo.FriendListVO;
import com.umik.tiktoksparkflow.vo.FriendSelectionVO;

public interface FriendService {
    FriendListVO cachedList();
    FriendListVO list();
    FriendSelectionVO selection();
    FriendSelectionVO saveSelection(FriendSelectionDTO selection);
}

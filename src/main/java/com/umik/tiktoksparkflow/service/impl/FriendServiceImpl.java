package com.umik.tiktoksparkflow.service.impl;

import com.umik.tiktoksparkflow.browser.BrowserRuntime;
import com.umik.tiktoksparkflow.browser.TiktokCreatorClient;
import com.umik.tiktoksparkflow.browser.FriendListSnapshot;
import com.umik.tiktoksparkflow.browser.SingleUserProfileGuard;
import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.dto.FriendSelectionDTO;
import com.umik.tiktoksparkflow.entity.FriendListEntity;
import com.umik.tiktoksparkflow.entity.FriendSelectionEntity;
import com.umik.tiktoksparkflow.mapper.FriendListMapper;
import com.umik.tiktoksparkflow.mapper.FriendSelectionMapper;
import com.umik.tiktoksparkflow.service.FriendService;
import com.umik.tiktoksparkflow.utils.SendReceiptParser;
import com.umik.tiktoksparkflow.utils.Gmt8Time;
import com.umik.tiktoksparkflow.vo.FriendListVO;
import com.umik.tiktoksparkflow.vo.FriendSelectionVO;
import org.springframework.stereotype.Service;


@Service
public class FriendServiceImpl implements FriendService {
    private final TiktokSenderConfiguration configuration;
    private final SendReceiptParser receiptParser;
    private final SingleUserProfileGuard profileGuard;
    private final BrowserRuntime browserRuntime;
    private final FriendListMapper friendListMapper;
    private final FriendSelectionMapper selectionMapper;

    public FriendServiceImpl(
            TiktokSenderConfiguration configuration,
            SendReceiptParser receiptParser,
            SingleUserProfileGuard profileGuard,
            BrowserRuntime browserRuntime,
            FriendListMapper friendListMapper,
            FriendSelectionMapper selectionMapper
    ) {
        this.configuration = configuration;
        this.receiptParser = receiptParser;
        this.profileGuard = profileGuard;
        this.browserRuntime = browserRuntime;
        this.friendListMapper = friendListMapper;
        this.selectionMapper = selectionMapper;
    }

    @Override
    public FriendListVO cachedList() {
        return toVO(friendListMapper.load());
    }

    @Override
    public FriendListVO list() {
        FriendListSnapshot friends;
        try (SingleUserProfileGuard.Lease ignored = profileGuard.acquire()) {
            friends = browserRuntime.execute(page -> {
                TiktokCreatorClient creator = new TiktokCreatorClient(
                        page, configuration, receiptParser);
                creator.requireAuthentication();
                return creator.listFriendsWithAvatars();
            });
        }
        FriendListEntity saved = friendListMapper.save(new FriendListEntity(
                friends.friends(),
                friends.avatars(),
                friends.conversationTypes(),
                Gmt8Time.now()));
        return toVO(saved);
    }

    @Override
    public FriendSelectionVO selection() {
        return toVO(selectionMapper.load());
    }

    @Override
    public FriendSelectionVO saveSelection(FriendSelectionDTO selection) {
        FriendSelectionEntity saved = selectionMapper.save(
                new FriendSelectionEntity(selection.selectedFriends()));
        return toVO(saved);
    }

    private FriendSelectionVO toVO(FriendSelectionEntity selection) {
        return new FriendSelectionVO(selection.selectedFriends());
    }

    private FriendListVO toVO(FriendListEntity friendList) {
        return new FriendListVO(
                friendList.friends(),
                friendList.avatars(),
                friendList.conversationTypes(),
                selectionMapper.load().selectedFriends(),
                Gmt8Time.normalize(friendList.refreshedAt()));
    }
}

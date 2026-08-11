package com.umik.tiktoksparkflow.service;

import com.umik.tiktoksparkflow.browser.BrowserRuntime;
import com.umik.tiktoksparkflow.browser.FriendListSnapshot;
import com.umik.tiktoksparkflow.browser.SingleUserProfileGuard;
import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.entity.FriendListEntity;
import com.umik.tiktoksparkflow.entity.FriendSelectionEntity;
import com.umik.tiktoksparkflow.mapper.FriendListMapper;
import com.umik.tiktoksparkflow.mapper.FriendSelectionMapper;
import com.umik.tiktoksparkflow.service.impl.FriendServiceImpl;
import com.umik.tiktoksparkflow.utils.SendReceiptParser;
import com.umik.tiktoksparkflow.vo.FriendListVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FriendServiceImplTest {
    @Test
    void 返回实时好友及已保存的选择() {
        BrowserRuntime browserRuntime = mock(BrowserRuntime.class);
        FriendListMapper friendListMapper = mock(FriendListMapper.class);
        FriendSelectionMapper selectionMapper = mock(FriendSelectionMapper.class);
        when(browserRuntime.execute(any())).thenReturn(new FriendListSnapshot(
                List.of("星火", "南."),
                java.util.Map.of("星火", "https://example.com/avatar.png"),
                java.util.Map.of()));
        when(friendListMapper.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(selectionMapper.load()).thenReturn(
                new FriendSelectionEntity(List.of("南.")));

        FriendService service = new FriendServiceImpl(
                new TiktokSenderConfiguration(),
                new SendReceiptParser(),
                new SingleUserProfileGuard(),
                browserRuntime,
                friendListMapper,
                selectionMapper);

        FriendListVO result = service.list();

        assertEquals(List.of("星火", "南."), result.friends());
        assertEquals("https://example.com/avatar.png", result.avatars().get("星火"));
        assertEquals(List.of("南."), result.selectedFriends());
        verify(friendListMapper).save(any(FriendListEntity.class));
    }
}

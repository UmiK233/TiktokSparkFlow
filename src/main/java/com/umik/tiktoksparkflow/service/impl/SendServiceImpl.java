package com.umik.tiktoksparkflow.service.impl;

import com.umik.tiktoksparkflow.browser.BrowserRuntime;
import com.umik.tiktoksparkflow.browser.TiktokCreatorClient;
import com.umik.tiktoksparkflow.browser.SingleUserProfileGuard;
import com.umik.tiktoksparkflow.config.TiktokSenderConfiguration;
import com.umik.tiktoksparkflow.dto.BulkSendCommand;
import com.umik.tiktoksparkflow.dto.SendCommand;
import com.umik.tiktoksparkflow.entity.SendHistoryRecordEntity;
import com.umik.tiktoksparkflow.mapper.FriendSelectionMapper;
import com.umik.tiktoksparkflow.mapper.SendHistoryMapper;
import com.umik.tiktoksparkflow.service.SendService;
import com.umik.tiktoksparkflow.service.RuntimeSettingsService;
import com.umik.tiktoksparkflow.utils.SendReceiptParser;
import com.umik.tiktoksparkflow.utils.Gmt8Time;
import com.umik.tiktoksparkflow.vo.BulkSendVO;
import com.umik.tiktoksparkflow.vo.SendReceiptVO;
import com.umik.tiktoksparkflow.vo.SendResultVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SendServiceImpl implements SendService {
    private final TiktokSenderConfiguration configuration;
    private final SendReceiptParser receiptParser;
    private final SingleUserProfileGuard profileGuard;
    private final BrowserRuntime browserRuntime;
    private final FriendSelectionMapper selectionMapper;
    private final SendHistoryMapper historyMapper;
    private final RuntimeSettingsService runtimeSettingsService;

    public SendServiceImpl(
            TiktokSenderConfiguration configuration,
            SendReceiptParser receiptParser,
            SingleUserProfileGuard profileGuard,
            BrowserRuntime browserRuntime,
            FriendSelectionMapper selectionMapper,
            SendHistoryMapper historyMapper,
            RuntimeSettingsService runtimeSettingsService
    ) {
        this.configuration = configuration;
        this.receiptParser = receiptParser;
        this.profileGuard = profileGuard;
        this.browserRuntime = browserRuntime;
        this.selectionMapper = selectionMapper;
        this.historyMapper = historyMapper;
        this.runtimeSettingsService = runtimeSettingsService;
    }

    @Override
    public SendResultVO send(SendCommand command) {
        if (command.sendMessage()
                && !runtimeSettingsService.get().allowRepeatedSend()
                && historyMapper.wasSentToday(command.targetNickname())) {
            return SendResultVO.skippedAlreadySent(command.targetNickname());
        }
        try (SingleUserProfileGuard.Lease ignored = profileGuard.acquire()) {
            return browserRuntime.execute(page -> {
                TiktokCreatorClient creator = new TiktokCreatorClient(
                        page, configuration, receiptParser);
                creator.requireAuthentication();
                creator.selectFriend(command.targetNickname());

                if (!command.sendMessage()) {
                    return SendResultVO.selected(command.targetNickname());
                }
                SendReceiptVO receipt = creator.sendAndConfirm(command.message());
                recordDirectSend(command.targetNickname(), command.message());
                return SendResultVO.confirmed(command.targetNickname(), receipt);
            });
        }
    }

    @Override
    public BulkSendVO sendSelected(BulkSendCommand command) {
        List<String> targets = selectionMapper.load().selectedFriends();
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("尚未配置需要群发的好友");
        }

        try (SingleUserProfileGuard.Lease ignored = profileGuard.acquire()) {
            return browserRuntime.execute(page -> {
                TiktokCreatorClient creator = new TiktokCreatorClient(
                        page, configuration, receiptParser);
                creator.requireAuthentication();
                List<SendResultVO> results = new ArrayList<>();

                for (int index = 0; index < targets.size(); index++) {
                    String target = targets.get(index);
                    try {
                        if (command.sendMessage()
                                && !runtimeSettingsService.get().allowRepeatedSend()
                                && historyMapper.wasSentToday(target)) {
                            results.add(SendResultVO.skippedAlreadySent(target));
                            continue;
                        }
                        creator.selectFriend(target);
                        if (command.sendMessage()) {
                            SendReceiptVO receipt = creator.sendAndConfirm(command.message());
                            recordDirectSend(target, command.message());
                            results.add(SendResultVO.confirmed(target, receipt));
                        } else {
                            results.add(SendResultVO.selected(target));
                        }
                    } catch (RuntimeException error) {
                        String detail = error.getMessage() == null || error.getMessage().isBlank()
                                ? "发送失败，未返回具体原因"
                                : error.getMessage();
                        results.add(SendResultVO.failed(target, detail));
                    }

                    if (index < targets.size() - 1) {
                        page.waitForTimeout(configuration.getBulkSendInterval().toMillis());
                    }
                }
                return BulkSendVO.from(results);
            });
        }
    }

    private void recordDirectSend(String target, String message) {
        historyMapper.record(new SendHistoryRecordEntity(
                "direct",
                target,
                message,
                Gmt8Time.now()));
    }

}

package com.umik.tiktoksparkflow;

import com.umik.tiktoksparkflow.dto.SendCommand;
import com.umik.tiktoksparkflow.vo.SendResultVO;
import com.umik.tiktoksparkflow.service.SendService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 仅在需要执行真实浏览器测试时修改这些常量并移除禁用标记。 */
@Disabled("手工测试：会打开持久化浏览器资料，并且可能发送真实消息")
@SpringBootTest
class ManualSingleFriendSendTest {
    private static final String TARGET_NICKNAME = "星火";
    private static final String MESSAGE = "续火花";
    private static final boolean SEND_MESSAGE = false;

    @Autowired
    private SendService sendService;

    @Test
    void sendToOneFriend() {
        SendResultVO result = sendService.send(
                new SendCommand(TARGET_NICKNAME, MESSAGE, SEND_MESSAGE));
        assertTrue(result.success());
    }
}

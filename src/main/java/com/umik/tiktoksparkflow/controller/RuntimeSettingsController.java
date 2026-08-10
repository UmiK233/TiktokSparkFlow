package com.umik.tiktoksparkflow.controller;

import com.umik.tiktoksparkflow.common.Result;
import com.umik.tiktoksparkflow.aspect.OperationLog;
import com.umik.tiktoksparkflow.dto.RuntimeSettingsDTO;
import com.umik.tiktoksparkflow.service.RuntimeSettingsService;
import com.umik.tiktoksparkflow.service.LoginExpiryEmailNotifier;
import com.umik.tiktoksparkflow.vo.RuntimeSettingsVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runtime-settings")
public class RuntimeSettingsController {
    private final RuntimeSettingsService runtimeSettingsService;
    private final LoginExpiryEmailNotifier loginExpiryEmailNotifier;

    public RuntimeSettingsController(
            RuntimeSettingsService runtimeSettingsService,
            LoginExpiryEmailNotifier loginExpiryEmailNotifier
    ) {
        this.runtimeSettingsService = runtimeSettingsService;
        this.loginExpiryEmailNotifier = loginExpiryEmailNotifier;
    }

    @GetMapping
    @OperationLog("读取运行配置")
    public Result<RuntimeSettingsVO> get() {
        return Result.success("运行配置获取成功", runtimeSettingsService.get());
    }

    @PutMapping
    @OperationLog("保存运行配置")
    public Result<RuntimeSettingsVO> update(@RequestBody RuntimeSettingsDTO settings) {
        return Result.success("运行配置已保存并立即生效", runtimeSettingsService.update(settings));
    }

    @PostMapping("/test-email")
    @OperationLog("发送 SMTP 测试邮件")
    public Result<Void> sendTestEmail() {
        loginExpiryEmailNotifier.sendTestEmail();
        return Result.success("测试邮件已发送", null);
    }
}

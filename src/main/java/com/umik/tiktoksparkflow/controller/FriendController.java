package com.umik.tiktoksparkflow.controller;

import com.umik.tiktoksparkflow.common.Result;
import com.umik.tiktoksparkflow.aspect.OperationLog;
import com.umik.tiktoksparkflow.dto.FriendSelectionDTO;
import com.umik.tiktoksparkflow.service.FriendService;
import com.umik.tiktoksparkflow.vo.FriendListVO;
import com.umik.tiktoksparkflow.vo.FriendSelectionVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/friends")
public class FriendController {
    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    @GetMapping
    @OperationLog("获取并同步好友列表")
    public Result<FriendListVO> list() {
        return Result.success("好友列表获取成功", friendService.list());
    }

    @GetMapping("/local")
    @OperationLog("读取本地好友列表")
    public Result<FriendListVO> cachedList() {
        return Result.success("本地好友列表获取成功", friendService.cachedList());
    }

    @GetMapping("/selection")
    @OperationLog("读取续火花名单")
    public Result<FriendSelectionVO> selection() {
        return Result.success("好友选择配置获取成功", friendService.selection());
    }

    @PutMapping("/selection")
    @OperationLog("保存续火花名单")
    public Result<FriendSelectionVO> saveSelection(@RequestBody FriendSelectionDTO selection) {
        return Result.success("好友选择配置保存成功",
                friendService.saveSelection(selection));
    }
}

package com.umik.tiktoksparkflow.vo;

public record SendResultVO(
        boolean success,
        boolean sent,
        String targetNickname,
        String detail,
        SendReceiptVO receipt
) {
    public static SendResultVO selected(String targetNickname) {
        return new SendResultVO(true, false, targetNickname,
                "已选中目标好友；当前为仅选择模式，没有发送消息", null);
    }

    public static SendResultVO confirmed(String targetNickname, SendReceiptVO receipt) {
        return new SendResultVO(true, true, targetNickname,
                "服务端回执和己方消息气泡均已确认", receipt);
    }

    public static SendResultVO failed(String targetNickname, String detail) {
        return new SendResultVO(false, false, targetNickname, detail, null);
    }

    public static SendResultVO skippedAlreadySent(String targetNickname) {
        return new SendResultVO(true, false, targetNickname,
                "今日已经确认发送成功，本次自动跳过", null);
    }
}

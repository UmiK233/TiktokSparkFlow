package com.umik.tiktoksparkflow.common;

import com.umik.tiktoksparkflow.enums.ResultCode;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void 成功响应包含数据() {
        Result<String> response = Result.success("查询成功", "返回内容");

        assertEquals(200, response.code());
        assertEquals("查询成功", response.message());
        assertEquals("返回内容", response.data());
    }

    @Test
    void 失败响应的数据为空() {
        Result<Void> response = Result.fail(ResultCode.FORBIDDEN, "权限不足，无法访问");
        String json = objectMapper.writeValueAsString(response);

        assertEquals(403, response.code());
        assertTrue(json.contains("\"message\":\"权限不足，无法访问\""));
        assertTrue(json.contains("\"data\":null"));
    }
}

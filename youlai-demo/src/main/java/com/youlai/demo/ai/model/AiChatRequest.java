package com.youlai.demo.ai.model;

import jakarta.validation.constraints.NotBlank;

public record AiChatRequest(
        @NotBlank(message = "消息内容不能为空")
        String message
) {
}

package com.youlai.demo.ai.controller;

import com.youlai.common.result.Result;
import com.youlai.demo.ai.model.AiChatRequest;
import com.youlai.demo.ai.model.AiChatResponse;
import com.youlai.demo.ai.service.AiChatService;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo/ai")
@ConditionalOnBean(AiChatService.class)
public class AiChatController {

    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/chat")
    public Result<AiChatResponse> chat(@Valid @RequestBody AiChatRequest request) {
        return Result.success(aiChatService.chat(request));
    }
}

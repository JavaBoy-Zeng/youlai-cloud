package com.youlai.demo.ai.service;

import com.youlai.demo.ai.model.AiChatRequest;
import com.youlai.demo.ai.model.AiChatResponse;
import com.youlai.demo.ai.tool.SqlMonitorAiTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@ConditionalOnBean(ChatClient.Builder.class)
public class AiChatService {

    private final ChatClient chatClient;
    private final SqlMonitorAiTools sqlMonitorAiTools;

    public AiChatService(ChatClient.Builder builder, SqlMonitorAiTools sqlMonitorAiTools) {
        this.chatClient = builder.build();
        this.sqlMonitorAiTools = sqlMonitorAiTools;
    }

    public AiChatResponse chat(AiChatRequest request) {
        String answer = chatClient.prompt()
                .system("""
                        You are an assistant for the youlai-demo service.
                        When users ask about SQL monitoring, slow SQL, SQL errors, SQL timeout, or database execution metrics, use the provided tool.
                        The tool category is ai, but the underlying monitoring data still comes from the existing SQL monitor subsystem.
                        Do not invent metric values.
                        """)
                .user(request.message())
                .tools(sqlMonitorAiTools)
                .toolContext(Map.of("category", "ai"))
                .call()
                .content();

        return new AiChatResponse(answer);
    }
}

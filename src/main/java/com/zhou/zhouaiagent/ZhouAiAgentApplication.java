package com.zhou.zhouaiagent;

import org.springframework.ai.mcp.client.autoconfigure.McpToolCallbackAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = McpToolCallbackAutoConfiguration.class)
public class ZhouAiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhouAiAgentApplication.class, args);
    }

}

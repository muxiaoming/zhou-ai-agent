package com.zhou.zhouimagesearchmcpserver;

import com.zhou.zhouimagesearchmcpserver.tool.ImageSearchTool;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ZhouImageSearchMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhouImageSearchMcpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider imageToolCallbackProvider(ImageSearchTool imageSearchTool) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(imageSearchTool)
                .build();
    }
}

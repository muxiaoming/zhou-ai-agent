package com.zhou.zhouaiagent.demo;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 查询扩展器 Demo
 */
@Component
public class MultiQueryExpanderDemo {

    // 注入bean失败(创建bean失败) 进入源码查看, 没有无参构造器, 得手动创建bean, 通过理解源码, 创建bean
    private final ChatClient.Builder chatClientBuilder;

    public MultiQueryExpanderDemo(ChatModel dashScopeChatmodel) {
        this.chatClientBuilder = ChatClient.builder(dashScopeChatmodel);
    }

    public List<Query> multiQueryExpander(String prompt) {
        MultiQueryExpander multiQueryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClientBuilder)
                .numberOfQueries(3)
                .build();
        return multiQueryExpander.expand(new Query(prompt));
    }
}

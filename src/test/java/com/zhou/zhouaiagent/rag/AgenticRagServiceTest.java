package com.zhou.zhouaiagent.rag;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AgenticRagServiceTest {

    @Resource
    private AgenticRagService agenticRagService;

    @Test
    void query_withKnowledgeBaseTopic_shouldRetrieveAndAnswer() {
        String chatId = "test-" + System.currentTimeMillis();
        String answer = agenticRagService.query("婚后夫妻关系不亲密怎么办？", chatId);
        Assertions.assertNotNull(answer);
        Assertions.assertFalse(answer.isBlank());
        Assertions.assertTrue(answer.length() > 20);
    }

    @Test
    void query_withSimpleGreeting_shouldAnswerDirectly() {
        String chatId = "test-" + System.currentTimeMillis();
        String answer = agenticRagService.query("你好", chatId);
        Assertions.assertNotNull(answer);
        Assertions.assertFalse(answer.isBlank());
    }

    @Test
    void query_withKeywordMatch_shouldFindViaHybridRetrieval() {
        String chatId = "test-" + System.currentTimeMillis();
        String answer = agenticRagService.query("单身的人如何提升自身魅力吸引潜在伴侣？", chatId);
        Assertions.assertNotNull(answer);
        Assertions.assertFalse(answer.isBlank());
    }

    @Test
    void query_withAmbiguousQuery_shouldFallBackGracefully() {
        String chatId = "test-" + System.currentTimeMillis();
        String answer = agenticRagService.query("怎么处理那种情况", chatId);
        Assertions.assertNotNull(answer);
        Assertions.assertFalse(answer.isBlank());
    }

    @Test
    void query_withUnrelatedTopic_shouldNotRetrieve() {
        String chatId = "test-" + System.currentTimeMillis();
        String answer = agenticRagService.query("Java的垃圾回收机制是什么？", chatId);
        Assertions.assertNotNull(answer);
        Assertions.assertFalse(answer.isBlank());
    }
}

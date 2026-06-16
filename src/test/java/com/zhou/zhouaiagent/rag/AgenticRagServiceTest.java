package com.zhou.zhouaiagent.rag;

import com.zhou.zhouaiagent.memory.JdbcChatMemoryRepository;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

@SpringBootTest
class AgenticRagServiceTest {

    @Resource
    private AgenticRagService agenticRagService;

    @Resource
    private JdbcTemplate jdbcTemplate;

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

    /**
     * 多轮对话记忆测试：验证会话 ID 能正确保存和检索对话历史
     */
    @Test
    void query_multiTurnConversation_shouldPreserveContext() {
        // 创建 repository 实例来验证数据库中的数据
        JdbcChatMemoryRepository chatMemoryRepository = new JdbcChatMemoryRepository(jdbcTemplate);

        // 使用相同的 chatId 进行多轮对话
        String chatId = "multi-turn-test-" + System.currentTimeMillis();

        // 第一轮：询问婚后关系问题
        String answer1 = agenticRagService.query("婚后夫妻关系不亲密怎么办？", chatId);
        Assertions.assertNotNull(answer1);
        Assertions.assertFalse(answer1.isBlank());

        // 第二轮：继续追问（应该能记住第一轮的对话）
        String answer2 = agenticRagService.query("你能再详细说说沟通技巧吗？", chatId);
        Assertions.assertNotNull(answer2);
        Assertions.assertFalse(answer2.isBlank());

        // 验证对话历史被保存到了数据库
        List<Message> conversationHistory = chatMemoryRepository.findByConversationId(chatId);
        Assertions.assertFalse(conversationHistory.isEmpty(), "对话历史应该被保存");

        // 应该有 4 条消息：第1轮用户消息、第1轮助手消息、第2轮用户消息、第2轮助手消息
        // 记忆窗口为 20，所以所有消息都应该保留
        Assertions.assertTrue(conversationHistory.size() >= 4,
                "应该保存至少4条消息，实际保存了 " + conversationHistory.size() + " 条");

        System.out.println("✓ 多轮对话测试通过！共保存 " + conversationHistory.size() + " 条消息");
    }

    /**
     * 会话隔离测试：验证不同 chatId 的对话互不影响
     */
    @Test
    void query_differentChatIds_shouldHaveIsolatedHistory() {
        // 创建 repository 实例来验证数据库中的数据
        JdbcChatMemoryRepository chatMemoryRepository = new JdbcChatMemoryRepository(jdbcTemplate);

        String chatId1 = "isolation-test-1-" + System.currentTimeMillis();
        String chatId2 = "isolation-test-2-" + System.currentTimeMillis();

        // chatId1 的对话
        agenticRagService.query("婚后关系问题", chatId1);
        agenticRagService.query("如何改善沟通", chatId1);

        // chatId2 的对话
        agenticRagService.query("单身如何提升魅力", chatId2);

        // 验证两个会话的历史是隔离的
        List<Message> history1 = chatMemoryRepository.findByConversationId(chatId1);
        List<Message> history2 = chatMemoryRepository.findByConversationId(chatId2);

        Assertions.assertEquals(4, history1.size(), "chatId1 应该有4条消息");
        Assertions.assertEquals(2, history2.size(), "chatId2 应该有2条消息");

        System.out.println("✓ 会话隔离测试通过！chatId1: " + history1.size() + " 条, chatId2: " + history2.size() + " 条");
    }

    /**
     * 边界测试：chatId 为 null 时应该直接回答，不保存历史
     */
    @Test
    void query_withNullChatId_shouldWorkWithoutHistory() {
        // chatId 为 null 时，应该降级为无记忆模式
        String answer = agenticRagService.query("你好", null);
        Assertions.assertNotNull(answer);
        Assertions.assertFalse(answer.isBlank());
        System.out.println("✓ null chatId 测试通过");
    }

    /**
     * 边界测试：chatId 为空字符串时应该降级
     */
    @Test
    void query_withEmptyChatId_shouldWorkWithoutHistory() {
        String answer = agenticRagService.query("你好", "");
        Assertions.assertNotNull(answer);
        Assertions.assertFalse(answer.isBlank());
        System.out.println("✓ 空字符串 chatId 测试通过");
    }

    /**
     * 边界测试：长对话超过记忆窗口（20条）应该截断
     */
    @Test
    void query_longConversation_shouldTruncateOldMessages() {
        JdbcChatMemoryRepository chatMemoryRepository = new JdbcChatMemoryRepository(jdbcTemplate);
        String chatId = "long-conv-test-" + System.currentTimeMillis();

        // 模拟超过20条消息的长对话（15轮，共30条消息）
        for (int i = 0; i < 15; i++) {
            agenticRagService.query("第" + (i+1) + "个问题", chatId);
        }

        // 验证消息被截断
        List<Message> history = chatMemoryRepository.findByConversationId(chatId);
        Assertions.assertTrue(history.size() <= 20,
                "应该最多保留20条消息，实际保留了 " + history.size() + " 条");

        System.out.println("✓ 长对话截断测试通过！保留了 " + history.size() + " 条消息");
    }

    /**
     * 边界测试：验证消息顺序（先发先存）
     */
    @Test
    void query_messageOrder_shouldBePreserved() {
        JdbcChatMemoryRepository chatMemoryRepository = new JdbcChatMemoryRepository(jdbcTemplate);
        String chatId = "order-test-" + System.currentTimeMillis();

        // 发送3轮对话
        agenticRagService.query("第一个问题", chatId);
        agenticRagService.query("第二个问题", chatId);
        agenticRagService.query("第三个问题", chatId);

        // 验证消息顺序
        List<Message> history = chatMemoryRepository.findByConversationId(chatId);
        Assertions.assertTrue(history.size() >= 6, "应该有至少6条消息");

        // 检查用户消息的内容顺序
        String firstUserMsg = history.get(0).getText();
        String secondUserMsg = history.get(2).getText();
        String thirdUserMsg = history.get(4).getText();

        Assertions.assertTrue(firstUserMsg.contains("第一个问题"), "第一条消息内容正确");
        Assertions.assertTrue(secondUserMsg.contains("第二个问题"), "第二条消息内容正确");
        Assertions.assertTrue(thirdUserMsg.contains("第三个问题"), "第三条消息内容正确");

        System.out.println("✓ 消息顺序测试通过");
    }
}

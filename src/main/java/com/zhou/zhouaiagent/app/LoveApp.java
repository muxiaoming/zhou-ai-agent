package com.zhou.zhouaiagent.app;

import com.alibaba.cloud.ai.advisor.RetrievalRerankAdvisor;
import com.zhou.zhouaiagent.advisor.MyLoggerAdvisor;
import com.zhou.zhouaiagent.rag.LoveAppRagCloudAdvisorConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;


@Slf4j
@Component
public class LoveApp {

    /**
     * 模型访问接口(ChatModel):
     * 底层接口, 负责真正调用 AI 模型(通义千问) 是必须有的(用来连接 AI) 直接调用，但麻烦
     * 便捷调用客户端(chatClient):
     * 高级工具, 简化调用, 链式 API, 必须依赖 ChatModel
     */
    private final ChatClient chatClient;


    public static final String SYSTEM_PROMPT = """
             扮演深耕恋爱心理领域的专家。开场向用户表明身份，告知用户可倾诉恋爱难题。
             围绕单身、恋爱、已婚三种状态提问：单身状态询问社交圈拓展及追求心仪对象的困扰；
             恋爱状态询问沟通、习惯差异引发的矛盾；已婚状态询问家庭责任与亲属关系处理的问题。
             引导用户详述事情经过、对方反应及自身想法，以便给出专属解决方案。
            """;

    public LoveApp(ChatModel dashScopeChatModel) {
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                // 初始化基于内存的对话记忆
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                // 记忆的最大消息数
                // maxMessages (2) = 记住最近 2 条消息（用户 + AI 各一条，凑一对）+ 再加上用户的输入, 查看日志可看到此时相当于有三条消息
                .maxMessages(20)
                .build();
        this.chatClient = ChatClient.builder(dashScopeChatModel)
                // 设置默认系统提示词
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        // 会话记忆
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        // 自定义日志 Advisor，可按需开启
                        new MyLoggerAdvisor()
                )
                .build();
    }


    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    record LoveReport(String title, List<String> suggests) {
    }

    /**
     * AI 恋爱报告功能（实战结构化输出）
     *
     * @param message
     * @param chatId
     * @return
     */
    public LoveReport doChatWithReport(String message, String chatId) {
        return chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                .call()
                .entity(LoveReport.class);
    }

    @Resource
    private SimpleVectorStore simpleVectorStore;
    @Resource
    private QuestionAnswerAdvisor questionAnswerAdvisor;

    /**
     * QuestionAnswerAdvisor
     *      做简单内部知识库 / 小问答机器人。
     *      快速验证 RAG 效果，不想写太多配置。
     *      需求固定：“查 3 条相似文档，直接回答”。
     * RetrievalAugmentationAdvisor
     *      企业级知识库：需要高准确率、低幻觉。
     *      问题模糊 / 简短：需要改写 / 扩展才能检索到好结果。
     *      检索结果噪声多：需要重排、过滤、去重。
     *      要实现高级 RAG：如 Multi-Query、RAG-Fusion、HyDE、Step-back Prompting。
     *
     * QuestionAnswerAdvisor：简单够用、开箱即用，适合快速开发简单 RAG 应用。
     * RetrievalAugmentationAdvisor：强大灵活、全链路可控，适合企业级、高精度、复杂 RAG 场景。
     *
     * 基于内存向量存储的简易知识库问答
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRagByInMemory(String message, String chatId) {
        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                //.advisors(questionAnswerAdvisor)
                // 应用 RAG 知识库问答
                .advisors(new QuestionAnswerAdvisor(simpleVectorStore))
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }

    @Resource
    private Advisor loveAppRagCloudAdvisor;

    /**
     * 基于云知识库的RAG检索增强服务
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithRagByCloud(String message, String chatId) {
        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId))
                // 应用 RAG 检索增强服务（基于云知识库服务）
                .advisors(loveAppRagCloudAdvisor)
                .call()
                .chatResponse();
        String content = chatResponse.getResult().getOutput().getText();
        log.info("content: {}", content);
        return content;
    }
}

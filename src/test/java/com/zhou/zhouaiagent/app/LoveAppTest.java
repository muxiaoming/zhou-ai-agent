package com.zhou.zhouaiagent.app;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class LoveAppTest {
    /**
     * 模型访问接口:
     * 底层接口, 负责真正调用 AI 模型(通义千问) 是必须有的(用来连接 AI) 直接调用，但麻烦
     */
    @Resource
    private ChatModel dashScopeChatModel;

    @Resource
    private LoveApp loveApp;

    /**
     * 相当麻烦
     *
     * @return
     */
    @Test
    void chatWithModel() {

        ChatResponse response = dashScopeChatModel.call(
                new Prompt(
                        List.of(
                                new SystemMessage(LoveApp.SYSTEM_PROMPT),
                                new UserMessage("我失恋了")
                        )
                )
        );
        String context = response.getResult().getOutput().getText();
        Assertions.assertNotNull(context);
    }

    @Test
    void doChat() {
        String chatId = UUID.randomUUID().toString();
        // 第一轮
        String message = "你好，我是张三";
        String answer = loveApp.doChat(message, chatId);
        // 第二轮
        message = "我想学习恋爱技巧";
        answer = loveApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
        // 第三轮
        message = "我的名字是？帮我回忆一下";
        answer = loveApp.doChat(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithReport() {
        String chatId = UUID.randomUUID().toString();
        String message = "你好，我是张三，我想让另一半（小珊）更爱我，但我不知道该怎么做";
        LoveApp.LoveReport loveReport = loveApp.doChatWithReport(message, chatId);
        Assertions.assertNotNull(loveReport);
    }

    @Test
    void doChatWithRagByInMemory() {
        String chatId = UUID.randomUUID().toString();
        String message = "我已经结婚了，但是婚后关系不太亲密，怎么办？";
        String answer = loveApp.doChatWithRagByInMemory(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithRagByCloud() {
        String chatId = UUID.randomUUID().toString();
        String message = "我已经结婚了，但是婚后关系不太亲密，怎么办？";
        String answer = loveApp.doChatWithRagByCloud(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithRagByCustom() {
        String chatId = UUID.randomUUID().toString();
        String message = "我已经结婚了，但是婚后关系不太亲密，怎么办？";
        // 婚后夫妻亲密关系疏远的改善方法包括：定期安排二人世界，如每周一次看电影或共进晚餐；保持身体亲密接触，日常拥抱、亲吻；分享日常喜怒哀乐，深入交流内心想法；一起回忆美好过往，如旅行经历、恋爱趣事；为对方制造小惊喜，如纪念日礼物。参考老张夫妇的做法，坚持每周约会、分享生活点滴，结婚多年仍甜蜜如初。推荐学习课程《婚后亲密关系维护秘籍》，系统掌握持续升温爱情、守护家庭幸福的方法与技巧。
        String answer = loveApp.doChatWithRagByCustom(message, chatId);
        Assertions.assertNotNull(answer);
    }
}
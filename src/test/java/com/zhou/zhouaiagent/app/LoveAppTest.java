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

    @Test
    void doChatWithTools() {
        // 测试联网搜索问题的答案
        testMessage("周末想带女朋友去上海约会，推荐几个适合情侣的小众打卡地？");

        // 测试网页抓取：恋爱案例分析
        testMessage("最近和对象吵架了，看看编程导航网站（codefather.cn）的其他情侣是怎么解决矛盾的？");

        // 测试资源下载：图片下载
        testMessage("直接下载一张适合做手机壁纸的星空情侣图片为文件");

        // 测试终端操作：执行代码
        testMessage("执行 Python3 脚本来生成一份结构清晰、含图表与文字分析的 恋爱记录PDF 报告");

        // 测试文件操作：保存用户档案
        testMessage("保存我的恋爱档案为文件");

        // 测试 PDF 生成
        testMessage("生成一份‘七夕约会计划’PDF，包含餐厅预订、活动流程和礼物清单");
    }

    private void testMessage(String message) {
        String chatId = UUID.randomUUID().toString();
        String answer = loveApp.doChatWithTools(message, chatId);
        Assertions.assertNotNull(answer);
    }

    @Test
    void doChatWithMcp() {

        String chatId = UUID.randomUUID().toString();
//        // 测试地图 MCP
//        String message = "我的另一半居住在上海静安区，请帮我找到 5 公里内合适的约会地点";
//        String answer = loveApp.doChatWithMcp(message, chatId);
//        Assertions.assertNotNull(answer);

        // 测试图片搜索 MCP
        // @Resource private ToolCallbackProvider toolCallbackProvider;
        // toolCallbackProvider 会启动 mcp-servers.json里面配置的stdio mcp jar包
        String message = "帮我搜索一些可爱的猫猫图片用于提高对象好感";
        String answer =  loveApp.doChatWithMcp(message, chatId);
        Assertions.assertNotNull(answer);
    }

}
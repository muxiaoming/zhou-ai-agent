package com.zhou.zhouaiagent.demo;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SpringAiAiInvokeTest {

    @Resource
    private ChatModel dashScopeChatModel;

    @Test
    public void test() {
        AssistantMessage output = dashScopeChatModel.call(new Prompt("你好, 我是java初学者张三"))
                .getResult()
                .getOutput();
        // 你好，张三！👋 很高兴认识你这位Java初学者～欢迎踏上编程学习之旅 🌟
        //
        //Java是一门非常经典、实用且适合入门的编程语言，语法清晰、生态丰富，广泛应用于企业级开发、Android应用、大数据等领域。作为初学者，你已经迈出了最重要的一步！
        //
        //如果你愿意，我可以帮你：
        //✅ 搭建Java开发环境（JDK + IDE，比如IntelliJ IDEA或VS Code）
        //✅ 写出你的第一个程序：`Hello, 张三！`
        //✅ 理解基础概念：变量、数据类型、运算符、if/else、循环、数组等
        //✅ 解答作业题或课后练习中的疑惑
        //✅ 用生活化的例子解释抽象概念（比如“类”就像设计图纸，“对象”就是按图造出来的具体房子 🏠）
        //
        //🎯 小建议：刚开始别急着记所有语法，先动手写、多运行、多改错——每个报错信息都是你的“编程教练” 😄
        //
        //那么，张三同学，你想从哪里开始呢？比如：
        //- “我刚装好JDK，但不知道怎么运行第一个程序…”
        //- “什么是main方法？为什么必须是public static void main(String[] args)？”
        //- “能给我一个简单的计算器小练习吗？”
        //- 或者……你手头正有一道不太明白的题目？
        //
        //随时告诉我，我们一步步来，稳扎稳打 💪
        //期待你的回复！😊
        System.out.println("test = " + output.getText());
        Assertions.assertNotNull(output);
    }

}
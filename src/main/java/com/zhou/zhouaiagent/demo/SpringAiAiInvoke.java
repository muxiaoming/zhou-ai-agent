package com.zhou.zhouaiagent.demo;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Spring AI 框架调用 AI 大模型（阿里）
 * 测试用, CommandLineRunner 可以在启动时执行一次, 要注册为bean(放开注解)
 * 可以在测试类中测试 com.zhou.zhouaiagent.demo.SpringAiAiInvokeTest#test()
 */
//@Component
public class SpringAiAiInvoke implements CommandLineRunner {

    @Resource
    private ChatModel dashScopeChatModel;

    @Override
    public void run(String... args) throws Exception {
        AssistantMessage output = dashScopeChatModel.call(new Prompt("你好, 我是java初学者张三"))
                .getResult()
                .getOutput();
        //text = 你好，张三！👋 很高兴认识你这位Java初学者～欢迎踏上编程学习之旅 🌟
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
        //🎯 小建议：学习Java时，多动手写代码、多运行、多调试——遇到报错别怕，那其实是编译器在耐心地帮你“找bug”，也是进步最快的时刻 😄
        //
        //那么，张三同学，你目前学到哪里啦？
        //是刚安装好JDK？还是正在看《Java核心技术》第一章？或者手头正有一道想不通的练习题？欢迎随时告诉我～我们一起搞定它！💪
        //
        //期待你的回复！😊
        System.out.println("text = " + output.getText());

    }
}

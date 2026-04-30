package com.zhou.zhouaiagent.demo;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.ai.rag.Query;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class MultiQueryExpanderDemoTest {

    @Resource
    private MultiQueryExpanderDemo multiQueryExpanderDemo;

    @Test
    void multiQueryExpander() {
        List<Query> expand = multiQueryExpanderDemo.multiQueryExpander("谁是JAVA之父啊?");
        Assertions.assertNotNull(expand);
    }
}
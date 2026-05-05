package com.zhou.zhouaiagent.tools;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PDFGenerationToolTest {

    @Test
    void generatePDF() {
        PDFGenerationTool tool = new PDFGenerationTool();
        String fileName = "恋爱.pdf";
        String content = "为爱而生 https://www.love.cn";
        String result = tool.generatePDF(fileName, content);
        assertNotNull(result);
    }
}
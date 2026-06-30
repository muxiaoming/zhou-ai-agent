package com.zhou.zhouaiagent.tools;

import cn.hutool.core.io.FileUtil;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.zhou.zhouaiagent.constant.FileConstant;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * PDF 生成工具
 */
public class PDFGenerationTool {

    @Tool(description = "Generate a PDF file with given content", returnDirect = false)
    public String generatePDF(
            @ToolParam(description = "Name of the file to save the generated PDF (must be English, like date_plan.pdf)") String fileName,
            @ToolParam(description = "Content to be included in the PDF") String content) {
        String fileDir = FileConstant.FILE_SAVE_DIR + "/pdf";

        // 强制生成英文文件名
        String baseName = fileName.toLowerCase()
                .replaceAll("[^a-z0-9._-]", "_")  // 只保留英文和数字
                .replaceAll("_+", "_")  // 合并多个下划线
                .replaceAll("^_|_$", "");  // 去掉首尾下划线

        if (baseName.isEmpty()) {
            baseName = "document";
        }

        if (!baseName.endsWith(".pdf")) {
            baseName = baseName + ".pdf";
        }

        // 添加时间戳保证文件名唯一
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String safeFileName = baseName.replace(".pdf", "") + "_" + timestamp + ".pdf";

        String filePath = fileDir + "/" + safeFileName;
        try {
            // 创建目录
            FileUtil.mkdir(fileDir);

            // 过滤掉不支持的字符（如 emoji）
            String sanitizedContent = content.replaceAll("[^\\p{L}\\p{N}\\p{P}\\s\\u4e00-\\u9fff]", "");

            // 创建 PdfWriter 和 PdfDocument 对象
            try (PdfWriter writer = new PdfWriter(filePath);
                 PdfDocument pdf = new PdfDocument(writer);
                 Document document = new Document(pdf)) {
                // 使用内置中文字体
                PdfFont font = PdfFontFactory.createFont("STSongStd-Light", "UniGB-UCS2-H");
                document.setFont(font);

                // 按行分割内容，创建多个段落
                String[] lines = sanitizedContent.split("\n");
                for (String line : lines) {
                    if (!line.trim().isEmpty()) {
                        document.add(new Paragraph(line));
                    }
                }
            }
            return "PDF generated successfully to: " + filePath;
        } catch (Exception e) {
            return "Error generating PDF: " + e.getMessage();
        }
    }
}

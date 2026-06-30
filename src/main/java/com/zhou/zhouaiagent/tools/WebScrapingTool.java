package com.zhou.zhouaiagent.tools;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.stream.Collectors;

/**
 * 网页抓取工具
 */
public class WebScrapingTool {

    @Tool(description = "Scrape the content of a web page")
    public String scrapeWebPage(@ToolParam(description = "URL of the web page to scrape") String url) {
        try {
            Document document = Jsoup.connect(url).get();

            // 移除脚本和样式
            document.select("script, style, nav, footer, header").remove();

            // 提取正文内容并转换为 Markdown 格式
            StringBuilder sb = new StringBuilder();

            // 提取标题
            String title = document.title();
            if (title != null && !title.isEmpty()) {
                sb.append("**").append(title).append("**\n\n");
            }

            // 提取主要内容（优先 article 或 main，否则用 body）
            Element content = document.selectFirst("article, main, .content, .post-content, #content");
            if (content == null) {
                content = document.body();
            }

            if (content != null) {
                // 处理标题
                for (int level = 1; level <= 6; level++) {
                    for (Element heading : content.select("h" + level)) {
                        String prefix = "#".repeat(level);
                        sb.append(prefix).append(" ").append(heading.text()).append("\n\n");
                    }
                }

                // 处理段落
                for (Element p : content.select("p")) {
                    String text = p.text().trim();
                    if (!text.isEmpty()) {
                        sb.append(text).append("\n\n");
                    }
                }

                // 处理列表
                for (Element li : content.select("li")) {
                    String text = li.text().trim();
                    if (!text.isEmpty()) {
                        sb.append("- ").append(text).append("\n");
                    }
                }
                sb.append("\n");
            }

            String result = sb.toString().trim();
            // 如果提取的内容太少，返回纯文本
            if (result.length() < 50) {
                result = document.body().text();
                // 每100个字符添加换行
                result = result.replaceAll("(.{100})", "$1\n");
            }

            return result.isEmpty() ? "未提取到有效内容" : result;
        } catch (Exception e) {
            return "❌ 网页抓取失败：" + e.getMessage();
        }
    }
}

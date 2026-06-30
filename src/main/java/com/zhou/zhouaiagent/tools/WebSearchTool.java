package com.zhou.zhouaiagent.tools;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 网页搜索工具
 * 官网 www.searchapi.io, 注册可得使用次数
 */
public class WebSearchTool {

    // SearchAPI 的搜索接口地址
    private static final String SEARCH_API_URL = "https://www.searchapi.io/api/v1/search";

    private final String apiKey;

    public WebSearchTool(String apiKey) {
        this.apiKey = apiKey;
    }

    @Tool(description = "Search for information from Baidu Search Engine")
    public String searchWeb(@ToolParam(description = "Search query keyword") String query) {
        Map<String, Object> paramMap = new HashMap<>();
        paramMap.put("q", query);
        paramMap.put("api_key", apiKey);
        paramMap.put("engine", "baidu");
        try {
            String response = HttpUtil.get(SEARCH_API_URL, paramMap);
            // 取出返回结果的前 5 条
            JSONObject jsonObject = JSONUtil.parseObj(response);
            // 提取 organic_results 部分
            JSONArray organicResults = jsonObject.getJSONArray("organic_results");
            if (JSONUtil.isNull(organicResults)) {
                return "搜索失败, 未搜索到内容。";
            }
            List<Object> objects = organicResults.subList(0, Math.min(5, organicResults.size()));

            // 格式化搜索结果
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < objects.size(); i++) {
                JSONObject item = (JSONObject) objects.get(i);
                String title = item.getStr("title");
                String link = item.getStr("link");
                String displayedLink = item.getStr("displayed_link");
                String snippet = item.getStr("snippet");
                String thumbnail = item.getStr("thumbnail");

                // 添加标题和链接
                sb.append(String.format("**%d. [%s](%s)**\n", i + 1, title, link));
                if (displayedLink != null) {
                    sb.append(String.format("   📎 %s\n", displayedLink));
                }

                // 如果有缩略图，显示图片（使用更小的尺寸）
                if (thumbnail != null && !thumbnail.isEmpty()) {
                    // 确保使用小尺寸缩略图
                    String thumbUrl = thumbnail.contains("?") ? thumbnail + "&w=100&h=100" : thumbnail + "?w=100&h=100";
                    sb.append(String.format("   ![缩略图](%s)\n", thumbUrl));
                }

                // 添加摘要
                if (snippet != null && !snippet.isEmpty()) {
                    String shortSnippet = snippet.length() > 100 ? snippet.substring(0, 100) + "..." : snippet;
                    sb.append(String.format("   %s\n\n", shortSnippet));
                }
            }

            return sb.toString();
        } catch (Exception e) {
            return "❌ 搜索失败：" + e.getMessage();
        }
    }
}

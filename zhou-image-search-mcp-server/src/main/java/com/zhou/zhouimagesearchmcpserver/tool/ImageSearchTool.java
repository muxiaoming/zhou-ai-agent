package com.zhou.zhouimagesearchmcpserver.tool;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ImageSearchTool {

    // 替换为真实的的 Pexels API 密钥（需从官网申请）
    @Value("${pexels.apikey}")
    private String API_KEY;

    // Pexels 常规搜索接口（请以文档为准）
    private static final String API_URL = "https://api.pexels.com/v1/search";

    @Tool(description = "search image from web")
    public String searchImage(@ToolParam(description = "Search query keyword") String query) {
        try {
            List<Map<String, String>> images = searchImagesWithInfo(query);

            if (images.isEmpty()) {
                return "未找到相关图片";
            }

            // 格式化为 Markdown，限制显示5张
            int maxShow = Math.min(5, images.size());
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("**🖼️ 找到 %d 张图片：**\n\n", images.size()));

            for (int i = 0; i < maxShow; i++) {
                Map<String, String> imageInfo = images.get(i);
                String url = imageInfo.get("medium");
                String photographer = imageInfo.get("photographer");
                String alt = imageInfo.get("alt");

                if (url != null && !url.isBlank()) {
                    // 使用更有意义的 alt 文本
                    String altText = (alt != null && !alt.isEmpty()) ? alt : "图片" + (i + 1);
                    if (altText.length() > 50) {
                        altText = altText.substring(0, 50) + "...";
                    }

                    sb.append(String.format("![%s](%s)\n", altText, url));

                    // 添加图片说明
                    if (photographer != null && !photographer.isEmpty()) {
                        sb.append(String.format("*📷 摄影师: %s*\n\n", photographer));
                    } else {
                        sb.append("\n");
                    }
                }
            }

            // 如果有更多图片，添加提示
            if (images.size() > maxShow) {
                sb.append(String.format("*...还有 %d 张图片*\n\n", images.size() - maxShow));
            }

            return sb.toString();
        } catch (Exception e) {
            return "❌ 图片搜索失败：" + e.getMessage();
        }
    }

    /**
     * 搜索图片并返回详细信息
     *
     * @param query 搜索关键词
     * @return 图片信息列表（包含medium URL, photographer, alt）
     */
    public List<Map<String, String>> searchImagesWithInfo(String query) {
        // 设置请求头（包含API密钥）
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", API_KEY);

        // 设置请求参数
        Map<String, Object> params = new HashMap<>();
        params.put("query", query);

        // 发送 GET 请求
        String response = HttpUtil.createGet(API_URL)
                .addHeaders(headers)
                .form(params)
                .execute()
                .body();

        // 解析响应JSON，提取图片信息
        return JSONUtil.parseObj(response)
                .getJSONArray("photos")
                .stream()
                .map(photoObj -> (JSONObject) photoObj)
                .map(photo -> {
                    Map<String, String> info = new HashMap<>();
                    JSONObject src = photo.getJSONObject("src");
                    if (src != null) {
                        info.put("medium", src.getStr("medium"));
                        info.put("large", src.getStr("large"));
                        info.put("original", src.getStr("original"));
                    }
                    info.put("photographer", photo.getStr("photographer"));
                    info.put("alt", photo.getStr("alt"));
                    return info;
                })
                .filter(info -> info.get("medium") != null && !info.get("medium").isEmpty())
                .collect(Collectors.toList());
    }

    /**
     * 搜索中等尺寸的图片列表（兼容旧接口）
     *
     * @param query 搜索关键词
     * @return 图片URL列表
     */
    public List<String> searchMediumImages(String query) {
        return searchImagesWithInfo(query).stream()
                .map(info -> info.get("medium"))
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toList());
    }
}

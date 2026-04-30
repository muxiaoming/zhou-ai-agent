package com.zhou.zhouaiagent.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 AI 的文档元信息增强器（为文档补充元信息）
 */
@Component
public class MyKeywordEnricher {

    @Resource
    private ChatModel dashscopeChatModel;

    public List<Document> enrichDocuments(List<Document> documents) {
        KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(dashscopeChatModel, 5);
        // 为文档补充元信息 (可能会有英文)
        // {"title": "如何提升自身魅力吸引潜在伴侣？", "category": "header_4", "filename": "恋爱常见问题和回答 - 单身篇.md", "excerpt_keywords": "personal grooming, confidence building, social skills, style development, holistic self-improvement"}
        // {"title": "线上交友有哪些注意事项能提高脱单成功率？", "category": "header_4", "filename": "恋爱常见问题和回答 - 单身篇.md", "excerpt_keywords": "线上交友,个人资料优化,聊天技巧,隐私保护,脱单指南"}
        return  keywordMetadataEnricher.apply(documents);
    }
}
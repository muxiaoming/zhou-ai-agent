package com.zhou.zhouaiagent.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 基于 JDBC 的 ChatMemoryRepository 实现
 * 使用 PostgreSQL 存储对话记忆，支持服务重启后会话恢复
 *
 * 存储策略：将消息拆分为 type + content + metadata 三列存储，
 * 避免 Java 序列化的类兼容性问题。
 */
public class JdbcChatMemoryRepository implements ChatMemoryRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcChatMemoryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
        initSchema();
    }

    private void initSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS ai_chat_memory (
                    id BIGSERIAL PRIMARY KEY,
                    conversation_id VARCHAR(255) NOT NULL,
                    message_type VARCHAR(50) NOT NULL,
                    content TEXT NOT NULL,
                    metadata TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_ai_chat_memory_conversation_id
                ON ai_chat_memory(conversation_id, id)
                """);
    }

    @Override
    public List<String> findConversationIds() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT conversation_id FROM ai_chat_memory",
                String.class
        );
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        List<Message> messages = jdbcTemplate.query(
                "SELECT message_type, content, metadata FROM ai_chat_memory WHERE conversation_id = ? ORDER BY id ASC",
                messageRowMapper(),
                conversationId
        );
        return messages != null ? messages : Collections.emptyList();
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        // 先删除该会话的旧消息，再保存新消息（全量替换策略）
        jdbcTemplate.update("DELETE FROM ai_chat_memory WHERE conversation_id = ?", conversationId);
        for (Message message : messages) {
            String type = message.getMessageType().getValue();
            String content = message.getText();
            String metadata = serializeMetadata(message.getMetadata());
            jdbcTemplate.update(
                    "INSERT INTO ai_chat_memory (conversation_id, message_type, content, metadata, created_at) VALUES (?, ?, ?, ?, ?)",
                    conversationId, type, content, metadata, Timestamp.from(Instant.now())
            );
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        jdbcTemplate.update("DELETE FROM ai_chat_memory WHERE conversation_id = ?", conversationId);
    }

    private RowMapper<Message> messageRowMapper() {
        return (ResultSet rs, int rowNum) -> {
            String type = rs.getString("message_type");
            String content = rs.getString("content");
            String metadataJson = rs.getString("metadata");
            Map<String, Object> metadata = deserializeMetadata(metadataJson);

            return switch (MessageType.fromValue(type)) {
                case USER -> UserMessage.builder()
                        .text(content)
                        .metadata(metadata)
                        .build();
                case ASSISTANT -> new AssistantMessage(content, metadata);
                case SYSTEM -> SystemMessage.builder()
                        .text(content)
                        .metadata(metadata)
                        .build();
                case TOOL -> new AssistantMessage(content, metadata);
            };
        };
    }

    private String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Map<String, Object> deserializeMetadata(String json) {
        if (json == null || json.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, HashMap.class);
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }
}

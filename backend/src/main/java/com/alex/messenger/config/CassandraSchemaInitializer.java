package com.alex.messenger.config;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CassandraSchemaInitializer {

    private final CqlSession cqlSession;

    @Value("${spring.cassandra.keyspace-name}")
    private String keyspaceName;

    @PostConstruct
    void initialize() {
        cqlSession.execute(createMessagesByChatTableStatement());
        cqlSession.execute(createMessagesByIdTableStatement());
        cqlSession.execute(createMessagesByTopicTableStatement());
        cqlSession.execute(createMessagesByThreadTableStatement());
        applyCompatibilityAlterations();
    }

    private void applyCompatibilityAlterations() {
        executeSchemaChange("ALTER TABLE %s.messages_by_chat_v2 ADD via_bot_user_id UUID".formatted(keyspaceName));
        executeSchemaChange("ALTER TABLE %s.messages_by_id_v2 ADD via_bot_user_id UUID".formatted(keyspaceName));
        executeSchemaChange("ALTER TABLE %s.messages_by_topic_v1 ADD via_bot_user_id UUID".formatted(keyspaceName));
        executeSchemaChange("ALTER TABLE %s.messages_by_thread_v1 ADD via_bot_user_id UUID".formatted(keyspaceName));
    }

    private void executeSchemaChange(String cql) {
        try {
            cqlSession.execute(cql);
        } catch (RuntimeException ignored) {
        }
    }

    private String createMessagesByChatTableStatement() {
        return """
                CREATE TABLE IF NOT EXISTS %s.messages_by_chat_v2 (
                    chat_id UUID,
                    message_id TIMEUUID,
                    created_at TIMESTAMP,
                    sender_id UUID,
                    recipient_id UUID,
                    via_bot_user_id UUID,
                    topic_id UUID,
                    thread_root_message_id UUID,
                    discussion_chat_id UUID,
                    discussion_root_message_id UUID,
                    ciphertext TEXT,
                    nonce TEXT,
                    key_version INT,
                    reply_to_message_id UUID,
                    forwarded_from_chat_id UUID,
                    forwarded_from_message_id UUID,
                    poll_id UUID,
                    sticker_id UUID,
                    attachment_ids LIST<UUID>,
                    delivery_status TEXT,
                    delivered_at TIMESTAMP,
                    read_at TIMESTAMP,
                    expires_at TIMESTAMP,
                    edited_at TIMESTAMP,
                    deleted_at TIMESTAMP,
                    PRIMARY KEY ((chat_id), message_id)
                ) WITH CLUSTERING ORDER BY (message_id DESC)
                    AND compaction = {
                        'class': 'TimeWindowCompactionStrategy',
                        'compaction_window_size': '1',
                        'compaction_window_unit': 'DAYS'
                    }
                    AND compression = {
                        'class': 'LZ4Compressor'
                    }
                """.formatted(keyspaceName);
    }

    private String createMessagesByIdTableStatement() {
        return """
                CREATE TABLE IF NOT EXISTS %s.messages_by_id_v2 (
                    message_id TIMEUUID PRIMARY KEY,
                    chat_id UUID,
                    created_at TIMESTAMP,
                    sender_id UUID,
                    recipient_id UUID,
                    via_bot_user_id UUID,
                    topic_id UUID,
                    thread_root_message_id UUID,
                    discussion_chat_id UUID,
                    discussion_root_message_id UUID,
                    ciphertext TEXT,
                    nonce TEXT,
                    key_version INT,
                    reply_to_message_id UUID,
                    forwarded_from_chat_id UUID,
                    forwarded_from_message_id UUID,
                    poll_id UUID,
                    sticker_id UUID,
                    attachment_ids LIST<UUID>,
                    delivery_status TEXT,
                    delivered_at TIMESTAMP,
                    read_at TIMESTAMP,
                    expires_at TIMESTAMP,
                    edited_at TIMESTAMP,
                    deleted_at TIMESTAMP
                ) WITH compression = {
                    'class': 'LZ4Compressor'
                }
                """.formatted(keyspaceName);
    }

    private String createMessagesByTopicTableStatement() {
        return """
                CREATE TABLE IF NOT EXISTS %s.messages_by_topic_v1 (
                    topic_id UUID,
                    message_id TIMEUUID,
                    chat_id UUID,
                    created_at TIMESTAMP,
                    sender_id UUID,
                    recipient_id UUID,
                    via_bot_user_id UUID,
                    thread_root_message_id UUID,
                    discussion_chat_id UUID,
                    discussion_root_message_id UUID,
                    ciphertext TEXT,
                    nonce TEXT,
                    key_version INT,
                    reply_to_message_id UUID,
                    forwarded_from_chat_id UUID,
                    forwarded_from_message_id UUID,
                    poll_id UUID,
                    sticker_id UUID,
                    attachment_ids LIST<UUID>,
                    delivery_status TEXT,
                    delivered_at TIMESTAMP,
                    read_at TIMESTAMP,
                    expires_at TIMESTAMP,
                    edited_at TIMESTAMP,
                    deleted_at TIMESTAMP,
                    PRIMARY KEY ((topic_id), message_id)
                ) WITH CLUSTERING ORDER BY (message_id DESC)
                    AND compaction = {
                        'class': 'TimeWindowCompactionStrategy',
                        'compaction_window_size': '1',
                        'compaction_window_unit': 'DAYS'
                    }
                    AND compression = {
                        'class': 'LZ4Compressor'
                    }
                """.formatted(keyspaceName);
    }

    private String createMessagesByThreadTableStatement() {
        return """
                CREATE TABLE IF NOT EXISTS %s.messages_by_thread_v1 (
                    thread_root_message_id UUID,
                    message_id TIMEUUID,
                    chat_id UUID,
                    created_at TIMESTAMP,
                    sender_id UUID,
                    recipient_id UUID,
                    via_bot_user_id UUID,
                    topic_id UUID,
                    discussion_chat_id UUID,
                    discussion_root_message_id UUID,
                    ciphertext TEXT,
                    nonce TEXT,
                    key_version INT,
                    reply_to_message_id UUID,
                    forwarded_from_chat_id UUID,
                    forwarded_from_message_id UUID,
                    poll_id UUID,
                    sticker_id UUID,
                    attachment_ids LIST<UUID>,
                    delivery_status TEXT,
                    delivered_at TIMESTAMP,
                    read_at TIMESTAMP,
                    expires_at TIMESTAMP,
                    edited_at TIMESTAMP,
                    deleted_at TIMESTAMP,
                    PRIMARY KEY ((thread_root_message_id), message_id)
                ) WITH CLUSTERING ORDER BY (message_id DESC)
                    AND compaction = {
                        'class': 'TimeWindowCompactionStrategy',
                        'compaction_window_size': '1',
                        'compaction_window_unit': 'DAYS'
                    }
                    AND compression = {
                        'class': 'LZ4Compressor'
                    }
                """.formatted(keyspaceName);
    }
}

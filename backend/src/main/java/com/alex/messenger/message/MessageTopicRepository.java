package com.alex.messenger.message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

public interface MessageTopicRepository extends CassandraRepository<MessageTopicEntity, MessageTopicPrimaryKey> {

    @Query("SELECT * FROM messages_by_topic_v1 WHERE topic_id = ?0 LIMIT ?1")
    List<MessageTopicEntity> findRecentByTopicId(UUID topicId, int limit);

    @Query("SELECT * FROM messages_by_topic_v1 WHERE topic_id = ?0 AND message_id < maxTimeuuid(?1) LIMIT ?2")
    List<MessageTopicEntity> findRecentByTopicIdBefore(UUID topicId, Instant before, int limit);

    @Query("SELECT * FROM messages_by_topic_v1 WHERE topic_id = ?0")
    List<MessageTopicEntity> findAllByTopicId(UUID topicId);
}

package com.alex.messenger.message;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@PrimaryKeyClass
public class MessageTopicPrimaryKey implements Serializable {

    @PrimaryKeyColumn(name = "topic_id", type = PrimaryKeyType.PARTITIONED)
    private UUID topicId;

    @PrimaryKeyColumn(name = "message_id", ordinal = 0, type = PrimaryKeyType.CLUSTERED)
    private UUID messageId;
}

package com.alex.messenger.message;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.CassandraType.Name;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@PrimaryKeyClass
public class MessageThreadPrimaryKey implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @PrimaryKeyColumn(name = "thread_root_message_id", type = PrimaryKeyType.PARTITIONED)
    @CassandraType(type = Name.UUID)
    private UUID threadRootMessageId;

    @PrimaryKeyColumn(name = "message_id", ordinal = 0, type = PrimaryKeyType.CLUSTERED)
    @CassandraType(type = Name.TIMEUUID)
    private UUID messageId;
}

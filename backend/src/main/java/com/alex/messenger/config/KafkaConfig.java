package com.alex.messenger.config;

import com.alex.messenger.chat.dto.ChatReadEventResponse;
import com.alex.messenger.chat.dto.PinMessageEventResponse;
import com.alex.messenger.chat.dto.TypingEventResponse;
import com.alex.messenger.message.MessageEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean
    public ProducerFactory<String, MessageEvent> producerFactory(KafkaProperties properties) {
        return buildJsonProducerFactory(properties);
    }

    @Bean
    public KafkaTemplate<String, MessageEvent> kafkaTemplate(ProducerFactory<String, MessageEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public ProducerFactory<String, TypingEventResponse> typingEventProducerFactory(KafkaProperties properties) {
        return buildJsonProducerFactory(properties);
    }

    @Bean
    public KafkaTemplate<String, TypingEventResponse> typingEventKafkaTemplate(
            ProducerFactory<String, TypingEventResponse> typingEventProducerFactory
    ) {
        return new KafkaTemplate<>(typingEventProducerFactory);
    }

    @Bean
    public ProducerFactory<String, ChatReadEventResponse> readEventProducerFactory(KafkaProperties properties) {
        return buildJsonProducerFactory(properties);
    }

    @Bean
    public KafkaTemplate<String, ChatReadEventResponse> readEventKafkaTemplate(
            ProducerFactory<String, ChatReadEventResponse> readEventProducerFactory
    ) {
        return new KafkaTemplate<>(readEventProducerFactory);
    }

    @Bean
    public ProducerFactory<String, PinMessageEventResponse> pinEventProducerFactory(KafkaProperties properties) {
        return buildJsonProducerFactory(properties);
    }

    @Bean
    public KafkaTemplate<String, PinMessageEventResponse> pinEventKafkaTemplate(
            ProducerFactory<String, PinMessageEventResponse> pinEventProducerFactory
    ) {
        return new KafkaTemplate<>(pinEventProducerFactory);
    }

    @Bean
    public ConsumerFactory<String, MessageEvent> consumerFactory(KafkaProperties properties) {
        return buildJsonConsumerFactory(properties, MessageEvent.class, "com.alex.messenger.message", null);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MessageEvent> kafkaListenerContainerFactory(
            ConsumerFactory<String, MessageEvent> consumerFactory,
            @Value("${alex.kafka.listener-concurrency}") int listenerConcurrency
    ) {
        return buildListenerFactory(consumerFactory, listenerConcurrency, 1_000L, 3L);
    }

    @Bean
    public ConsumerFactory<String, TypingEventResponse> typingEventConsumerFactory(KafkaProperties properties) {
        return buildJsonConsumerFactory(properties, TypingEventResponse.class, "com.alex.messenger.chat.dto", "latest");
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TypingEventResponse> typingKafkaListenerContainerFactory(
            ConsumerFactory<String, TypingEventResponse> typingEventConsumerFactory,
            @Value("${alex.kafka.listener-concurrency}") int listenerConcurrency
    ) {
        return buildListenerFactory(typingEventConsumerFactory, listenerConcurrency, 500L, 5L);
    }

    @Bean
    public ConsumerFactory<String, ChatReadEventResponse> readEventConsumerFactory(KafkaProperties properties) {
        return buildJsonConsumerFactory(properties, ChatReadEventResponse.class, "com.alex.messenger.chat.dto", "latest");
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ChatReadEventResponse> readKafkaListenerContainerFactory(
            ConsumerFactory<String, ChatReadEventResponse> readEventConsumerFactory,
            @Value("${alex.kafka.listener-concurrency}") int listenerConcurrency
    ) {
        return buildListenerFactory(readEventConsumerFactory, listenerConcurrency, 500L, 5L);
    }

    @Bean
    public ConsumerFactory<String, PinMessageEventResponse> pinEventConsumerFactory(KafkaProperties properties) {
        return buildJsonConsumerFactory(properties, PinMessageEventResponse.class, "com.alex.messenger.chat.dto", "latest");
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PinMessageEventResponse> pinKafkaListenerContainerFactory(
            ConsumerFactory<String, PinMessageEventResponse> pinEventConsumerFactory,
            @Value("${alex.kafka.listener-concurrency}") int listenerConcurrency
    ) {
        return buildListenerFactory(pinEventConsumerFactory, listenerConcurrency, 500L, 5L);
    }

    @Bean
    public NewTopic chatMessagesTopic(
            @Value("${alex.kafka.chat-messages-topic}") String topicName,
            @Value("${alex.kafka.chat-messages-partitions}") int partitions,
            @Value("${alex.kafka.chat-messages-replication-factor}") short replicationFactor
    ) {
        return buildTopic(topicName, partitions, replicationFactor, 7L * 24L * 60L * 60L * 1000L);
    }

    @Bean
    public NewTopic chatTypingEventsTopic(
            @Value("${alex.kafka.chat-typing-events-topic}") String topicName,
            @Value("${alex.kafka.chat-typing-events-partitions}") int partitions,
            @Value("${alex.kafka.chat-typing-events-replication-factor}") short replicationFactor
    ) {
        return buildTopic(topicName, partitions, replicationFactor, 5L * 60L * 1000L);
    }

    @Bean
    public NewTopic chatReadEventsTopic(
            @Value("${alex.kafka.chat-read-events-topic}") String topicName,
            @Value("${alex.kafka.chat-read-events-partitions}") int partitions,
            @Value("${alex.kafka.chat-read-events-replication-factor}") short replicationFactor
    ) {
        return buildTopic(topicName, partitions, replicationFactor, 30L * 60L * 1000L);
    }

    @Bean
    public NewTopic chatPinEventsTopic(
            @Value("${alex.kafka.chat-pin-events-topic}") String topicName,
            @Value("${alex.kafka.chat-pin-events-partitions}") int partitions,
            @Value("${alex.kafka.chat-pin-events-replication-factor}") short replicationFactor
    ) {
        return buildTopic(topicName, partitions, replicationFactor, 30L * 60L * 1000L);
    }

    private <T> ProducerFactory<String, T> buildJsonProducerFactory(KafkaProperties properties) {
        Map<String, Object> config = new HashMap<>(properties.buildProducerProperties());
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        config.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(config);
    }

    private <T> ConsumerFactory<String, T> buildJsonConsumerFactory(
            KafkaProperties properties,
            Class<T> payloadType,
            String trustedPackages,
            String autoOffsetReset
    ) {
        Map<String, Object> config = new HashMap<>(properties.buildConsumerProperties());
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, trustedPackages);
        config.put(JsonDeserializer.VALUE_DEFAULT_TYPE, payloadType.getName());
        config.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        if (autoOffsetReset != null) {
            config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, autoOffsetReset);
        }
        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                new JsonDeserializer<>(payloadType, false)
        );
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> buildListenerFactory(
            ConsumerFactory<String, T> consumerFactory,
            int listenerConcurrency,
            long backOffIntervalMs,
            long maxAttempts
    ) {
        ConcurrentKafkaListenerContainerFactory<String, T> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(listenerConcurrency);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(backOffIntervalMs, maxAttempts)));
        return factory;
    }

    private NewTopic buildTopic(
            String topicName,
            int partitions,
            short replicationFactor,
            long retentionMs
    ) {
        return TopicBuilder.name(topicName)
                .partitions(partitions)
                .replicas(replicationFactor)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, TopicConfig.CLEANUP_POLICY_DELETE)
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, replicationFactor > 1 ? "2" : "1")
                .config(TopicConfig.RETENTION_MS_CONFIG, Long.toString(retentionMs))
                .build();
    }
}

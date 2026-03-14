package com.alex.messenger.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.cassandra.CassandraContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class BackendInfrastructureSmokeTest {

    @Container
    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @Container
    @SuppressWarnings("resource")
    private static final CassandraContainer CASSANDRA =
            new CassandraContainer(DockerImageName.parse("cassandra:5.0"));

    @Container
    @SuppressWarnings("resource")
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"));

    @Container
    @SuppressWarnings("resource")
    private static final GenericContainer<?> MINIO = new GenericContainer<>(DockerImageName.parse(
            "minio/minio:RELEASE.2025-09-07T16-13-09Z-cpuv1"
    ))
            .withEnv("MINIO_ROOT_USER", "alexminio")
            .withEnv("MINIO_ROOT_PASSWORD", "alexminio-secret")
            .withCommand("server", "/data")
            .withExposedPorts(9000)
            .withStartupTimeout(Duration.ofMinutes(2));

    @BeforeAll
    static void initializeCassandraKeyspace() throws Exception {
        if (CASSANDRA.isRunning()) {
            CASSANDRA.execInContainer(
                    "cqlsh",
                    "-e",
                    "CREATE KEYSPACE IF NOT EXISTS alex_messenger WITH replication = {'class':'SimpleStrategy','replication_factor':1};"
            );
        }
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.cassandra.contact-points", CASSANDRA::getHost);
        registry.add("spring.cassandra.port", () -> CASSANDRA.getMappedPort(9042));
        registry.add("spring.cassandra.keyspace-name", () -> "alex_messenger");
        registry.add("spring.cassandra.local-datacenter", CASSANDRA::getLocalDatacenter);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("alex.media.s3.endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("alex.media.s3.public-endpoint", () -> "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000));
        registry.add("alex.features.lawful-direct-export", () -> true);
    }

    @Test
    void contextLoads() {
        assertThat(POSTGRES.isRunning()).isTrue();
        assertThat(CASSANDRA.isRunning()).isTrue();
        assertThat(KAFKA.isRunning()).isTrue();
        assertThat(MINIO.isRunning()).isTrue();
    }
}

package com.alex.messenger.media;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MediaStorageConfig {

    @Bean
    public MinioClient mediaStorageMinioClient(
            @Value("${alex.media.s3.endpoint}") String endpoint,
            @Value("${alex.media.s3.access-key}") String accessKey,
            @Value("${alex.media.s3.secret-key}") String secretKey
    ) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    @Qualifier("mediaPresignMinioClient")
    public MinioClient mediaPresignMinioClient(
            @Value("${alex.media.s3.public-endpoint}") String publicEndpoint,
            @Value("${alex.media.s3.access-key}") String accessKey,
            @Value("${alex.media.s3.secret-key}") String secretKey
    ) {
        return MinioClient.builder()
                .endpoint(publicEndpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}

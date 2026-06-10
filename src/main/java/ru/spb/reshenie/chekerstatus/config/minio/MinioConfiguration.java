package ru.spb.reshenie.chekerstatus.config.minio;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class MinioConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
    public MinioClient minioClient(MinioProperties properties) {
        requireText(properties.getEndpoint(), "MINIO_ENDPOINT");
        requireText(properties.getAccessKey(), "MINIO_ACCESS_KEY");
        requireText(properties.getSecretKey(), "MINIO_SECRET_KEY");
        requireText(properties.getBucket(), "MINIO_BUCKET");
        return MinioClient.builder()
                .endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey())
                .build();
    }

    private void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " must be configured when MinIO storage is enabled");
        }
    }
}

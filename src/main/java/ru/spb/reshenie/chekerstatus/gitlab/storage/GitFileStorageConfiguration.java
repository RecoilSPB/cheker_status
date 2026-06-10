package ru.spb.reshenie.chekerstatus.gitlab.storage;

import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.spb.reshenie.chekerstatus.config.minio.MinioProperties;

@Configuration
public class GitFileStorageConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "minio", name = "enabled", havingValue = "true")
    public GitFileStorage minioGitFileStorage(MinioClient minioClient, MinioProperties properties) {
        return new MinioGitFileStorage(minioClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean(GitFileStorage.class)
    public GitFileStorage noopGitFileStorage() {
        return new NoopGitFileStorage();
    }
}

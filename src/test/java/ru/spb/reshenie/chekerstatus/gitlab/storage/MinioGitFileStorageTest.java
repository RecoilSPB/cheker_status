package ru.spb.reshenie.chekerstatus.gitlab.storage;

import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import ru.spb.reshenie.chekerstatus.config.minio.MinioProperties;
import ru.spb.reshenie.chekerstatus.gitlab.model.DocumentGitLink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MinioGitFileStorageTest {

    @Test
    void objectKeyUsesShortAsciiSafePathForUnicodeFileNames() {
        MinioProperties properties = new MinioProperties();
        properties.setObjectPrefix("gitlab");
        MinioGitFileStorage storage = new MinioGitFileStorage(mock(MinioClient.class), properties);

        String objectKey = storage.objectKey(
                new DocumentGitLink(1L, 1L, 1L, "record", "1.2.3", "Document", "url",
                        "git.minzdrav.gov.ru", "semd/test project", "main"),
                "docs/schema file.xml",
                "abc123",
                "95d205945896e72681a357274b2d32d40af43d5059c496edff63bfc26a5f0687"
        );

        assertThat(objectKey)
                .startsWith("gitlab/git-link-1/ref-abc123/95d205945896e72681a357274b2d32d40af43d5059c496edff63bfc26a5f0687-")
                .endsWith(".xml");
        assertThat(objectKey).doesNotContain("%");
    }

    @Test
    void objectKeyStaysCompactForLongCyrillicPaths() {
        MinioProperties properties = new MinioProperties();
        properties.setObjectPrefix("gitlab");
        MinioGitFileStorage storage = new MinioGitFileStorage(mock(MinioClient.class), properties);

        String objectKey = storage.objectKey(
                new DocumentGitLink(77L, 1L, 1L, "record", "1.2.3", "Document", "url",
                        "git.minzdrav.gov.ru", "semd/test project", "main"),
                "XSD_CDA_НАПРАВЛЕНИЕ_НА_ПАТОЛОГО_АНАТОМИЧЕСКОЕ_ВСКРЫТИЕ_Р1/CDA.xsd",
                "ede6d72b8f3fa7963e4567b2f767ef430fb72a92",
                "95d205945896e72681a357274b2d32d40af43d5059c496edff63bfc26a5f0687"
        );

        assertThat(objectKey)
                .startsWith("gitlab/git-link-77/ref-ede6d72b8f3fa7963e4567b2f767ef430fb72a92/95d205945896e72681a357274b2d32d40af43d5059c496edff63bfc26a5f0687-")
                .endsWith(".xsd");
        assertThat(objectKey.length()).isLessThan(180);
        assertThat(objectKey).doesNotContain("%");
    }
}

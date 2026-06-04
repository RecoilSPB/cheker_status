package ru.spb.reshenie.chekerstatus.gitlab.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class GitContentUtilsTest {

    @Test
    void calculatesSha256() {
        String hash = GitContentUtils.sha256("abc".getBytes(StandardCharsets.UTF_8));

        assertThat(hash).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void detectsBinaryContent() {
        assertThat(GitContentUtils.looksBinary(new byte[]{'a', 0, 'b'})).isTrue();
    }
}

package ru.spb.reshenie.chekerstatus.gitlab;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitLabUrlEncoderTest {

    @Test
    void encodesProjectPath() {
        String encoded = GitLabUrlEncoder.encodeProjectPath("semd/1.2.643/test project");

        assertThat(encoded).isEqualTo("semd%2F1.2.643%2Ftest%20project");
    }

    @Test
    void encodesFilePath() {
        String encoded = GitLabUrlEncoder.encodeFilePath("docs/examples/file name.xml");

        assertThat(encoded).isEqualTo("docs%2Fexamples%2Ffile%20name.xml");
    }
}

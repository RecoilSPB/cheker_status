package ru.spb.reshenie.chekerstatus.gitlab.parser;

import ru.spb.reshenie.chekerstatus.gitlab.model.GitLabLink;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitLabLinkParserTest {

    private final GitLabLinkParser parser = new GitLabLinkParser();

    @Test
    void parsesNsiGitLabTreeLink() {
        GitLabLink link = parser.parse("https://git.minzdrav.gov.ru/semd/1.2.643.5.1.13.13.15.98/-/tree/1.2.643.5.1.13.13.15.98.1");

        assertThat(link).isNotNull();
        assertThat(link.getHost()).isEqualTo("git.minzdrav.gov.ru");
        assertThat(link.getProjectPath()).isEqualTo("semd/1.2.643.5.1.13.13.15.98");
        assertThat(link.getTreeRef()).isEqualTo("1.2.643.5.1.13.13.15.98.1");
    }

    @Test
    void parsesNsiGitLabBlobLink() {
        GitLabLink link = parser.parse("https://git.minzdrav.gov.ru/semd/1.2.643.5.1.13.13.15.160/-/blob/1.2.643.5.1.13.13.15.160.1");

        assertThat(link).isNotNull();
        assertThat(link.getProjectPath()).isEqualTo("semd/1.2.643.5.1.13.13.15.160");
        assertThat(link.getTreeRef()).isEqualTo("1.2.643.5.1.13.13.15.160.1");
    }

    @Test
    void returnsNullForUnsupportedLink() {
        GitLabLink link = parser.parse("https://git.minzdrav.gov.ru/users/sign_in");

        assertThat(link).isNull();
    }
}

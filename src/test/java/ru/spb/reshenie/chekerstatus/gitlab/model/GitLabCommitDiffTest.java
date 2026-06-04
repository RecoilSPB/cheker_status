package ru.spb.reshenie.chekerstatus.gitlab.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitLabCommitDiffTest {

    @Test
    void detectsAddedChangeType() {
        GitLabCommitDiff diff = new GitLabCommitDiff(null, "a.xml", "", true, false, false, false, null);

        assertThat(diff.changeType()).isEqualTo("added");
    }

    @Test
    void detectsDeletedChangeType() {
        GitLabCommitDiff diff = new GitLabCommitDiff("a.xml", "a.xml", "", false, false, true, false, null);

        assertThat(diff.changeType()).isEqualTo("deleted");
    }

    @Test
    void detectsRenamedChangeType() {
        GitLabCommitDiff diff = new GitLabCommitDiff("a.xml", "b.xml", "", false, true, false, false, null);

        assertThat(diff.changeType()).isEqualTo("renamed");
    }

    @Test
    void detectsModifiedChangeType() {
        GitLabCommitDiff diff = new GitLabCommitDiff("a.xml", "a.xml", "", false, false, false, false, null);

        assertThat(diff.changeType()).isEqualTo("modified");
    }
}

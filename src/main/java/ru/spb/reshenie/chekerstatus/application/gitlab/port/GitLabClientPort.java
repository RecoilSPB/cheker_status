package ru.spb.reshenie.chekerstatus.application.gitlab.port;

import ru.spb.reshenie.chekerstatus.domain.gitlab.DocumentGitLink;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitLabCommit;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitLabCommitDetails;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitLabCommitDiff;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitLabFileContentResult;

import java.util.List;

public interface GitLabClientPort {

    List<GitLabCommit> fetchCommitsUntilKnown(DocumentGitLink document, String stopCommitSha);

    GitLabCommitDetails fetchCommitDetails(DocumentGitLink document, String commitSha);

    List<GitLabCommitDiff> fetchCommitDiff(DocumentGitLink document, String commitSha);

    GitLabFileContentResult fetchRawFile(DocumentGitLink document, String filePath, String ref);
}

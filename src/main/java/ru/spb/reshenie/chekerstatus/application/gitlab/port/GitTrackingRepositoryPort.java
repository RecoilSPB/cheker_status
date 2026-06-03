package ru.spb.reshenie.chekerstatus.application.gitlab.port;

import ru.spb.reshenie.chekerstatus.domain.gitlab.DocumentGitLink;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitLabCommit;
import ru.spb.reshenie.chekerstatus.domain.gitlab.StoredGitLabCommit;

import java.util.List;

public interface GitTrackingRepositoryPort {

    List<DocumentGitLink> refreshAndFindActiveLinks(long dictionaryId);

    int saveCommits(long documentGitLinkId, List<GitLabCommit> commits);

    void markSyncSuccess(long documentGitLinkId);

    void markSyncError(long documentGitLinkId, String error);

    List<StoredGitLabCommit> findCommitsPendingFileHistory(long documentGitLinkId);

    String findLatestStoredCommitSha(long documentGitLinkId);

    void markCommitFileHistorySuccess(long commitRowId);

    void markCommitFileHistoryFailed(long commitRowId, String error);
}

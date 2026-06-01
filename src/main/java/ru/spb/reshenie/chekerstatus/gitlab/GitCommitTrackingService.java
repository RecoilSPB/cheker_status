package ru.spb.reshenie.chekerstatus.gitlab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.spb.reshenie.chekerstatus.config.GitLabProperties;

import java.util.List;

@Service
public class GitCommitTrackingService {

    private static final Logger log = LoggerFactory.getLogger(GitCommitTrackingService.class);

    private final GitLabProperties properties;
    private final GitLabClient gitLabClient;
    private final GitTrackingRepository repository;

    public GitCommitTrackingService(GitLabProperties properties,
                                    GitLabClient gitLabClient,
                                    GitTrackingRepository repository) {
        this.properties = properties;
        this.gitLabClient = gitLabClient;
        this.repository = repository;
    }

    public void synchronizeDictionaryDocuments(long dictionaryId) {
        if (!properties.isEnabled()) {
            log.info("GitLab commit tracking is disabled");
            return;
        }

        List<DocumentGitLink> documents = repository.refreshAndFindActiveLinks(dictionaryId);
        if (documents.isEmpty()) {
            log.info("No active GitLab document links found: dictionaryId={}", dictionaryId);
            return;
        }

        if (properties.isRequireToken() && !StringUtils.hasText(properties.getToken())) {
            String error = "GitLab token is not configured";
            for (DocumentGitLink document : documents) {
                repository.markSyncError(document.getId(), error);
            }
            log.warn("{}: dictionaryId={}, documents={}", error, dictionaryId, documents.size());
            return;
        }

        int success = 0;
        int failed = 0;
        int commits = 0;
        int processed = 0;
        int limit = properties.getMaxDocumentsPerRun();
        for (DocumentGitLink document : documents) {
            if (limit > 0 && processed >= limit) {
                break;
            }
            processed++;
            try {
                List<GitLabCommit> documentCommits = gitLabClient.fetchAllCommits(document);
                repository.saveCommits(document.getId(), documentCommits);
                repository.markSyncSuccess(document.getId());
                success++;
                commits += documentCommits.size();
            } catch (RuntimeException e) {
                repository.markSyncError(document.getId(), e.getMessage());
                failed++;
                log.warn("Cannot synchronize GitLab commits: documentOid={}, project={}, ref={}, error={}",
                        document.getDocumentOid(), document.getProjectPath(), document.getTreeRef(), e.getMessage());
                log.debug("GitLab synchronization error details", e);
            }
        }

        log.info("GitLab commit tracking finished: dictionaryId={}, documents={}, processed={}, success={}, failed={}, commits={}",
                dictionaryId, documents.size(), processed, success, failed, commits);
    }
}

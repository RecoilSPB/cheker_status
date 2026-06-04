package ru.spb.reshenie.chekerstatus.gitlab.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GitLinkRefreshResult {

    private final List<DocumentGitLink> activeLinks;
    private final int insertedLinks;
    private final int updatedLinks;
    private final int deactivatedLinks;
    private final int parseErrors;

    public GitLinkRefreshResult(List<DocumentGitLink> activeLinks,
                                int insertedLinks,
                                int updatedLinks,
                                int deactivatedLinks,
                                int parseErrors) {
        this.activeLinks = Collections.unmodifiableList(new ArrayList<DocumentGitLink>(
                activeLinks == null ? Collections.emptyList() : activeLinks
        ));
        this.insertedLinks = insertedLinks;
        this.updatedLinks = updatedLinks;
        this.deactivatedLinks = deactivatedLinks;
        this.parseErrors = parseErrors;
    }

    public List<DocumentGitLink> getActiveLinks() {
        return activeLinks;
    }

    public int getInsertedLinks() {
        return insertedLinks;
    }

    public int getUpdatedLinks() {
        return updatedLinks;
    }

    public int getDeactivatedLinks() {
        return deactivatedLinks;
    }

    public int getParseErrors() {
        return parseErrors;
    }
}

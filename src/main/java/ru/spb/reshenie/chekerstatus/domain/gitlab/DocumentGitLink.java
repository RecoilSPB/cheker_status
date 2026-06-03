package ru.spb.reshenie.chekerstatus.domain.gitlab;

public class DocumentGitLink {

    private final long id;
    private final long dictionaryId;
    private final long nsiRecordId;
    private final String recordKey;
    private final String documentOid;
    private final String documentName;
    private final String gitLink;
    private final String gitHost;
    private final String projectPath;
    private final String treeRef;

    public DocumentGitLink(long id,
                           long dictionaryId,
                           long nsiRecordId,
                           String recordKey,
                           String documentOid,
                           String documentName,
                           String gitLink,
                           String gitHost,
                           String projectPath,
                           String treeRef) {
        this.id = id;
        this.dictionaryId = dictionaryId;
        this.nsiRecordId = nsiRecordId;
        this.recordKey = recordKey;
        this.documentOid = documentOid;
        this.documentName = documentName;
        this.gitLink = gitLink;
        this.gitHost = gitHost;
        this.projectPath = projectPath;
        this.treeRef = treeRef;
    }

    public long getId() {
        return id;
    }

    public long getDictionaryId() {
        return dictionaryId;
    }

    public long getNsiRecordId() {
        return nsiRecordId;
    }

    public String getRecordKey() {
        return recordKey;
    }

    public String getDocumentOid() {
        return documentOid;
    }

    public String getDocumentName() {
        return documentName;
    }

    public String getGitLink() {
        return gitLink;
    }

    public String getGitHost() {
        return gitHost;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getTreeRef() {
        return treeRef;
    }
}

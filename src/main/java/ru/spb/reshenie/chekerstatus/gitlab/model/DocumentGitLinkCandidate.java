package ru.spb.reshenie.chekerstatus.gitlab.model;

public class DocumentGitLinkCandidate {

    private final long nsiRecordId;
    private final String recordKey;
    private final String documentOid;
    private final String documentName;
    private final String gitLink;

    public DocumentGitLinkCandidate(long nsiRecordId,
                                    String recordKey,
                                    String documentOid,
                                    String documentName,
                                    String gitLink) {
        this.nsiRecordId = nsiRecordId;
        this.recordKey = recordKey;
        this.documentOid = documentOid;
        this.documentName = documentName;
        this.gitLink = gitLink;
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
}

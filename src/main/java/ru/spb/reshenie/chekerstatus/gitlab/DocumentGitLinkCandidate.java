package ru.spb.reshenie.chekerstatus.gitlab;

class DocumentGitLinkCandidate {

    private final long nsiRecordId;
    private final String recordKey;
    private final String documentOid;
    private final String documentName;
    private final String gitLink;

    DocumentGitLinkCandidate(long nsiRecordId,
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

    long getNsiRecordId() {
        return nsiRecordId;
    }

    String getRecordKey() {
        return recordKey;
    }

    String getDocumentOid() {
        return documentOid;
    }

    String getDocumentName() {
        return documentName;
    }

    String getGitLink() {
        return gitLink;
    }
}

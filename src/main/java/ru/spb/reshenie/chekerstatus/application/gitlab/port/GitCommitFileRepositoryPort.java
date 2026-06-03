package ru.spb.reshenie.chekerstatus.application.gitlab.port;

import ru.spb.reshenie.chekerstatus.domain.gitlab.GitCommitFileChange;

public interface GitCommitFileRepositoryPort {

    boolean saveFileChange(GitCommitFileChange change);
}

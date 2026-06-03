package ru.spb.reshenie.chekerstatus.application.gitlab.port;

import java.util.concurrent.Callable;

public interface GitLabConcurrencyPort {

    <T> T withProjectPermit(Callable<T> action);

    <T> T withCommitPermit(Callable<T> action);

    <T> T withFilePermit(Callable<T> action);

    <T> T withDbWritePermit(Callable<T> action);

    void withDbWritePermit(Runnable action);
}

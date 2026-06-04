package ru.spb.reshenie.chekerstatus.gitlab.sync;

import org.springframework.stereotype.Component;
import ru.spb.reshenie.chekerstatus.config.gitlab.GitLabProperties;
import ru.spb.reshenie.chekerstatus.gitlab.client.GitLabClientException;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;

@Component
public class GitLabConcurrencyLimiter {

    private final Semaphore projectSemaphore;
    private final Semaphore commitSemaphore;
    private final Semaphore fileSemaphore;
    private final Semaphore dbWriteSemaphore;

    public GitLabConcurrencyLimiter(GitLabProperties properties) {
        this.projectSemaphore = new Semaphore(positive(properties.getMaxConcurrentProjects()), true);
        this.commitSemaphore = new Semaphore(positive(properties.getMaxConcurrentCommits()), true);
        this.fileSemaphore = new Semaphore(positive(properties.getMaxConcurrentFiles()), true);
        this.dbWriteSemaphore = new Semaphore(positive(properties.getMaxConcurrentDbWrites()), true);
    }

    public <T> T withProjectPermit(Callable<T> action) {
        return withPermit(projectSemaphore, "project", action);
    }

    public <T> T withCommitPermit(Callable<T> action) {
        return withPermit(commitSemaphore, "commit", action);
    }

    public <T> T withFilePermit(Callable<T> action) {
        return withPermit(fileSemaphore, "file", action);
    }

    public <T> T withDbWritePermit(Callable<T> action) {
        return withPermit(dbWriteSemaphore, "db-write", action);
    }

    public void withDbWritePermit(Runnable action) {
        withDbWritePermit(new Callable<Void>() {
            @Override
            public Void call() {
                action.run();
                return null;
            }
        });
    }

    int availableProjectPermits() {
        return projectSemaphore.availablePermits();
    }

    int availableCommitPermits() {
        return commitSemaphore.availablePermits();
    }

    int availableFilePermits() {
        return fileSemaphore.availablePermits();
    }

    int availableDbWritePermits() {
        return dbWriteSemaphore.availablePermits();
    }

    private <T> T withPermit(Semaphore semaphore, String permitName, Callable<T> action) {
        boolean acquired = false;
        try {
            semaphore.acquire();
            acquired = true;
            return action.call();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitLabClientException("Interrupted while waiting for GitLab " + permitName + " permit", e);
        } catch (GitLabClientException e) {
            throw e;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new GitLabClientException("GitLab " + permitName + " task failed", e);
        } finally {
            if (acquired) {
                semaphore.release();
            }
        }
    }

    private int positive(int value) {
        return Math.max(1, value);
    }
}

package ru.spb.reshenie.chekerstatus.config.gitlab;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class GitLabExecutorConfigurationTest {

    @Test
    void gitLabExecutorUsesVirtualThreadsWhenEnabled() throws Exception {
        GitLabProperties properties = new GitLabProperties();
        properties.setVirtualThreadsEnabled(true);
        ExecutorService executorService = new GitLabExecutorConfiguration().gitLabVirtualThreadExecutor(properties);

        try {
            Future<Boolean> virtualThread = executorService.submit(new java.util.concurrent.Callable<Boolean>() {
                @Override
                public Boolean call() {
                    return Thread.currentThread().isVirtual();
                }
            });

            assertThat(virtualThread.get()).isTrue();
        } finally {
            executorService.shutdownNow();
        }
    }
}

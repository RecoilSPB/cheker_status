package ru.spb.reshenie.chekerstatus.gitlab.sync;

import ru.spb.reshenie.chekerstatus.config.gitlab.GitLabProperties;
import ru.spb.reshenie.chekerstatus.gitlab.client.GitLabClientException;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class GitLabConcurrencyLimiterTest {

    @Test
    void fileSemaphoreLimitsConcurrentTasks() throws Exception {
        GitLabProperties properties = new GitLabProperties();
        properties.setMaxConcurrentFiles(2);
        GitLabConcurrencyLimiter limiter = new GitLabConcurrencyLimiter(properties);
        ExecutorService executorService = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("test-file-vt-", 0).factory()
        );
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger current = new AtomicInteger();
        AtomicInteger maxSeen = new AtomicInteger();
        List<Future<Void>> futures = new ArrayList<Future<Void>>();

        try {
            for (int i = 0; i < 10; i++) {
                futures.add(executorService.submit(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        start.await(2, TimeUnit.SECONDS);
                        limiter.withFilePermit(new Callable<Void>() {
                            @Override
                            public Void call() throws Exception {
                                int active = current.incrementAndGet();
                                maxSeen.updateAndGet(previous -> Math.max(previous, active));
                                try {
                                    Thread.sleep(50L);
                                } finally {
                                    current.decrementAndGet();
                                }
                                return null;
                            }
                        });
                        return null;
                    }
                }));
            }

            start.countDown();
            for (Future<Void> future : futures) {
                future.get();
            }

            assertThat(maxSeen.get()).isLessThanOrEqualTo(2);
            assertThat(limiter.availableFilePermits()).isEqualTo(2);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void waitingForPermitRestoresInterruptedFlag() throws Exception {
        GitLabProperties properties = new GitLabProperties();
        properties.setMaxConcurrentFiles(1);
        GitLabConcurrencyLimiter limiter = new GitLabConcurrencyLimiter(properties);
        CountDownLatch holderEntered = new CountDownLatch(1);
        CountDownLatch releaseHolder = new CountDownLatch(1);
        CountDownLatch waiterStarted = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        AtomicBoolean interrupted = new AtomicBoolean();

        Thread holder = Thread.ofVirtual().name("test-holder-vt").start(new Runnable() {
            @Override
            public void run() {
                limiter.withFilePermit(new Callable<Void>() {
                    @Override
                    public Void call() throws Exception {
                        holderEntered.countDown();
                        releaseHolder.await(2, TimeUnit.SECONDS);
                        return null;
                    }
                });
            }
        });
        assertThat(holderEntered.await(2, TimeUnit.SECONDS)).isTrue();

        Thread waiter = Thread.ofVirtual().name("test-waiter-vt").start(new Runnable() {
            @Override
            public void run() {
                waiterStarted.countDown();
                try {
                    limiter.withFilePermit(new Callable<Void>() {
                        @Override
                        public Void call() {
                            return null;
                        }
                    });
                } catch (Throwable e) {
                    interrupted.set(Thread.currentThread().isInterrupted());
                    error.set(e);
                }
            }
        });
        assertThat(waiterStarted.await(2, TimeUnit.SECONDS)).isTrue();

        Thread.sleep(100L);
        waiter.interrupt();
        waiter.join(2_000L);
        releaseHolder.countDown();
        holder.join(2_000L);

        assertThat(error.get()).isInstanceOf(GitLabClientException.class);
        assertThat(interrupted.get()).isTrue();
        assertThat(limiter.availableFilePermits()).isEqualTo(1);
    }
}

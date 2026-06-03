package ru.spb.reshenie.chekerstatus.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

@Configuration
public class VirtualThreadConfiguration {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadConfiguration.class);

    @Bean(destroyMethod = "shutdown")
    @Qualifier("gitLabVirtualThreadExecutor")
    public ExecutorService gitLabVirtualThreadExecutor(GitLabProperties properties) {
        if (properties.isVirtualThreadsEnabled()) {
            ThreadFactory factory = Thread.ofVirtual().name("gitlab-vt-", 0).factory();
            log.info("GitLab synchronization executor uses virtual threads");
            return Executors.newThreadPerTaskExecutor(factory);
        }

        int poolSize = Math.max(1,
                properties.getMaxConcurrentProjects()
                        + properties.getMaxConcurrentCommits()
                        + properties.getMaxConcurrentFiles()
                        + properties.getMaxConcurrentDbWrites());
        log.info("GitLab synchronization executor uses bounded platform thread pool: size={}", poolSize);
        AtomicLong counter = new AtomicLong();
        ThreadFactory factory = new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable);
                thread.setName("gitlab-pt-" + counter.getAndIncrement());
                return thread;
            }
        };
        return Executors.newFixedThreadPool(poolSize, factory);
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("nsi-scheduler-");
        scheduler.initialize();
        return scheduler;
    }

    @Bean(name = TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
    public TaskExecutor applicationTaskExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("app-task-");
        executor.setVirtualThreads(true);
        return executor;
    }

    @Bean
    public Clock applicationClock() {
        return Clock.systemUTC();
    }
}

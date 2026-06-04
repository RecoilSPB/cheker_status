package ru.spb.reshenie.chekerstatus.config.gitlab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Configuration
public class GitLabExecutorConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GitLabExecutorConfiguration.class);

    @Bean(destroyMethod = "shutdown")
    @Qualifier("gitLabVirtualThreadExecutor")
    public ExecutorService gitLabVirtualThreadExecutor() {
        ThreadFactory factory = Thread.ofVirtual().name("gitlab-vt-", 0).factory();
        log.info("GitLab synchronization executor uses virtual threads");
        return Executors.newThreadPerTaskExecutor(factory);
    }

}

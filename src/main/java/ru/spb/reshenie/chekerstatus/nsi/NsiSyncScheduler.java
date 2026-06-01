package ru.spb.reshenie.chekerstatus.nsi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.spb.reshenie.chekerstatus.config.NsiProperties;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class NsiSyncScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NsiSyncScheduler.class);

    private final NsiSyncService syncService;
    private final NsiProperties properties;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public NsiSyncScheduler(NsiSyncService syncService, NsiProperties properties) {
        this.syncService = syncService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (properties.isSyncOnStartup()) {
            runOnce(false);
        }
    }

    @Scheduled(
            fixedDelayString = "${nsi.poll-fixed-delay-ms:3600000}",
            initialDelayString = "${nsi.poll-initial-delay-ms:60000}"
    )
    public void scheduledSynchronization() {
        runOnce(false);
    }

    private void runOnce(boolean forceReload) {
        if (!running.compareAndSet(false, true)) {
            log.info("NSI synchronization is already running");
            return;
        }
        try {
            syncService.synchronizeConfiguredDictionaries(forceReload);
        } finally {
            running.set(false);
        }
    }
}

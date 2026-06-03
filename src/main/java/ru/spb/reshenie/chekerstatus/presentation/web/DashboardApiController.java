package ru.spb.reshenie.chekerstatus.presentation.web;

import ru.spb.reshenie.chekerstatus.infrastructure.persistence.NsiSyncRunRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import ru.spb.reshenie.chekerstatus.application.nsi.NsiSyncScheduler;
import ru.spb.reshenie.chekerstatus.domain.sync.DashboardSummary;
import ru.spb.reshenie.chekerstatus.domain.sync.NsiSyncRunDetails;
import ru.spb.reshenie.chekerstatus.domain.sync.NsiSyncRunFilter;
import ru.spb.reshenie.chekerstatus.domain.sync.NsiSyncRunLogFilter;
import ru.spb.reshenie.chekerstatus.domain.sync.PagedResult;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncErrorStage;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunLogLevel;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunStatus;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunType;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardApiController {

    private final NsiSyncRunRepository syncRunRepository;
    private final NsiSyncScheduler syncScheduler;

    public DashboardApiController(NsiSyncRunRepository syncRunRepository, NsiSyncScheduler syncScheduler) {
        this.syncRunRepository = syncRunRepository;
        this.syncScheduler = syncScheduler;
    }

    @GetMapping("/summary")
    public DashboardSummary summary() {
        return dashboardSummary();
    }

    @GetMapping("/sync-runs")
    public PagedResult<?> syncRuns(@RequestParam(name = "page", defaultValue = "0") int page,
                                   @RequestParam(name = "size", defaultValue = "20") int size,
                                   @RequestParam(name = "status", required = false) SyncRunStatus status,
                                   @RequestParam(name = "runType", required = false) SyncRunType runType,
                                   @RequestParam(name = "dictionaryIdentifier", required = false) String dictionaryIdentifier,
                                   @RequestParam(name = "dictionaryVersion", required = false) String dictionaryVersion,
                                   @RequestParam(name = "dateFrom", required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateFrom,
                                   @RequestParam(name = "dateTo", required = false)
                                   @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dateTo,
                                   @RequestParam(name = "hasErrors", required = false) Boolean hasErrors,
                                   @RequestParam(name = "sort", defaultValue = "startedAt") String sort,
                                   @RequestParam(name = "direction", defaultValue = "desc") String direction) {
        NsiSyncRunFilter filter = new NsiSyncRunFilter();
        filter.setPage(page);
        filter.setSize(size);
        filter.setStatus(status);
        filter.setRunType(runType);
        filter.setDictionaryIdentifier(dictionaryIdentifier);
        filter.setDictionaryVersion(dictionaryVersion);
        filter.setDateFrom(dateFrom);
        filter.setDateTo(dateTo);
        filter.setHasErrors(hasErrors);
        filter.setSort(sort);
        filter.setDirection(direction);
        return syncRunRepository.findRuns(filter);
    }

    @GetMapping("/sync-runs/{id}")
    public NsiSyncRunDetails syncRunDetails(@PathVariable("id") long id) {
        try {
            return syncRunRepository.findDetails(id);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sync run not found: " + id, e);
        }
    }

    @GetMapping("/sync-runs/{id}/logs")
    public PagedResult<?> syncRunLogs(@PathVariable("id") long id,
                                      @RequestParam(name = "page", defaultValue = "0") int page,
                                      @RequestParam(name = "size", defaultValue = "100") int size,
                                      @RequestParam(name = "level", required = false) SyncRunLogLevel level,
                                      @RequestParam(name = "stage", required = false) SyncErrorStage stage) {
        try {
            syncRunRepository.findRun(id);
            NsiSyncRunLogFilter filter = new NsiSyncRunLogFilter();
            filter.setPage(page);
            filter.setSize(size);
            filter.setLevel(level);
            filter.setStage(stage);
            return syncRunRepository.findLogs(id, filter);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sync run not found: " + id, e);
        }
    }

    @PostMapping("/sync-runs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ManualSyncResponse startManualSync(@RequestBody(required = false) ManualSyncRequest request) {
        String dictionaryIdentifier = request == null ? null : request.getDictionaryIdentifier();
        boolean forceReload = request != null && request.isForceReload();
        try {
            long runId = syncScheduler.startManualSynchronization(dictionaryIdentifier, forceReload);
            return new ManualSyncResponse(runId, SyncRunStatus.RUNNING.name());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    public static class ManualSyncRequest {
        private String dictionaryIdentifier;
        private boolean forceReload;

        public String getDictionaryIdentifier() {
            return dictionaryIdentifier;
        }

        public void setDictionaryIdentifier(String dictionaryIdentifier) {
            this.dictionaryIdentifier = dictionaryIdentifier;
        }

        public boolean isForceReload() {
            return forceReload;
        }

        public void setForceReload(boolean forceReload) {
            this.forceReload = forceReload;
        }
    }

    public static class ManualSyncResponse {
        private final long syncRunId;
        private final String status;

        public ManualSyncResponse(long syncRunId, String status) {
            this.syncRunId = syncRunId;
            this.status = status;
        }

        public long getSyncRunId() {
            return syncRunId;
        }

        public String getStatus() {
            return status;
        }
    }

    private DashboardSummary dashboardSummary() {
        DashboardSummary summary = syncRunRepository.dashboardSummary();
        return summary.withScheduler(
                syncScheduler.getNextAutoRunAt(),
                syncScheduler.getNextAutoRunDelayMs(),
                Long.valueOf(syncScheduler.getAutoSyncIntervalMs())
        );
    }
}

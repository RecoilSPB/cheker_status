package ru.spb.reshenie.chekerstatus.web.controller;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import ru.spb.reshenie.chekerstatus.nsi.scheduler.NsiSyncScheduler;
import ru.spb.reshenie.chekerstatus.sync.model.DashboardSummary;
import ru.spb.reshenie.chekerstatus.sync.repository.SyncRunRepository;
import ru.spb.reshenie.chekerstatus.sync.query.NsiSyncRunFilter;
import ru.spb.reshenie.chekerstatus.sync.query.NsiSyncRunLogFilter;
import ru.spb.reshenie.chekerstatus.sync.query.PagedResult;
import ru.spb.reshenie.chekerstatus.sync.model.SyncRunStatus;
import ru.spb.reshenie.chekerstatus.sync.model.SyncRunType;
import ru.spb.reshenie.chekerstatus.sync.model.SyncErrorStage;
import ru.spb.reshenie.chekerstatus.sync.model.SyncRunLogLevel;
import ru.spb.reshenie.chekerstatus.security.service.SecurityAccessService;
import ru.spb.reshenie.chekerstatus.web.model.DocumentDetails;
import ru.spb.reshenie.chekerstatus.web.repository.UiRepository;
import ru.spb.reshenie.chekerstatus.web.model.DownloadedFileVersion;
import ru.spb.reshenie.chekerstatus.web.service.GitFileDiffViewService;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Controller
public class UiController {

    private static final ZoneId DEFAULT_CLIENT_ZONE = ZoneId.of("UTC");
    private static final int DASHBOARD_SYNC_RUN_PAGE_SIZE = 5;
    private static final int MAX_VISIBLE_PAGE_LINKS = 9;

    private final UiRepository repository;
    private final SyncRunRepository syncRunRepository;
    private final NsiSyncScheduler syncScheduler;
    private final SecurityAccessService securityAccessService;
    private final GitFileDiffViewService gitFileDiffViewService;

    public UiController(UiRepository repository,
                        SyncRunRepository syncRunRepository,
                        NsiSyncScheduler syncScheduler,
                        SecurityAccessService securityAccessService,
                        GitFileDiffViewService gitFileDiffViewService) {
        this.repository = repository;
        this.syncRunRepository = syncRunRepository;
        this.syncScheduler = syncScheduler;
        this.securityAccessService = securityAccessService;
        this.gitFileDiffViewService = gitFileDiffViewService;
    }

    @GetMapping("/")
    public String root() {
        try {
            return "redirect:" + securityAccessService.firstAccessiblePath();
        } catch (AccessDeniedException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No accessible sections for current user", e);
        }
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(name = "page", defaultValue = "0") int page,
                            @RequestParam(name = "size", defaultValue = "20") int size,
                            @RequestParam(name = "status", required = false) SyncRunStatus status,
                            @RequestParam(name = "runType", required = false) SyncRunType runType,
                            @RequestParam(name = "dictionaryIdentifier", required = false) String dictionaryIdentifier,
                            @RequestParam(name = "dictionaryVersion", required = false) String dictionaryVersion,
                            @RequestParam(name = "dateFrom", required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
                            @RequestParam(name = "dateTo", required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
                            @RequestParam(name = "hasErrors", required = false) Boolean hasErrors,
                            @RequestParam(name = "sort", defaultValue = "startedAt") String sort,
                            @RequestParam(name = "direction", defaultValue = "desc") String direction,
                            @RequestParam(name = "tz", required = false) String timeZone,
                            @RequestParam(name = "clientTimeZone", required = false) String clientTimeZone,
                            Model model) {
        ZoneId clientZone = resolveClientZone(clientTimeZone == null ? timeZone : clientTimeZone);

        NsiSyncRunFilter filter = new NsiSyncRunFilter();
        filter.setPage(page);
        filter.setSize(DASHBOARD_SYNC_RUN_PAGE_SIZE);
        filter.setStatus(status);
        filter.setRunType(runType);
        filter.setDictionaryIdentifier(dictionaryIdentifier);
        filter.setDictionaryVersion(dictionaryVersion);
        filter.setDateFrom(toOffsetDateTime(dateFrom, clientZone));
        filter.setDateTo(toOffsetDateTime(dateTo, clientZone));
        filter.setHasErrors(hasErrors);
        filter.setSort(sort);
        filter.setDirection(direction);
        PagedResult<?> runs = syncRunRepository.findRuns(filter);

        model.addAttribute("active", "dashboard");
        model.addAttribute("summary", dashboardSummary());
        model.addAttribute("runs", runs);
        model.addAttribute("pageNumbers", pageNumbers(runs));
        model.addAttribute("filter", filter);
        model.addAttribute("statuses", SyncRunStatus.values());
        model.addAttribute("runTypes", SyncRunType.values());
        model.addAttribute("dateFromInput", dateFrom);
        model.addAttribute("dateToInput", dateTo);
        model.addAttribute("clientTimeZone", clientZone.getId());
        return "dashboard";
    }

    @GetMapping("/dashboard/sync-runs/{id}")
    public String dashboardRunDetails(@PathVariable("id") long id, Model model) {
        try {
            model.addAttribute("active", "dashboard");
            model.addAttribute("details", syncRunRepository.findDetails(id));
            return "sync-run";
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sync run not found: " + id, e);
        }
    }

    @GetMapping("/dashboard/sync-runs/{id}/fragment")
    public String dashboardRunDetailsFragment(@PathVariable("id") long id, Model model) {
        try {
            model.addAttribute("active", "dashboard");
            model.addAttribute("details", syncRunRepository.findDetails(id));
            return "sync-run :: run-detail";
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sync run not found: " + id, e);
        }
    }

    @GetMapping("/dashboard/sync-runs/{id}/logs")
    public String dashboardRunLogs(@PathVariable("id") long id,
                                   @RequestParam(name = "page", defaultValue = "0") int page,
                                   @RequestParam(name = "size", defaultValue = "100") int size,
                                   @RequestParam(name = "level", required = false) SyncRunLogLevel level,
                                   @RequestParam(name = "stage", required = false) SyncErrorStage stage,
                                   Model model) {
        addRunLogModel(id, page, size, level, stage, model);
        return "sync-run-log";
    }

    @GetMapping("/dashboard/sync-runs/{id}/logs/fragment")
    public String dashboardRunLogsFragment(@PathVariable("id") long id,
                                           @RequestParam(name = "page", defaultValue = "0") int page,
                                           @RequestParam(name = "size", defaultValue = "100") int size,
                                           @RequestParam(name = "level", required = false) SyncRunLogLevel level,
                                           @RequestParam(name = "stage", required = false) SyncErrorStage stage,
                                           Model model) {
        addRunLogModel(id, page, size, level, stage, model);
        return "sync-run-log :: log-content";
    }

    @GetMapping("/dashboard/fragments/summary")
    public String dashboardSummaryFragment(Model model) {
        model.addAttribute("summary", dashboardSummary());
        return "dashboard :: summary-stats";
    }

    @GetMapping("/dashboard/fragments/sync-runs")
    public String dashboardSyncRunsFragment(@RequestParam(name = "page", defaultValue = "0") int page,
                                            @RequestParam(name = "size", defaultValue = "20") int size,
                                            @RequestParam(name = "status", required = false) SyncRunStatus status,
                                            @RequestParam(name = "runType", required = false) SyncRunType runType,
                                            @RequestParam(name = "dictionaryIdentifier", required = false) String dictionaryIdentifier,
                                            @RequestParam(name = "dictionaryVersion", required = false) String dictionaryVersion,
                                            @RequestParam(name = "dateFrom", required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
                                            @RequestParam(name = "dateTo", required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
                                            @RequestParam(name = "hasErrors", required = false) Boolean hasErrors,
                                            @RequestParam(name = "sort", defaultValue = "startedAt") String sort,
                                            @RequestParam(name = "direction", defaultValue = "desc") String direction,
                                            @RequestParam(name = "tz", required = false) String timeZone,
                                            @RequestParam(name = "clientTimeZone", required = false) String clientTimeZone,
                                            Model model) {
        ZoneId clientZone = resolveClientZone(clientTimeZone == null ? timeZone : clientTimeZone);
        NsiSyncRunFilter filter = new NsiSyncRunFilter();
        filter.setPage(page);
        filter.setSize(DASHBOARD_SYNC_RUN_PAGE_SIZE);
        filter.setStatus(status);
        filter.setRunType(runType);
        filter.setDictionaryIdentifier(dictionaryIdentifier);
        filter.setDictionaryVersion(dictionaryVersion);
        filter.setDateFrom(toOffsetDateTime(dateFrom, clientZone));
        filter.setDateTo(toOffsetDateTime(dateTo, clientZone));
        filter.setHasErrors(hasErrors);
        filter.setSort(sort);
        filter.setDirection(direction);
        PagedResult<?> runs = syncRunRepository.findRuns(filter);
        model.addAttribute("runs", runs);
        model.addAttribute("pageNumbers", pageNumbers(runs));
        model.addAttribute("filter", filter);
        model.addAttribute("dateFromInput", dateFrom);
        model.addAttribute("dateToInput", dateTo);
        model.addAttribute("clientTimeZone", clientZone.getId());
        return "dashboard :: sync-runs-panel";
    }

    @PostMapping("/dashboard/sync-runs")
    public String startDashboardSync(@RequestParam(name = "dictionaryIdentifier", required = false) String dictionaryIdentifier,
                                     @RequestParam(name = "forceReload", defaultValue = "false") boolean forceReload) {
        try {
            syncScheduler.startManualSynchronization(dictionaryIdentifier, forceReload);
        } catch (IllegalStateException e) {
            // The dashboard disables the button while a run is active; stale submits can be ignored quietly.
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/documents")
    public String documents(@RequestParam(name = "q", required = false) String query,
                            @RequestParam(name = "status", required = false) String status,
                            Model model) {
        model.addAttribute("active", "documents");
        model.addAttribute("query", query == null ? "" : query);
        model.addAttribute("status", status == null ? "" : status);
        model.addAttribute("stats", repository.dashboardStats());
        model.addAttribute("documents", repository.documents(query, status));
        return "documents";
    }

    @GetMapping("/commits")
    public String commits(Model model) {
        model.addAttribute("active", "commits");
        model.addAttribute("stats", repository.dashboardStats());
        model.addAttribute("commits", repository.recentCommits(300));
        return "commits";
    }

    @GetMapping("/file-changes")
    public String fileChanges(Model model) {
        model.addAttribute("active", "fileChanges");
        model.addAttribute("stats", repository.dashboardStats());
        model.addAttribute("fileChanges", repository.recentFileChanges(300));
        return "file-changes";
    }

    @GetMapping("/documents/{id}")
    public String document(@PathVariable("id") long id,
                           @RequestParam(name = "file", required = false) Long selectedFileChangeId,
                           Model model) {
        try {
            model.addAttribute("active", "documents");
            DocumentDetails details = repository.documentDetails(id, selectedFileChangeId);
            model.addAttribute("details", details.withStructuredDiff(
                    gitFileDiffViewService.loadStructuredDiff(id, details.getSelectedFileChange())
            ));
            return "document";
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + id, e);
        }
    }

    @GetMapping("/documents/{documentId}/file-changes/{fileChangeId}/download")
    public ResponseEntity<ByteArrayResource> downloadFileVersion(@PathVariable("documentId") long documentId,
                                                                 @PathVariable("fileChangeId") long fileChangeId,
                                                                 @RequestParam("version") String version) {
        try {
            DownloadedFileVersion downloaded = gitFileDiffViewService.loadVersion(documentId, fileChangeId, version);
            ByteArrayResource resource = new ByteArrayResource(downloaded.getContent());
            ContentDisposition disposition = ContentDisposition.attachment()
                    .filename(downloaded.getFileName(), StandardCharsets.UTF_8)
                    .build();
            return ResponseEntity.ok()
                    .header("Content-Disposition", disposition.toString())
                    .contentLength(downloaded.getContent().length)
                    .header("Content-Type", downloaded.getContentType())
                    .body(resource);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File version not found", e);
        }
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value, ZoneId clientZone) {
        if (value == null) {
            return null;
        }
        return value.atZone(clientZone).toOffsetDateTime();
    }

    private DashboardSummary dashboardSummary() {
        DashboardSummary summary = syncRunRepository.dashboardSummary();
        return summary.withScheduler(
                syncScheduler.getNextAutoRunAt(),
                syncScheduler.getNextAutoRunDelayMs(),
                Long.valueOf(syncScheduler.getAutoSyncIntervalMs())
        );
    }

    private void addRunLogModel(long id,
                                int page,
                                int size,
                                SyncRunLogLevel level,
                                SyncErrorStage stage,
                                Model model) {
        try {
            NsiSyncRunLogFilter filter = new NsiSyncRunLogFilter();
            filter.setPage(page);
            filter.setSize(size);
            filter.setLevel(level);
            filter.setStage(stage);
            PagedResult<?> logs = syncRunRepository.findLogs(id, filter);
            model.addAttribute("active", "dashboard");
            model.addAttribute("run", syncRunRepository.findRun(id));
            model.addAttribute("logs", logs);
            model.addAttribute("pageNumbers", pageNumbers(logs));
            model.addAttribute("filter", filter);
            model.addAttribute("levels", SyncRunLogLevel.values());
            model.addAttribute("stages", SyncErrorStage.values());
        } catch (EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sync run not found: " + id, e);
        }
    }

    private ZoneId resolveClientZone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return DEFAULT_CLIENT_ZONE;
        }
        try {
            return ZoneId.of(timeZone.trim());
        } catch (DateTimeException e) {
            return DEFAULT_CLIENT_ZONE;
        }
    }

    private List<Integer> pageNumbers(PagedResult<?> runs) {
        int totalPages = runs.getTotalPages();
        if (totalPages <= 0) {
            return List.of();
        }
        if (totalPages <= MAX_VISIBLE_PAGE_LINKS) {
            return range(0, totalPages - 1);
        }

        int current = runs.getPage();
        int side = (MAX_VISIBLE_PAGE_LINKS - 1) / 2;
        int start = Math.max(0, current - side);
        int end = Math.min(totalPages - 1, start + MAX_VISIBLE_PAGE_LINKS - 1);
        start = Math.max(0, end - MAX_VISIBLE_PAGE_LINKS + 1);
        return range(start, end);
    }

    private List<Integer> range(int startInclusive, int endInclusive) {
        List<Integer> result = new ArrayList<Integer>();
        for (int page = startInclusive; page <= endInclusive; page++) {
            result.add(Integer.valueOf(page));
        }
        return result;
    }
}

package ru.spb.reshenie.chekerstatus.web;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.spb.reshenie.chekerstatus.nsi.NsiSyncScheduler;
import ru.spb.reshenie.chekerstatus.sync.NsiSyncRunFilter;
import ru.spb.reshenie.chekerstatus.sync.NsiSyncRunRepository;
import ru.spb.reshenie.chekerstatus.sync.PagedResult;
import ru.spb.reshenie.chekerstatus.sync.SyncRunStatus;
import ru.spb.reshenie.chekerstatus.sync.SyncRunType;

import java.time.OffsetDateTime;

@Controller
public class UiController {

    private final UiRepository repository;
    private final NsiSyncRunRepository syncRunRepository;
    private final NsiSyncScheduler syncScheduler;

    public UiController(UiRepository repository,
                        NsiSyncRunRepository syncRunRepository,
                        NsiSyncScheduler syncScheduler) {
        this.repository = repository;
        this.syncRunRepository = syncRunRepository;
        this.syncScheduler = syncScheduler;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/documents";
    }

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(name = "page", defaultValue = "0") int page,
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
                            @RequestParam(name = "direction", defaultValue = "desc") String direction,
                            Model model) {
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
        PagedResult<?> runs = syncRunRepository.findRuns(filter);

        model.addAttribute("active", "dashboard");
        model.addAttribute("summary", syncRunRepository.dashboardSummary());
        model.addAttribute("runs", runs);
        model.addAttribute("filter", filter);
        model.addAttribute("statuses", SyncRunStatus.values());
        model.addAttribute("runTypes", SyncRunType.values());
        return "dashboard";
    }

    @GetMapping("/dashboard/sync-runs/{id}")
    public String dashboardRunDetails(@PathVariable("id") long id, Model model) {
        model.addAttribute("active", "dashboard");
        model.addAttribute("details", syncRunRepository.findDetails(id));
        return "sync-run";
    }

    @PostMapping("/dashboard/sync-runs")
    public String startDashboardSync(@RequestParam(name = "dictionaryIdentifier", required = false) String dictionaryIdentifier,
                                     @RequestParam(name = "forceReload", defaultValue = "false") boolean forceReload,
                                     RedirectAttributes redirectAttributes) {
        try {
            long runId = syncScheduler.startManualSynchronization(dictionaryIdentifier, forceReload);
            redirectAttributes.addFlashAttribute("message", "Синхронизация запущена, ID " + runId);
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("message", e.getMessage());
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
        model.addAttribute("active", "documents");
        model.addAttribute("details", repository.documentDetails(id, selectedFileChangeId));
        return "document";
    }
}

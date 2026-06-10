package ru.spb.reshenie.chekerstatus.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.spb.reshenie.chekerstatus.gitlab.service.FileDiffBackfillScope;
import ru.spb.reshenie.chekerstatus.gitlab.service.GitFileDiffBackfillResult;
import ru.spb.reshenie.chekerstatus.gitlab.service.GitFileDiffBackfillService;

@RestController
@RequestMapping("/api/file-diff")
public class FileDiffAdminController {

    private final GitFileDiffBackfillService gitFileDiffBackfillService;

    public FileDiffAdminController(GitFileDiffBackfillService gitFileDiffBackfillService) {
        this.gitFileDiffBackfillService = gitFileDiffBackfillService;
    }

    @PostMapping("/backfill")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public GitFileDiffBackfillResult backfill(@RequestParam(name = "scope", defaultValue = "MISSING") FileDiffBackfillScope scope,
                                              @RequestParam(name = "documentId", required = false) Long documentId,
                                              @RequestParam(name = "limit", defaultValue = "100") int limit) {
        return gitFileDiffBackfillService.backfill(scope, documentId, limit);
    }
}

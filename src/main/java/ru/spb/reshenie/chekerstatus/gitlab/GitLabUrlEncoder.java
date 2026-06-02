package ru.spb.reshenie.chekerstatus.gitlab;

import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

public final class GitLabUrlEncoder {

    private GitLabUrlEncoder() {
    }

    public static String encodeProjectPath(String projectPath) {
        return UriUtils.encodePathSegment(projectPath, StandardCharsets.UTF_8.name());
    }

    public static String encodeFilePath(String filePath) {
        return UriUtils.encodePathSegment(filePath, StandardCharsets.UTF_8.name());
    }

    public static String encodeQueryValue(String value) {
        return UriUtils.encodeQueryParam(value, StandardCharsets.UTF_8.name());
    }
}

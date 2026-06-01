package ru.spb.reshenie.chekerstatus.gitlab;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;

@Component
public class GitLabLinkParser {

    private static final String TREE_MARKER = "/-/tree/";
    private static final String BLOB_MARKER = "/-/blob/";

    public GitLabLink parse(String gitLink) {
        if (gitLink == null || gitLink.trim().isEmpty()) {
            return null;
        }

        try {
            URI uri = URI.create(gitLink.trim());
            String path = uri.getRawPath();
            if (path == null) {
                return null;
            }

            String marker = path.contains(TREE_MARKER) ? TREE_MARKER : BLOB_MARKER;
            int markerIndex = path.indexOf(marker);
            if (markerIndex < 0) {
                return null;
            }

            String rawProjectPath = path.substring(1, markerIndex);
            String rawTreeRef = path.substring(markerIndex + marker.length());
            if (rawProjectPath.isEmpty() || rawTreeRef.isEmpty()) {
                return null;
            }

            return new GitLabLink(
                    gitLink.trim(),
                    uri.getHost(),
                    decode(rawProjectPath),
                    decode(rawTreeRef)
            );
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }
}

package ru.spb.reshenie.chekerstatus.gitlab.diff;

import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitContentUtils;

import java.util.Locale;
import java.util.Set;

public final class FileTypeClassifier {

    private static final Set<String> TEXT_EXTENSIONS = Set.of(
            "txt", "text", "log", "md", "markdown", "csv", "tsv", "json", "xml", "xsd", "xsl", "xslt", "sch",
            "yaml", "yml", "sql", "java", "kt", "kts", "groovy", "js", "ts", "tsx", "jsx",
            "html", "htm", "css", "scss", "less", "properties", "conf", "ini", "cfg",
            "sh", "bat", "cmd", "ps1", "dockerfile", "gradle"
    );
    private static final Set<String> ARCHIVE_EXTENSIONS = Set.of(
            "zip", "jar", "war", "ear", "tar", "gz", "tgz", "bz2", "xz", "7z", "rar",
            "pptx", "pptm", "odt", "ods", "odp"
    );

    private FileTypeClassifier() {
    }

    public static String extension(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        int slashIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex <= slashIndex || dotIndex == path.length() - 1) {
            return "";
        }
        return path.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    public static MediaType mediaType(String path) {
        return MediaTypeFactory.getMediaType(path).orElse(MediaType.APPLICATION_OCTET_STREAM);
    }

    public static boolean isDocx(String path) {
        return "docx".equals(extension(path));
    }

    public static boolean isXlsx(String path) {
        return "xlsx".equals(extension(path));
    }

    public static boolean isPdf(String path) {
        return "pdf".equals(extension(path));
    }

    public static boolean isTextPath(String path) {
        String extension = extension(path);
        if (TEXT_EXTENSIONS.contains(extension)) {
            return true;
        }
        MediaType mediaType = mediaType(path);
        return "text".equalsIgnoreCase(mediaType.getType())
                || isSubtype(mediaType, "json")
                || isSubtype(mediaType, "xml")
                || isSubtype(mediaType, "yaml")
                || isSubtype(mediaType, "javascript");
    }

    public static boolean looksText(byte[] bytes, String path) {
        if (bytes == null) {
            return false;
        }
        return isTextPath(path) || !GitContentUtils.looksBinary(bytes);
    }

    public static String formatFamily(String path, byte[] bytes) {
        MediaType mediaType = mediaType(path);
        if (isDocx(path)) {
            return "DOCX";
        }
        if (isXlsx(path)) {
            return "XLSX";
        }
        if (isPdf(path)) {
            return "PDF";
        }
        if (isTextPath(path) || (bytes != null && !GitContentUtils.looksBinary(bytes))) {
            return "TEXT";
        }
        if ("image".equalsIgnoreCase(mediaType.getType())) {
            return "IMAGE";
        }
        if ("audio".equalsIgnoreCase(mediaType.getType())) {
            return "AUDIO";
        }
        if ("video".equalsIgnoreCase(mediaType.getType())) {
            return "VIDEO";
        }
        if (ARCHIVE_EXTENSIONS.contains(extension(path)) || isSubtype(mediaType, "zip")
                || isSubtype(mediaType, "tar") || isSubtype(mediaType, "compressed")) {
            return "ARCHIVE";
        }
        return "BINARY";
    }

    private static boolean isSubtype(MediaType mediaType, String value) {
        String subtype = mediaType.getSubtype();
        return subtype != null && subtype.toLowerCase(Locale.ROOT).contains(value);
    }
}

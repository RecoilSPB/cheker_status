package ru.spb.reshenie.chekerstatus.gitlab.diff;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

@Component
@Order(40)
public class TextFileDiffHandler extends AbstractLineFileDiffHandler {

    private final ObjectMapper objectMapper;

    public TextFileDiffHandler(ObjectMapper objectMapper) {
        super("TEXT", "TEXT");
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(FileDiffSource source) {
        String path = source.getPrimaryPath();
        byte[] bytes = firstAvailableBytes(source);
        return FileTypeClassifier.isTextPath(path)
                && (hasReadableContent(source.getBefore()) || hasReadableContent(source.getAfter()))
                && (bytes == null || FileTypeClassifier.looksText(bytes, path));
    }

    @Override
    public ExtractedFileContent extract(FileDiffSource.Version version) {
        if (version == null || !version.exists() || !version.hasReadableContent()) {
            return ExtractedFileContent.available(List.of());
        }
        String text = version.getContent();
        if (text == null) {
            byte[] bytes = version.readableBytes();
            text = TextContentDecoder.decode(bytes, version.getPath());
        }
        String normalized = normalize(text, version.getPath());
        return ExtractedFileContent.available(lines(normalized));
    }

    private boolean hasReadableContent(FileDiffSource.Version version) {
        return version != null && version.hasReadableContent();
    }

    private byte[] firstAvailableBytes(FileDiffSource source) {
        byte[] after = source.getAfter().readableBytes();
        if (after != null) {
            return after;
        }
        return source.getBefore().readableBytes();
    }

    private String normalize(String text, String path) {
        String extension = FileTypeClassifier.extension(path);
        String normalized = stripBom(normalizeLineEndings(text));
        if ("json".equals(extension)) {
            return prettyPrintJson(normalized);
        }
        if ("xml".equals(extension) || "xsd".equals(extension) || "xsl".equals(extension)
                || "xslt".equals(extension) || "sch".equals(extension)) {
            return prettyPrintXml(normalized);
        }
        return trimRight(normalized);
    }

    private String prettyPrintJson(String text) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(sortJson(objectMapper.readTree(text)));
        } catch (Exception e) {
            return trimRight(text);
        }
    }

    private String prettyPrintXml(String text) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setNamespaceAware(true);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(text)));
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            var transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return trimRight(normalizeLineEndings(writer.toString()));
        } catch (Exception e) {
            return trimRight(text);
        }
    }

    private List<String> lines(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        String[] parts = text.split("\n", -1);
        List<String> lines = new ArrayList<String>(parts.length);
        for (String part : parts) {
            lines.add(part);
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private String stripBom(String text) {
        if (text != null && !text.isEmpty() && text.charAt(0) == '\ufeff') {
            return text.substring(1);
        }
        return text;
    }

    private String normalizeLineEndings(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
    }

    private com.fasterxml.jackson.databind.JsonNode sortJson(com.fasterxml.jackson.databind.JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            ArrayNode array = JsonNodeFactory.instance.arrayNode();
            node.forEach(item -> array.add(sortJson(item)));
            return array;
        }
        ObjectNode object = JsonNodeFactory.instance.objectNode();
        java.util.List<String> names = new java.util.ArrayList<String>();
        node.fieldNames().forEachRemaining(names::add);
        java.util.Collections.sort(names);
        for (String name : names) {
            object.set(name, sortJson(node.get(name)));
        }
        return object;
    }

    private String trimRight(String text) {
        String normalized = normalizeLineEndings(text);
        String[] parts = normalized.split("\n", -1);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(parts[i].replaceFirst("[ \\t]+$", ""));
        }
        return builder.toString();
    }
}

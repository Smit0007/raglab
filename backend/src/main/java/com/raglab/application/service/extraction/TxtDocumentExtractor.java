package com.raglab.application.service.extraction;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class TxtDocumentExtractor implements DocumentExtractor {

    @Override
    public boolean supports(String contentType, String filename) {
        return (contentType != null && (
            contentType.equalsIgnoreCase("text/plain")
            || contentType.equalsIgnoreCase("text/markdown")
        )) || (filename != null && (
            filename.toLowerCase().endsWith(".txt")
            || filename.toLowerCase().endsWith(".md")
        ));
    }

    @Override
    public ExtractedDocument extract(InputStream inputStream, String filename, String contentType) throws Exception {
        byte[] bytes = inputStream.readAllBytes();
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Cannot extract text from empty text file: " + filename);
        }

        String text = new String(bytes, StandardCharsets.UTF_8).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Text file contains no characters: " + filename);
        }

        List<ExtractedDocument.ExtractedPage> pages = List.of(new ExtractedDocument.ExtractedPage(1, text));
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("pageCount", 1);
        metadata.put("byteLength", bytes.length);

        return new ExtractedDocument(filename, "text/plain", 1, pages, metadata);
    }
}

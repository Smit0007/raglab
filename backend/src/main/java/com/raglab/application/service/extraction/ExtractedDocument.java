package com.raglab.application.service.extraction;

import java.util.List;
import java.util.Map;

public record ExtractedDocument(
    String filename,
    String contentType,
    int pageCount,
    List<ExtractedPage> pages,
    Map<String, Object> metadata
) {
    public record ExtractedPage(
        int pageNumber,
        String content
    ) {}
}

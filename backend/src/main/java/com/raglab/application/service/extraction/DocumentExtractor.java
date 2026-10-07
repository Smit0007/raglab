package com.raglab.application.service.extraction;

import java.io.InputStream;

public interface DocumentExtractor {
    boolean supports(String contentType, String filename);
    ExtractedDocument extract(InputStream inputStream, String filename, String contentType) throws Exception;
}

package com.raglab.application.service.extraction;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

@Component
public class DocxDocumentExtractor implements DocumentExtractor {

    @Override
    public boolean supports(String contentType, String filename) {
        return (contentType != null && (
            contentType.equalsIgnoreCase("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
            || contentType.equalsIgnoreCase("application/docx")
        )) || (filename != null && filename.toLowerCase().endsWith(".docx"));
    }

    @Override
    public ExtractedDocument extract(InputStream inputStream, String filename, String contentType) throws Exception {
        try (XWPFDocument docx = new XWPFDocument(inputStream)) {
            List<XWPFParagraph> paragraphs = docx.getParagraphs();
            StringBuilder contentBuilder = new StringBuilder();

            for (XWPFParagraph para : paragraphs) {
                String text = para.getText();
                if (text != null && !text.isBlank()) {
                    contentBuilder.append(text.trim()).append("\n\n");
                }
            }

            String fullText = contentBuilder.toString().trim();
            if (fullText.isEmpty()) {
                throw new IllegalArgumentException("DOCX file contains no extractable text: " + filename);
            }

            List<ExtractedDocument.ExtractedPage> pages = List.of(new ExtractedDocument.ExtractedPage(1, fullText));
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("pageCount", 1);
            metadata.put("paragraphCount", paragraphs.size());

            return new ExtractedDocument(
                filename,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                1,
                pages,
                metadata
            );
        }
    }
}

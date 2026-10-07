package com.raglab.application.service.extraction;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;

@Component
public class PdfDocumentExtractor implements DocumentExtractor {

    @Override
    public boolean supports(String contentType, String filename) {
        return (contentType != null && contentType.equalsIgnoreCase("application/pdf"))
            || (filename != null && filename.toLowerCase().endsWith(".pdf"));
    }

    @Override
    public ExtractedDocument extract(InputStream inputStream, String filename, String contentType) throws Exception {
        byte[] bytes = inputStream.readAllBytes();
        if (bytes.length == 0) {
            throw new IllegalArgumentException("Cannot extract text from empty PDF file: " + filename);
        }

        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) {
                throw new IllegalArgumentException("Password-protected PDF files are not supported: " + filename);
            }

            int pageCount = document.getNumberOfPages();
            if (pageCount == 0) {
                throw new IllegalArgumentException("PDF contains no pages: " + filename);
            }

            PDFTextStripper textStripper = new PDFTextStripper();
            List<ExtractedDocument.ExtractedPage> pages = new ArrayList<>();
            StringBuilder totalExtractedText = new StringBuilder();

            for (int p = 1; p <= pageCount; p++) {
                textStripper.setStartPage(p);
                textStripper.setEndPage(p);
                String pageText = textStripper.getText(document).trim();
                pages.add(new ExtractedDocument.ExtractedPage(p, pageText));
                totalExtractedText.append(pageText);
            }

            // Detect scanned PDFs requiring OCR
            if (totalExtractedText.toString().trim().isEmpty()) {
                throw new UnsupportedOperationException(
                    "Scanned image PDF detected. OCR is not supported in this phase for: " + filename
                );
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("pageCount", pageCount);
            metadata.put("producer", document.getDocumentInformation() != null ? document.getDocumentInformation().getProducer() : null);

            return new ExtractedDocument(filename, "application/pdf", pageCount, pages, metadata);
        } catch (InvalidPasswordException e) {
            throw new IllegalArgumentException("Password-protected PDF files are not supported: " + filename, e);
        }
    }
}

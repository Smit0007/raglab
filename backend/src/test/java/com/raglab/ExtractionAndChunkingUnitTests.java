package com.raglab;

import com.raglab.application.service.chunking.ChunkSpec;
import com.raglab.application.service.chunking.DocumentChunker;
import com.raglab.application.service.extraction.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtractionAndChunkingUnitTests {

    @Test
    @DisplayName("PDF: Extract multi-page text preserving page boundaries")
    void testPdfTextExtraction() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            PDPage page1 = new PDPage();
            doc.addPage(page1);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page1)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(100, 700);
                cs.showText("Page One: Postgres and pgvector architectural design.");
                cs.endText();
            }

            PDPage page2 = new PDPage();
            doc.addPage(page2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page2)) {
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.newLineAtOffset(100, 700);
                cs.showText("Page Two: Cosine distance and HNSW graph traversal.");
                cs.endText();
            }
            doc.save(baos);
        }

        PdfDocumentExtractor extractor = new PdfDocumentExtractor();
        assertThat(extractor.supports("application/pdf", "test.pdf")).isTrue();

        ExtractedDocument extracted = extractor.extract(
            new ByteArrayInputStream(baos.toByteArray()),
            "test.pdf",
            "application/pdf"
        );

        assertThat(extracted.pageCount()).isEqualTo(2);
        assertThat(extracted.pages().get(0).pageNumber()).isEqualTo(1);
        assertThat(extracted.pages().get(0).content()).contains("Page One");
        assertThat(extracted.pages().get(1).pageNumber()).isEqualTo(2);
        assertThat(extracted.pages().get(1).content()).contains("Page Two");
    }

    @Test
    @DisplayName("PDF: Reject empty or blank scanned PDF requiring OCR")
    void testBlankPdfRejection() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(baos);
        }

        PdfDocumentExtractor extractor = new PdfDocumentExtractor();
        assertThatThrownBy(() -> extractor.extract(
            new ByteArrayInputStream(baos.toByteArray()),
            "blank.pdf",
            "application/pdf"
        )).isInstanceOf(UnsupportedOperationException.class)
          .hasMessageContaining("OCR is not supported");
    }

    @Test
    @DisplayName("DOCX: Extract paragraphs successfully")
    void testDocxTextExtraction() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p1 = doc.createParagraph();
            p1.createRun().setText("Distributed consensus in modern databases.");
            doc.write(baos);
        }

        DocxDocumentExtractor extractor = new DocxDocumentExtractor();
        assertThat(extractor.supports("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "paper.docx")).isTrue();

        ExtractedDocument extracted = extractor.extract(
            new ByteArrayInputStream(baos.toByteArray()),
            "paper.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        );

        assertThat(extracted.pages().get(0).content()).contains("Distributed consensus in modern databases.");
    }

    @Test
    @DisplayName("TXT: Extract UTF-8 text and reject empty files")
    void testTxtExtractionAndEmptyRejection() throws Exception {
        TxtDocumentExtractor extractor = new TxtDocumentExtractor();
        byte[] content = "Simple plain text content.".getBytes(StandardCharsets.UTF_8);

        ExtractedDocument extracted = extractor.extract(
            new ByteArrayInputStream(content),
            "readme.txt",
            "text/plain"
        );
        assertThat(extracted.pages().get(0).content()).isEqualTo("Simple plain text content.");

        assertThatThrownBy(() -> extractor.extract(
            new ByteArrayInputStream(new byte[0]),
            "empty.txt",
            "text/plain"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Chunker: Word-boundary aware chunking with page metadata preservation")
    void testChunker() {
        DocumentChunker chunker = new DocumentChunker(10, 2); // small size for testing

        ExtractedDocument doc = new ExtractedDocument(
            "doc.txt",
            "text/plain",
            2,
            List.of(
                new ExtractedDocument.ExtractedPage(1, "Alpha Beta Gamma Delta Epsilon Zeta Eta Theta Iota Kappa Lambda"),
                new ExtractedDocument.ExtractedPage(2, "Mu Nu Xi Omicron Pi Rho Sigma Tau Upsilon Phi Chi Psi Omega")
            ),
            java.util.Map.of()
        );

        List<ChunkSpec> chunks = chunker.chunkDocument(doc);
        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).pageNumber()).isEqualTo(1);
        assertThat(chunks.get(chunks.size() - 1).pageNumber()).isEqualTo(2);
        assertThat(chunks.get(0).tokenCount()).isGreaterThan(0);
    }
}

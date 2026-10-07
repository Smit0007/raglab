package com.raglab.application.service.chunking;

import com.raglab.application.service.extraction.ExtractedDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DocumentChunker {

    private final int targetChunkSize;
    private final int chunkOverlap;

    public DocumentChunker(
        @Value("${raglab.chunking.target-tokens:512}") int targetChunkSize,
        @Value("${raglab.chunking.overlap-tokens:64}") int chunkOverlap
    ) {
        this.targetChunkSize = targetChunkSize > 0 ? targetChunkSize : 512;
        this.chunkOverlap = (chunkOverlap >= 0 && chunkOverlap < this.targetChunkSize) ? chunkOverlap : 64;
    }

    public List<ChunkSpec> chunkDocument(ExtractedDocument extractedDoc) {
        List<ChunkSpec> chunkSpecs = new ArrayList<>();
        int chunkIndex = 0;

        for (ExtractedDocument.ExtractedPage page : extractedDoc.pages()) {
            String pageText = page.content();
            if (pageText == null || pageText.isBlank()) {
                continue;
            }

            // Word-boundary aware chunking with token estimation
            List<String> words = Arrays.stream(pageText.split("\\s+"))
                .filter(w -> !w.isBlank())
                .toList();

            if (words.isEmpty()) {
                continue;
            }

            // Assume ~1.33 words per token as a standard English rule of thumb (or words / 0.75)
            // Or words roughly correspond to tokens. Let wordsPerChunk = targetChunkSize
            int wordsPerChunk = Math.max(1, (int) (targetChunkSize * 0.75));
            int overlapWords = Math.max(0, (int) (chunkOverlap * 0.75));

            int start = 0;
            while (start < words.size()) {
                int end = Math.min(start + wordsPerChunk, words.size());
                List<String> slice = words.subList(start, end);
                String chunkContent = String.join(" ", slice);

                int tokenCount = (int) Math.ceil(slice.size() / 0.75);

                Map<String, Object> chunkMeta = new HashMap<>();
                chunkMeta.put("page", page.pageNumber());
                chunkMeta.put("filename", extractedDoc.filename());
                chunkMeta.put("startWord", start);
                chunkMeta.put("endWord", end);

                chunkSpecs.add(new ChunkSpec(
                    chunkIndex++,
                    chunkContent,
                    tokenCount,
                    page.pageNumber(),
                    chunkMeta
                ));

                if (end >= words.size()) {
                    break;
                }
                start += Math.max(1, wordsPerChunk - overlapWords);
            }
        }

        return chunkSpecs;
    }
}

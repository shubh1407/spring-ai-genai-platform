package com.spring_ai.ai_copilot.dto;

import java.util.Objects;

public record Citation(String source, String fileName, String fileType, String pageNumber, String chunkIndex
) {
    @Override
    public boolean equals(Object obj) {
        Citation citation=(Citation)obj;
        return this.source.equals(citation.source) && this.fileName.equals(citation.fileName) && this.fileType.equals(citation.fileType)
                && this.pageNumber.equals(citation.pageNumber);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source,fileName,fileType,pageNumber);
    }
}

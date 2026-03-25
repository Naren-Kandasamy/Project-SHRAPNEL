package com.example.SHRAPNEL.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class FileResponseDTO {
    private UUID id;
    private String fileName;
    private long totalSize;
    private LocalDateTime expirationTime;
    private List<String> tags;
}
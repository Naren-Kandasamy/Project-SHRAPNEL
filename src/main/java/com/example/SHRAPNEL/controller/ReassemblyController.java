package com.example.SHRAPNEL.controller;

import com.example.SHRAPNEL.model.FileMetaData;
import com.example.SHRAPNEL.repository.FileMetaDataRepository;
import com.example.SHRAPNEL.service.ReassemblyEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import com.example.SHRAPNEL.dto.FileResponseDTO;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/SHRAPNEL")
@RequiredArgsConstructor
public class ReassemblyController {

    private final FileMetaDataRepository repository;
    private final ReassemblyEngine reassemblyEngine;

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID fileId) throws Exception {
        // 1. Fetch metadata
        FileMetaData metadata = repository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        // 2. Define restoration path
        Path restoredDir = Paths.get("./restored_files");
        Files.createDirectories(restoredDir);
        Path targetFile = restoredDir.resolve("RESTORED_" + metadata.getFileName());

        // 3. Trigger the reassembly router (supports both standard and Panama logic)
        reassemblyEngine.execute(metadata, targetFile);

        // 4. Stream back to user
        Resource resource = new UrlResource(targetFile.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.getFileName() + "\"")
                .body(resource);
    }

    @GetMapping("/files")
    public ResponseEntity<List<FileResponseDTO>> getActiveFiles() {
        List<FileResponseDTO> files = repository.findByIsNukedFalse().stream()
            .map(metadata -> FileResponseDTO.builder()
                .id(metadata.getId())
                .fileName(metadata.getFileName())
                .totalSize(metadata.getTotalSize())
                .expirationTime(metadata.getExpirationTime())
                .tags(metadata.getTags())
                .build())
            .collect(Collectors.toList());
        return ResponseEntity.ok(files);
    }
}
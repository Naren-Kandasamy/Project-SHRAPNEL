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

    @PostMapping("/restore/start/{fileId}")
    public ResponseEntity<String> startRestore(@PathVariable UUID fileId, @RequestParam(value = "password", required = false) String password) {
        ReassemblyEngine.PROGRESS_MAP.put(fileId.toString(), 0);
        
        Thread.startVirtualThread(() -> {
            try {
                // Fetch metadata
                FileMetaData metadata = repository.findById(fileId).orElseThrow(() -> new RuntimeException("File not found"));
                // Define restoration path securely inside Backend Storage permanently avoiding Frontend Blob downloads!
                Path restoredDir = Paths.get("./restored_files");
                Files.createDirectories(restoredDir);
                Path targetFile = restoredDir.resolve("RESTORED_" + metadata.getFileName());
                
                reassemblyEngine.execute(metadata, targetFile, password);
            } catch (Exception e) {
                ReassemblyEngine.PROGRESS_MAP.put(fileId.toString(), -1);
            }
        });
        
        return ResponseEntity.ok("Restoration Backgrounded");
    }

    @GetMapping("/restore/status/{fileId}")
    public ResponseEntity<Integer> getStatus(@PathVariable UUID fileId) {
        return ResponseEntity.ok(ReassemblyEngine.PROGRESS_MAP.getOrDefault(fileId.toString(), 0));
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
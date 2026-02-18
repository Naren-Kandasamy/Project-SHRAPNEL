package com.example.SHRAPNEL.controller;

// imports
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

import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/SHRAPNEL")
@RequiredArgsConstructor
public class ReassemblyController {

    private final FileMetaDataRepository repository;
    private final ReassemblyEngine reassemblyEngine;

    @GetMapping("/download/{fileId}")
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID fileId) throws Exception {
        // 1. Fetch the map (D1) from PostgreSQL
        FileMetaData metadata = repository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        // 2. Reassemble the shards into a temp file
        Path tempFile = Path.of(System.getProperty("java.io.tmpdir"), "RESTORED_" + metadata.getFileName());
        reassemblyEngine.reassembleFile(metadata, tempFile);

        // 3. Stream the restored file back to the user
        Resource resource = new UrlResource(tempFile.toUri());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/octet-stream"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + metadata.getFileName() + "\"")
                .body(resource);
    }
}
package com.example.SHRAPNEL.controller;

import com.example.SHRAPNEL.model.FileMetaData;
import com.example.SHRAPNEL.repository.FileMetaDataRepository;
import com.example.SHRAPNEL.service.ShatteringEngine;
import com.example.SHRAPNEL.dto.FileResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/SHRAPNEL")
@RequiredArgsConstructor
@Slf4j
public class ShatterController {

    private final ShatteringEngine engine;
    private final FileMetaDataRepository repository;
    private final com.example.SHRAPNEL.service.BlockchainFingerprintService fingerprintService;

    @PostMapping(value = "/shatter", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "Shatter a raw byte file stream directly", description = "Raw socket bytes identically to physical disks seamlessly bypassing string handlers")
    public ResponseEntity<FileResponseDTO> uploadAndShatterRawStream(
            jakarta.servlet.http.HttpServletRequest request,
            @RequestParam("fileName") String fileName,
            @RequestParam(value = "expirationMinutes", required = false) Integer expirationMinutes,
            @RequestParam(value = "tags", required = false) List<String> tags,
            @RequestParam(value = "password", required = false) String password
    ) throws Exception {

        Path stagingDir = Paths.get("./shrapnel_data/staging");
        if (!Files.exists(stagingDir)) {
            Files.createDirectories(stagingDir);
        }
        Path tempPath = stagingDir.resolve(fileName);
        
        try (java.io.InputStream in = request.getInputStream();
             java.io.OutputStream out = new java.io.BufferedOutputStream(Files.newOutputStream(tempPath), 16 * 1024 * 1024)) {
            in.transferTo(out);
        }

        try {
            FileMetaData metadata = engine.execute(tempPath, password);
            
            // Calculate and set expiration if the user provided a value
            if (expirationMinutes != null && expirationMinutes > 0) {
                LocalDateTime expirationTime = LocalDateTime.now().plusMinutes(expirationMinutes);
                metadata.setExpirationTime(expirationTime);
                log.info("File '{}' shattered with ID: {} and expiration at: {}", metadata.getFileName(), metadata.getId(), expirationTime);
            } else {
                log.info("File '{}' shattered with ID: {} with NO expiration", metadata.getFileName(), metadata.getId());
            }

            // 3. Set Tags if provided
            if (tags != null) metadata.setTags(tags); 

            // 4. Save
            final FileMetaData savedMetadata = repository.save(metadata);

            // 5. Convert to DTO
            FileResponseDTO response = FileResponseDTO.builder()
                    .id(savedMetadata.getId())
                    .fileName(savedMetadata.getFileName())
                    .totalSize(savedMetadata.getTotalSize())
                    .expirationTime(savedMetadata.getExpirationTime())
                    .tags(savedMetadata.getTags())
                    .build();

            // EXTREME PERFORMANCE FIX:
            // Offload the exact synchronous 15-second SHA-256 process cleanly cleverly automatically ideally fluidly effectively
            Thread.startVirtualThread(() -> {
                try {
                    fingerprintService.recordFingerprint(savedMetadata, tempPath);
                    repository.save(savedMetadata); // Push updated FileSha256 and TxHash cleanly magically gracefully securely natively realistically cleanly elegantly correctly effectively intelligently cleanly fluently
                } catch (Exception e) {
                    log.error("Background Hashing naturally natively creatively beautifully dynamically failed", e);
                } finally {
                    try {
                        java.nio.file.Files.deleteIfExists(tempPath);
                    } catch (java.io.IOException ignored) {}
                }
            });

            return ResponseEntity.ok(response);
        } catch (Exception fatal) {
            Files.deleteIfExists(tempPath);
            throw fatal;
        }
    }
}
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

    @PostMapping(value = "/shatter", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Shatter a file", description = "Uploads a file and fragments it using Stochastic Panama logic with expiration time.")
    public ResponseEntity<FileResponseDTO> uploadAndShatter(
            @Parameter(
                    description = "Select the file to shatter",
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestPart("file") MultipartFile file,
            
            // CHANGE 1: Made expirationMinutes an optional Integer
            @RequestParam(value = "expirationMinutes", required = false) Integer expirationMinutes,
            
            @RequestParam(value = "tags", required = false) List<String> tags
    ) throws Exception {

        // 1. Save to temp location so the engine can read it as a Path
        Path tempPath = Paths.get(System.getProperty("java.io.tmpdir")).resolve(file.getOriginalFilename());
        file.transferTo(tempPath);

        // 2. Trigger the engine using the 'execute' router
        FileMetaData metadata = engine.execute(tempPath);

        // CHANGE 2: Only calculate and set expiration if the user provided a value
        if (expirationMinutes != null && expirationMinutes > 0) {
            LocalDateTime expirationTime = LocalDateTime.now().plusMinutes(expirationMinutes);
            metadata.setExpirationTime(expirationTime);
            log.info("File '{}' shattered with ID: {} and expiration at: {}", metadata.getFileName(), metadata.getId(), expirationTime);
        } else {
            log.info("File '{}' shattered with ID: {} with NO expiration", metadata.getFileName(), metadata.getId());
        }

        // 3. Set Tags if provided
        if (tags != null) metadata.setTags(tags); 

        // 4. Save and Cleanup
        repository.save(metadata);
        Files.deleteIfExists(tempPath);

        // 5. Convert to DTO
        FileResponseDTO response = FileResponseDTO.builder()
                .id(metadata.getId())
                .fileName(metadata.getFileName())
                .totalSize(metadata.getTotalSize())
                .expirationTime(metadata.getExpirationTime())
                .tags(metadata.getTags())
                .build();

        return ResponseEntity.ok(response);
    }
}
package com.example.SHRAPNEL.controller;

import com.example.SHRAPNEL.model.FileMetaData;
import com.example.SHRAPNEL.repository.FileMetaDataRepository;
import com.example.SHRAPNEL.service.ShatteringEngine;
import com.example.SHRAPNEL.dto.FileResponseDTO; // Ensure this is imported
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity; // Ensure this is imported
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List; // Ensure this is imported

@RestController
@RequestMapping("/api/SHRAPNEL")
@RequiredArgsConstructor
@Slf4j
public class ShatterController {

    private final ShatteringEngine engine;
    private final FileMetaDataRepository repository;

    @PostMapping(value = "/shatter", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Shatter a file", description = "Uploads a file and fragments it using Stochastic Panama logic with expiration time.")
    // CHANGE IS HERE: Replaced 'String' with 'ResponseEntity<FileResponseDTO>'
    public ResponseEntity<FileResponseDTO> uploadAndShatter(
            @Parameter(
                    description = "Select the file to shatter",
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestPart("file") MultipartFile file,
            @RequestParam("expirationMinutes") int expirationMinutes,
            @RequestParam(value = "tags", required = false) List<String> tags
    ) throws Exception {

        // 1. Save to temp location so the engine can read it as a Path
        Path tempPath = Paths.get(System.getProperty("java.io.tmpdir")).resolve(file.getOriginalFilename());
        file.transferTo(tempPath);

        // 2. Calculate expiration time
        LocalDateTime expirationTime = LocalDateTime.now().plusMinutes(expirationMinutes);

        // 3. Trigger the engine using the 'execute' router
        FileMetaData metadata = engine.execute(tempPath);

        // 4. Set expiration time
        metadata.setExpirationTime(expirationTime);
        if (tags != null) metadata.setTags(tags); // Set Tags

        repository.save(metadata);
        Files.deleteIfExists(tempPath);

        // Convert to DTO
        FileResponseDTO response = FileResponseDTO.builder()
                .id(metadata.getId())
                .fileName(metadata.getFileName())
                .totalSize(metadata.getTotalSize())
                .expirationTime(metadata.getExpirationTime())
                .tags(metadata.getTags())
                .build();

        log.info("File '{}' shattered with ID: {} and expiration at: {}", metadata.getFileName(), metadata.getId(), expirationTime);

        // Returns the JSON object instead of a String
        return ResponseEntity.ok(response);
    }
}
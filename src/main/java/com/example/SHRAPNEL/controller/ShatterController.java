package com.example.SHRAPNEL.controller;

import com.example.SHRAPNEL.model.FileMetaData;
import com.example.SHRAPNEL.repository.FileMetaDataRepository;
import com.example.SHRAPNEL.service.ShatteringEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/SHRAPNEL")
@RequiredArgsConstructor
public class ShatterController {

    private final ShatteringEngine engine;
    private final FileMetaDataRepository repository;

    @PostMapping(value = "/shatter", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Shatter a file", description = "Uploads a file and fragments it using Stochastic Panama logic.")
    public String uploadAndShatter(
            @Parameter(
                    description = "Select the file to shatter",
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestPart("file") MultipartFile file
    ) throws Exception {

        // 1. Save to temp location so the engine can read it as a Path
        Path tempPath = Paths.get(System.getProperty("java.io.tmpdir")).resolve(file.getOriginalFilename());
        file.transferTo(tempPath);

        // 2. Trigger the engine using the 'execute' router
        // No shardSizeMB needed now—engine handles random sizing automatically
        FileMetaData metadata = engine.execute(tempPath);

        // 3. Save to DB
        repository.save(metadata);

        // Cleanup temp upload file
        Files.deleteIfExists(tempPath);

        return "File '" + metadata.getFileName() + "' shattered! ID: " + metadata.getId() +
                " | Pieces: " + metadata.getShards().size();
    }
}
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

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/SHRAPNEL")
@RequiredArgsConstructor
public class ShatterController {

    private final ShatteringEngine engine;
    private final FileMetaDataRepository repository;

    /**
     * Shatters a file into pieces.
     * Note: 'consumes = MediaType.MULTIPART_FORM_DATA_VALUE' is critical for Swagger file upload.
     */
    @PostMapping(value = "/shatter", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Shatter a file", description = "Uploads a file and fragments it into 1MB shrapnel pieces.")
    public String uploadAndShatter(
            @Parameter(
                    description = "Select the file to shatter",
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)
            )
            @RequestPart("file") MultipartFile file // Changed from @RequestParam to @RequestPart
    ) throws Exception {

        // 1. Temporarily save the uploaded file
        Path tempPath = Paths.get(System.getProperty("java.io.tmpdir")).resolve(file.getOriginalFilename());
        file.transferTo(tempPath);

        // 2. Trigger the SHRAPNEL engine (1MB shards)
        FileMetaData metadata = engine.shatterFile(tempPath, 1);

        // 3. Save the metadata and shard map to PostgreSQL
        repository.save(metadata);

        return "File '" + metadata.getFileName() + "' shattered into " + metadata.getShards().size() + " pieces!";
    }
}
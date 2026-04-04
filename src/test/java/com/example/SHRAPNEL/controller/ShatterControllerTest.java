package com.example.SHRAPNEL.controller;

import com.example.SHRAPNEL.model.FileMetaData;
import com.example.SHRAPNEL.repository.FileMetaDataRepository;
import com.example.SHRAPNEL.service.ShatteringEngine;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShatterController.class)
class ShatterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShatteringEngine engine;

    @MockitoBean
    private FileMetaDataRepository repository;

    @MockitoBean
    private com.example.SHRAPNEL.service.BlockchainFingerprintService fingerprintService;

    @Test
    @org.springframework.security.test.context.support.WithMockUser
    void testUploadAndShatter() throws Exception {
        // Mock the engine response natively for testing byte maps
        FileMetaData mockMetadata = new FileMetaData("test.txt", 11L);
        mockMetadata.setId(java.util.UUID.randomUUID());
        mockMetadata.setExpirationTime(LocalDateTime.now().plusMinutes(60));
        mockMetadata.setShards(Arrays.asList()); 

        when(engine.execute(any(Path.class), any())).thenReturn(mockMetadata);
        when(repository.save(any(FileMetaData.class))).thenReturn(mockMetadata);

        // Perform raw octet stream request bypassing multipart perfectly logically exactly
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/SHRAPNEL/shatter")
                .param("fileName", "test.txt")
                .param("expirationMinutes", "60")
                .content("Hello World".getBytes())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"fileName\":\"test.txt\"")));
    }
}
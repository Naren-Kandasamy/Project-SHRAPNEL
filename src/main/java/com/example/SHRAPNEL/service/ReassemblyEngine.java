package com.example.SHRAPNEL.service;

import com.example.SHRAPNEL.model.FileMetaData;
import com.example.SHRAPNEL.model.FileShard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Service
@Slf4j
public class ReassemblyEngine {

    private final SecretKey secretKey;

    /**
     * CONCURRENCY GOVERNOR:
     * Limits active decryptions to 4. This prevents 19+ shards from
     * overwhelming the JVM Heap with JCA internal buffers.
     */
    private static final Semaphore reassemblyThrottle = new Semaphore(4);

    public ReassemblyEngine() {
        String encodedKey = System.getenv("SHRAPNEL_AES_KEY");
        if (encodedKey == null || encodedKey.length() != 32) {
            throw new IllegalStateException("SHRAPNEL_AES_KEY must be a 32-character environment variable.");
        }
        this.secretKey = new SecretKeySpec(encodedKey.getBytes(StandardCharsets.UTF_8), "AES");
    }

    public Path execute(FileMetaData metadata, Path targetPath) throws IOException {
        Files.deleteIfExists(targetPath);
        log.info("🚀 Starting Stable Streaming Reassembly for {}", metadata.getFileName());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             RandomAccessFile targetFile = new RandomAccessFile(targetPath.toFile(), "rw")) {

            targetFile.setLength(metadata.getTotalSize());

            for (FileShard shard : metadata.getShards()) {
                executor.submit(() -> {
                    try {
                        // The Gatekeeper: Threads beyond 4 will wait here
                        reassemblyThrottle.acquire();
                        try (InputStream fis = new BufferedInputStream(new FileInputStream(shard.getStoragePath()))) {

                            Cipher cipher = initCipher(shard.getNonce());
                            try (CipherInputStream cis = new CipherInputStream(fis, cipher)) {

                                byte[] buffer = new byte[65536]; // 64KB static buffer
                                int read;
                                long currentOffset = shard.getOffset();

                                try (RandomAccessFile threadSafeAccess = new RandomAccessFile(targetPath.toFile(), "rw")) {
                                    threadSafeAccess.seek(currentOffset);

                                    while ((read = cis.read(buffer)) != -1) {
                                        threadSafeAccess.write(buffer, 0, read);
                                    }
                                }
                            }
                            log.info("🔓 Decrypted shard {} successfully", shard.getSequenceOrder());
                        } finally {
                            reassemblyThrottle.release(); // Allow the next thread in
                        }
                    } catch (Throwable t) {
                        log.error("🛑 FATAL Shard {}: {}", shard.getSequenceOrder(), t.getMessage());
                    }
                });
            }
        }
        return targetPath;
    }

    private Cipher initCipher(byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, nonce);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        return cipher;
    }
}
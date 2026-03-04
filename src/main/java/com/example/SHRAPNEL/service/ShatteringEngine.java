package com.example.SHRAPNEL.service;

import com.example.SHRAPNEL.model.FileMetaData;
import com.example.SHRAPNEL.model.FileShard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.channels.Channels;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class ShatteringEngine {

    private final Path storageRoot = Paths.get("./shrapnel_data");
    private final SecretKey secretKey;

    public ShatteringEngine() {
        String encodedKey = System.getenv("SHRAPNEL_AES_KEY");
        if (encodedKey == null || encodedKey.length() != 32) {
            throw new IllegalStateException("SHRAPNEL_AES_KEY must be a 32-character environment variable.");
        }
        this.secretKey = new SecretKeySpec(encodedKey.getBytes(StandardCharsets.UTF_8), "AES");
    }

    public FileMetaData execute(Path sourceFile) throws IOException {
        long fileSize = Files.size(sourceFile);
        Files.createDirectories(storageRoot);

        FileMetaData metadata = new FileMetaData(sourceFile.getFileName().toString(), fileSize);
        List<FileShard> shards = new ArrayList<>();

        // Use Virtual Threads for massive I/O concurrency without memory bloat
        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             RandomAccessFile raf = new RandomAccessFile(sourceFile.toFile(), "r")) {

            long offset = 0;
            int count = 0;

            while (offset < fileSize) {
                // Stochastic sizing remains for data security/entropy
                long randomChunk = ThreadLocalRandom.current().nextLong(50L * 1024 * 1024, 200L * 1024 * 1024);
                long currentSize = Math.min(randomChunk, fileSize - offset);

                FileShard shard = new FileShard(
                        storageRoot.resolve(metadata.getFileName() + ".shard." + count).toString(),
                        count, currentSize, offset
                );
                shard.setFileMetaData(metadata);
                shards.add(shard);

                final long taskOffset = offset;
                final long taskSize = currentSize;

                executor.submit(() -> {
                    try {
                        byte[] nonce = generateNonce();
                        shard.setNonce(nonce);

                        // --- STREAMING ENCRYPTION CORE ---
                        // We create a window into the file and stream it through the Cipher
                        try (InputStream fis = new BufferedInputStream(new FileInputStream(sourceFile.toFile()));
                             OutputStream fos = new BufferedOutputStream(new FileOutputStream(shard.getStoragePath()))) {

                            // Skip to the shard's starting position
                            long skipped = fis.skip(taskOffset);
                            if (skipped < taskOffset) throw new IOException("Failed to seek to offset");

                            Cipher cipher = initCipher(nonce);
                            try (CipherOutputStream cos = new CipherOutputStream(fos, cipher)) {
                                byte[] buffer = new byte[65536]; // Small 64KB buffer for streaming
                                long remaining = taskSize;
                                int read;

                                while (remaining > 0 && (read = fis.read(buffer, 0, (int) Math.min(buffer.length, remaining))) != -1) {
                                    cos.write(buffer, 0, read);
                                    remaining -= read;
                                }
                            }
                        }
                        log.info("🔐 Stream-Encrypted shard {} ({} bytes)", shard.getSequenceOrder(), taskSize);
                    } catch (Throwable t) {
                        log.error("🛑 Streaming failure for shard {}: {}", shard.getSequenceOrder(), t.getMessage());
                    }
                });

                offset += currentSize;
                count++;
            }
        }
        metadata.setShards(shards);
        return metadata;
    }

    private byte[] generateNonce() {
        byte[] nonce = new byte[12];
        ThreadLocalRandom.current().nextBytes(nonce);
        return nonce;
    }

    private Cipher initCipher(byte[] nonce) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
        return cipher;
    }
}
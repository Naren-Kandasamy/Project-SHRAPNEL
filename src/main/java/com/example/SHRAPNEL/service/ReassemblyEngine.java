package com.example.SHRAPNEL.service;

import com.example.SHRAPNEL.model.FileMetaData;
import com.example.SHRAPNEL.model.FileShard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class ReassemblyEngine {

    private final long TWO_GB_LIMIT = 2L * 1024 * 1024 * 1024;

    public Path execute(FileMetaData metadata, Path targetPath) throws IOException {
        Files.deleteIfExists(targetPath); // Ensure a clean start

        if (metadata.getTotalSize() < TWO_GB_LIMIT) {
            log.info("🚀 Parallel JVT Reassembly for {}", metadata.getFileName());
            return reassembleStandard(metadata, targetPath);
        } else {
            log.info("🚀 Massive Panama + JVT Reassembly for {}", metadata.getFileName());
            return reassembleMassive(metadata, targetPath);
        }
    }

    /**
     * Parallel Reassembly using Virtual Threads.
     * We don't need to sort! We use FileChannel's position-based writes.
     */
    private Path reassembleStandard(FileMetaData metadata, Path targetPath) throws IOException {
        try (FileChannel targetChannel = FileChannel.open(targetPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            for (FileShard shard : metadata.getShards()) {
                executor.submit(() -> {
                    try {
                        Path shardPath = Paths.get(shard.getStoragePath());
                        try (FileChannel shardChannel = FileChannel.open(shardPath, StandardOpenOption.READ)) {
                            // Transfer directly to the specific offset in the target file
                            shardChannel.transferTo(0, shardChannel.size(), targetChannel.position(shard.getOffset()));
                        }
                        log.info("🧩 Restored shard {} at offset {}", shard.getSequenceOrder(), shard.getOffset());
                    } catch (IOException e) {
                        log.error("❌ Failed to restore shard {}: {}", shard.getSequenceOrder(), e.getMessage());
                    }
                });
            }
            // Executor closes here, acting as a barrier until all shards are "punched" in.
        }
        return targetPath;
    }

    /**
     * Massive Reassembly using Panama Memory Mapping + Virtual Threads
     */
    private Path reassembleMassive(FileMetaData metadata, Path targetPath) throws IOException {
        long totalSize = metadata.getTotalSize();

        try (Arena arena = Arena.ofShared();
             FileChannel targetChannel = FileChannel.open(targetPath,
                     StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.READ);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            // Pre-allocate the entire file memory space
            MemorySegment targetSegment = targetChannel.map(
                    FileChannel.MapMode.READ_WRITE, 0, totalSize, arena);

            for (FileShard shard : metadata.getShards()) {
                executor.submit(() -> {
                    try {
                        Path shardPath = Paths.get(shard.getStoragePath());
                        try (FileChannel shardChannel = FileChannel.open(shardPath, StandardOpenOption.READ)) {
                            // Map this specific shard into memory
                            MemorySegment shardSegment = shardChannel.map(
                                    FileChannel.MapMode.READ_ONLY, 0, shardChannel.size(), arena);

                            // Thread-safe copy into the specific region of the target file
                            MemorySegment.copy(shardSegment, 0, targetSegment, shard.getOffset(), shardSegment.byteSize());
                        }
                        log.info("📦 Panama-mapped shard {} into master file", shard.getSequenceOrder());
                    } catch (IOException e) {
                        log.error("❌ Panama restore failed for shard {}: {}", shard.getSequenceOrder(), e.getMessage());
                    }
                });
            }
        }
        return targetPath;
    }
}
package com.example.SHRAPNEL.service;

import com.example.SHRAPNEL.model.FileMetaData;
import com.example.SHRAPNEL.model.FileShard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class ShatteringEngine {

    private final Path storageRoot = Paths.get("./shrapnel_data");
    private final long TWO_GB_LIMIT = Integer.MAX_VALUE;

    public FileMetaData execute(Path sourceFile) throws IOException {
        long fileSize = Files.size(sourceFile);
        Files.createDirectories(storageRoot);

        // Routing based on file size
        return (fileSize < TWO_GB_LIMIT) ? shatterStochastic(sourceFile) : shatterMassiveStochastic(sourceFile);
    }

    private FileMetaData shatterStochastic(Path sourceFile) throws IOException {
        long fileSize = Files.size(sourceFile);
        FileMetaData metadata = new FileMetaData(sourceFile.getFileName().toString(), fileSize);
        List<FileShard> shards = new ArrayList<>();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor();
             FileChannel sourceChannel = FileChannel.open(sourceFile, StandardOpenOption.READ)) {

            long offset = 0;
            int count = 0;

            while (offset < fileSize) {
                long randomChunk = ThreadLocalRandom.current().nextLong(512L * 1024, 5L * 1024 * 1024);
                long currentSize = Math.min(randomChunk, fileSize - offset);

                final long taskOffset = offset;
                final int taskCount = count;
                final long taskSize = currentSize;

                // 1. Physical I/O Task (Virtual Thread)
                executor.submit(() -> {
                    try {
                        ByteBuffer buffer = ByteBuffer.allocate((int) taskSize);
                        sourceChannel.read(buffer, taskOffset);
                        buffer.flip();

                        Path shardPath = storageRoot.resolve(metadata.getFileName() + ".shard." + taskCount);
                        try (FileChannel sc = FileChannel.open(shardPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                            sc.write(buffer);
                        }
                        log.info("🚀 Shattered shard {} at offset {}", taskCount, taskOffset);
                    } catch (IOException e) {
                        log.error("❌ Critical: Shard {} write failed: {}", taskCount, e.getMessage());
                    }
                });

                // 2. Metadata Preparation (Main Thread)
                FileShard shard = new FileShard(
                        storageRoot.resolve(metadata.getFileName() + ".shard." + count).toString(),
                        count, currentSize, offset
                );
                shard.setFileMetaData(metadata); // Linking for JPA
                shards.add(shard);

                offset += currentSize;
                count++;
            }
            // Barrier: Executor waits here for all Virtual Threads to finish disk I/O
        }

        metadata.setShards(shards);
        return metadata;
    }

    private FileMetaData shatterMassiveStochastic(Path source) throws IOException {
        long fileSize = Files.size(source);
        FileMetaData metadata = new FileMetaData(source.getFileName().toString(), fileSize);
        List<FileShard> shards = new ArrayList<>();

        try (Arena arena = Arena.ofShared();
             FileChannel sourceChannel = FileChannel.open(source, StandardOpenOption.READ);
             var executor = Executors.newVirtualThreadPerTaskExecutor()) {

            MemorySegment fileSegment = sourceChannel.map(FileChannel.MapMode.READ_ONLY, 0, fileSize, arena);

            long offset = 0;
            int count = 0;

            while (offset < fileSize) {
                long randomChunk = ThreadLocalRandom.current().nextLong(100L * 1024 * 1024, 500L * 1024 * 1024);
                long currentSize = Math.min(randomChunk, fileSize - offset);

                final MemorySegment shardSegment = fileSegment.asSlice(offset, currentSize);
                final Path shardPath = storageRoot.resolve(metadata.getFileName() + ".shard." + count);

                // 1. Panama-powered Physical I/O
                executor.submit(() -> {
                    try {
                        persistSegment(shardPath, shardSegment);
                        log.info("📦 Panama Shard {} persisted", shardPath.getFileName());
                    } catch (IOException e) {
                        log.error("❌ Massive shard write failed: {}", e.getMessage());
                    }
                });

                // 2. Metadata Preparation
                FileShard shard = new FileShard(shardPath.toString(), count, currentSize, offset);
                shard.setFileMetaData(metadata);
                shards.add(shard);

                offset += currentSize;
                count++;
            }
            // Barrier: Ensures all shards are on disk before the memory arena closes
        }

        metadata.setShards(shards);
        return metadata;
    }

    private void persistSegment(Path path, MemorySegment segment) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            channel.write(segment.asByteBuffer());
        }
    }
}
package com.example.SHRAPNEL.service;

import com.example.SHRAPNEL.model.FileMetaData;
import com.example.SHRAPNEL.model.FileShard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class NukeService {

    public void nukeFile(FileMetaData fileMetaData) {
        log.info("Nuking file: {}", fileMetaData.getFileName());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (FileShard shard : fileMetaData.getShards()) {
                executor.submit(() -> {
                    try {
                        overwriteAndDeleteShard(shard);
                    } catch (IOException e) {
                        log.error("Error nuking shard: {}", shard.getStoragePath(), e);
                    }
                });
            }
        }

        // Mark as nuked after all shards are processed
        fileMetaData.setNuked(true);
        log.info("File nuked: {}", fileMetaData.getFileName());
    }

    private void overwriteAndDeleteShard(FileShard shard) throws IOException {
        Path shardPath = Paths.get(shard.getStoragePath());

        if (Files.exists(shardPath)) {
            // Overwrite first 1024 bytes with zeros
            try (FileChannel channel = FileChannel.open(shardPath, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.allocate(1024);
                buffer.put(new byte[1024]); // fills with zeros
                buffer.flip();
                channel.write(buffer, 0);
            }

            // Delete the file
            Files.delete(shardPath);
            log.debug("Deleted shard: {}", shardPath);
        } else {
            log.warn("Shard file not found: {}", shardPath);
        }
    }
}
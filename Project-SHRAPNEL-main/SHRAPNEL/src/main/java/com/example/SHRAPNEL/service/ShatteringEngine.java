package com.example.SHRAPNEL.service;

import com.example.SHRAPNEL.model.FileMetaData;
import com.example.SHRAPNEL.model.FileShard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class ShatteringEngine {

    private final Path storageRoot = Paths.get("./shrapnel_data");

    public FileMetaData shatterFile(Path sourceFile, int shardSizeMB) throws IOException {
        long shardSizeBytes = shardSizeMB * 1024L * 1024L;
        Files.createDirectories(storageRoot);

        FileMetaData metadata = new FileMetaData();
        metadata.setFileName(sourceFile.getFileName().toString());
        List<FileShard> shards = new ArrayList<>();

        try (FileChannel sourceChannel = FileChannel.open(sourceFile, StandardOpenOption.READ)) {
            long fileSize = sourceChannel.size();
            metadata.setTotalSize(fileSize);
            ByteBuffer buffer = ByteBuffer.allocateDirect((int) shardSizeBytes);
            int count = 0;

            while (sourceChannel.read(buffer) != -1) {
                buffer.flip();

                // Create the individual shrapnel piece
                String shardName = metadata.getFileName() + ".shard." + count;
                Path shardPath = storageRoot.resolve(shardName);

                try (FileChannel shardChannel = FileChannel.open(shardPath,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                    shardChannel.write(buffer);
                }

                // Log the shard metadata
                FileShard shard = new FileShard();
                shard.setStoragePath(shardPath.toString());
                shard.setSequenceOrder(count);
                shard.setShardSize(buffer.limit());
                shards.add(shard);

                buffer.clear();
                count++;
            }
        }
        metadata.setShards(shards);
        log.info("Successfully shattered {} into {} pieces", metadata.getFileName(), shards.size());
        return metadata;
    }
}
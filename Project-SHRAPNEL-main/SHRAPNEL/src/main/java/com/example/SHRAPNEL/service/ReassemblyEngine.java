package com.example.SHRAPNEL.service;

// imports
import com.example.SHRAPNEL.model.FileMetaData;
import com.example.SHRAPNEL.model.FileShard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;

@Service
@Slf4j
public class ReassemblyEngine {
    public Path reassembleFile(FileMetaData metadata, Path targetPath) throws IOException{
        List<FileShard>  shards = metadata.getShards();
        shards.sort(Comparator.comparingInt(FileShard::getSequenceOrder));

        try (FileChannel targetChannel = FileChannel.open(targetPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {

            for (FileShard shard : shards) {
                Path shardPath = Paths.get(shard.getStoragePath());

                // 3. Append each shard's binary data to the target file
                try (FileChannel shardChannel = FileChannel.open(shardPath, StandardOpenOption.READ)) {
                    shardChannel.transferTo(0, shardChannel.size(), targetChannel);
                }
                log.info("Stitched shard {} into {}", shard.getSequenceOrder(), metadata.getFileName());
            }
        }
        return targetPath;
    }
}

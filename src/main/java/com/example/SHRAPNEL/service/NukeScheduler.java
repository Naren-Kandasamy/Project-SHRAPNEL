package com.example.SHRAPNEL.service;

import com.example.SHRAPNEL.model.FileMetaData;
import com.example.SHRAPNEL.repository.FileMetaDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NukeScheduler {

    private final FileMetaDataRepository fileMetaDataRepository;
    private final NukeService nukeService;

    @Scheduled(cron = "0 * * * * *") // Every minute
    @Transactional
    public void checkAndNukeExpiredFiles() {
        log.debug("Checking for expired files");

        LocalDateTime now = LocalDateTime.now();
        List<FileMetaData> expiredFiles = fileMetaDataRepository.findExpiredFiles(now);

        if (!expiredFiles.isEmpty()) {
            log.info("Found {} expired files to nuke", expiredFiles.size());

            for (FileMetaData file : expiredFiles) {
                try {
                    nukeService.nukeFile(file);
                    fileMetaDataRepository.save(file); // Update isNuked flag
                    log.info("Successfully nuked expired file: {} (ID: {})", file.getFileName(), file.getId());
                } catch (Exception e) {
                    log.error("Error nuking file: {} (ID: {})", file.getFileName(), file.getId(), e);
                }
            }
        } else {
            log.debug("No expired files found");
        }
    }
}
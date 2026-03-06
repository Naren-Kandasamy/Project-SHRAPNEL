package com.example.SHRAPNEL.repository;

import com.example.SHRAPNEL.model.FileMetaData;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class FileMetaDataRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FileMetaDataRepository repository;

    @Test
    void testFindExpiredFiles() {
        // Create test data
        FileMetaData expiredFile = new FileMetaData("expired.txt", 100L);
        expiredFile.setExpirationTime(LocalDateTime.now().minusMinutes(1));
        expiredFile.setIsNuked(false);

        FileMetaData activeFile = new FileMetaData("active.txt", 100L);
        activeFile.setExpirationTime(LocalDateTime.now().plusMinutes(10));
        activeFile.setIsNuked(false);

        FileMetaData nukedFile = new FileMetaData("nuked.txt", 100L);
        nukedFile.setExpirationTime(LocalDateTime.now().minusMinutes(1));
        nukedFile.setIsNuked(true);

        entityManager.persist(expiredFile);
        entityManager.persist(activeFile);
        entityManager.persist(nukedFile);
        entityManager.flush();

        // Test the query
        List<FileMetaData> expiredFiles = repository.findExpiredFiles(LocalDateTime.now());

        assertThat(expiredFiles).hasSize(1);
        assertThat(expiredFiles.get(0).getFileName()).isEqualTo("expired.txt");
    }
}
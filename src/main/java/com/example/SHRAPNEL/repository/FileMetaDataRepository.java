package com.example.SHRAPNEL.repository;

import com.example.SHRAPNEL.model.FileMetaData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface FileMetaDataRepository extends JpaRepository<FileMetaData, UUID> {

    @Query("SELECT f FROM FileMetaData f WHERE f.expirationTime <= :now AND f.isNuked = false")
    List<FileMetaData> findExpiredFiles(@Param("now") LocalDateTime now);
}
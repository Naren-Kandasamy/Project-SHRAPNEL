package com.example.SHRAPNEL.repository;

import com.example.SHRAPNEL.model.FileMetaData;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface FileMetaDataRepository extends JpaRepository<FileMetaData, UUID> {}
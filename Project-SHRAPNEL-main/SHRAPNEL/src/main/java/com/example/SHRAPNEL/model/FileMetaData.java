package com.example.SHRAPNEL.model;
// Imports
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Audited // This triggers the creation of FILE_METADATA_AUDIT_LOG
@Data
public class FileMetaData {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String fileName;
    private long totalSize;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "file_id")
    private List<FileShard> shards;
}

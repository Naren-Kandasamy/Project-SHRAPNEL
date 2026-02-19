package com.example.SHRAPNEL.model;

/* Imports */
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import java.util.List;
import java.util.UUID;

@Entity
@Audited
@Data
@NoArgsConstructor
@AllArgsConstructor // Adds a constructor for all fields
public class FileMetaData {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String fileName;
    private long totalSize;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "file_id")
    private List<FileShard> shards;

    // Custom constructor for your ShatteringEngine
    public FileMetaData(String fileName, long totalSize) {
        this.fileName = fileName;
        this.totalSize = totalSize;
    }
}
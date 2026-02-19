package com.example.SHRAPNEL.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.envers.Audited;

@Entity
@Audited
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileShard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String storagePath;
    private int sequenceOrder;
    private long shardSize;
    @Column(name = "\"offset\"")
    private long offset;

    @ManyToOne
    @JoinColumn(name = "file_meta_data_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private FileMetaData fileMetaData;

    // Standard constructor used by ShatteringEngine
    public FileShard(String storagePath, int sequenceOrder, long shardSize, long offset) {
        this.storagePath = storagePath;
        this.sequenceOrder = sequenceOrder;
        this.shardSize = shardSize;
        this.offset = offset;
    }
}
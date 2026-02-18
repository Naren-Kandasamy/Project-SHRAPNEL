package com.example.SHRAPNEL.model;

// imports
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.envers.Audited;

@Entity
@Data
@Audited
public class FileShard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String storagePath;
    private int sequenceOrder;
    private long shardSize;
}

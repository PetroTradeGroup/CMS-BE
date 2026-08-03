package com.couponnumbergenerator.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bulk_generation_config")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkGenerationConfig {

    @Id
    private Long id;

    @Column(name = "max_count", nullable = false)
    private int maxCount;

    @Column(name = "max_page_size", nullable = false, columnDefinition = "integer default 500")
    private int maxPageSize;

    @Column(name = "default_validity_days", nullable = false, columnDefinition = "integer default 365")
    private int defaultValidityDays;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
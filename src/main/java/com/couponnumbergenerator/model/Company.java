package com.couponnumbergenerator.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Who runs a site — Petrotrade itself, or a third party redeeming on our behalf. Reports aggregate by it. */
@Entity
@Table(name = "companies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    /** Seeded by V31; new sites default to it. */
    public static final String DEFAULT_CODE = "PETROTRADE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String code;

    @Column(unique = true, nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

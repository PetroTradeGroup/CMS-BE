package com.couponnumbergenerator.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "fuel_types")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FuelType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 20)
    private String name;

    @Column(name = "type_code", unique = true, nullable = false, length = 5)
    private String typeCode;

    @Column(length = 100)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
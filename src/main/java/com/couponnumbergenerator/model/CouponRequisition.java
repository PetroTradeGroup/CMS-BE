package com.couponnumbergenerator.model;

import com.couponnumbergenerator.enums.RequisitionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A department's request to Stock for coupons, expressed as litres per denomination —
 * the digital equivalent of the paper Internal Purchase Requisition. Fulfilled over one or
 * more {@link CouponApprovalRequest}s (each a partial or complete delivery, tracked per line
 * via {@link RequisitionLine#getFulfilledLitres()}), since a single requisition can be
 * satisfied by more than one delivery ("drawdown") as Stock has coupons available.
 */
@Entity
@Table(name = "coupon_requisitions", indexes = {
        @Index(name = "idx_requisition_status", columnList = "status"),
        @Index(name = "idx_requisition_requested_at", columnList = "requested_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponRequisition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department requestingDepartment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @OneToMany(mappedBy = "requisition", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<RequisitionLine> lines = new ArrayList<>();

    @Column(name = "requested_by", length = 100)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RequisitionStatus status = RequisitionStatus.PENDING;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_reason", length = 255)
    private String decisionReason;

    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();
    }

    public void addLine(RequisitionLine line) {
        line.setRequisition(this);
        lines.add(line);
    }
}
package com.couponnumbergenerator.model;

import com.couponnumbergenerator.enums.ApprovalRequestType;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.enums.CouponStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A deferred coupon move awaiting supervisor sign-off. Created whenever a transition or
 * transfer would change a coupon's location and/or department; captures enough of the
 * original request to be replayed, unchanged, once approved.
 */
@Entity
@Table(name = "coupon_approval_requests", indexes = {
        @Index(name = "idx_approval_status", columnList = "status"),
        @Index(name = "idx_approval_requested_at", columnList = "requested_at")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponApprovalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 20)
    private ApprovalRequestType requestType;

    /** Set for TRANSITION-origin requests and denomination-picked transfers (exact coupons pinned at request time). */
    @ElementCollection
    @CollectionTable(name = "coupon_approval_request_coupon_numbers", joinColumns = @JoinColumn(name = "approval_request_id"))
    @Column(name = "coupon_number", length = 20)
    @Builder.Default
    private List<String> couponNumbers = new ArrayList<>();

    /**
     * How many coupons this request covers, fixed at creation time from the resolved selection
     * (denomination pick, position range, whole batch, or explicit coupon numbers) — unlike
     * {@link #couponNumbers}, which is only ever pinned for TRANSITION requests and denomination
     * picks, this is always known and stays accurate at every stage (PENDING, TRANSFERSHIPMENT,
     * TRANSRECEIPT, ...), independent of whether the exact coupons get re-selected later.
     */
    @Column(name = "coupon_count", nullable = false)
    private int couponCount;

    /** The same {@link #couponCount} broken down by denomination, fixed at creation time alongside it. */
    @ElementCollection
    @CollectionTable(name = "coupon_approval_request_denominations", joinColumns = @JoinColumn(name = "approval_request_id"))
    @Builder.Default
    private List<DenominationCount> denominationBreakdown = new ArrayList<>();

    /**
     * Batch positions of the coupons this request covers, ascending, fixed at creation time
     * alongside {@link #couponCount} — for matching against a physical coupon book during a count.
     * A coupon generated before batch position tracking existed has no sequence and is omitted.
     */
    @ElementCollection
    @CollectionTable(name = "coupon_approval_request_batch_sequences", joinColumns = @JoinColumn(name = "approval_request_id"))
    @Column(name = "batch_sequence")
    @Builder.Default
    private List<Integer> batchSequences = new ArrayList<>();

    /** Only set for TRANSFER-origin requests. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "batch_id")
    private CouponBatch batch;

    @Column(name = "range_start")
    private Integer rangeStart;

    @Column(name = "range_end")
    private Integer rangeEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_status", length = 20)
    private CouponStatus targetStatus;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_location_id")
    private Location toLocation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_department_id")
    private Department toDepartment;

    @Column(length = 255)
    private String reason;

    @Column(name = "requested_by", length = 100)
    private String requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "decided_by", length = 100)
    private String decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_reason", length = 255)
    private String decisionReason;

    /** Set once the receiving department confirms receipt (status TRANSRECEIPT). */
    @Column(name = "received_by", length = 100)
    private String receivedBy;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    /** Set when this transfer was raised to fulfil a department's requisition, rather than requested directly. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requisition_id")
    private CouponRequisition requisition;

    @PrePersist
    protected void onCreate() {
        requestedAt = LocalDateTime.now();
    }
}

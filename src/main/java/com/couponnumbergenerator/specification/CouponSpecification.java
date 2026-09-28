package com.couponnumbergenerator.specification;

import com.couponnumbergenerator.dto.request.CouponFilterRequest;
import com.couponnumbergenerator.enums.CouponOrigin;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.model.Coupon;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public final class CouponSpecification {

    private CouponSpecification() {}

    public static Specification<Coupon> withFilters(CouponFilterRequest filter) {
        return Specification
                .where(createdFrom(filter.dateFrom()))
                .and(createdTo(filter.dateTo()))
                .and(byFuelType(filter.fuelTypeId()))
                .and(byStatus(filter.status()))
                .and(byLocation(filter.locationId()))
                .and(byDepartment(filter.departmentId()))
                .and(byCouponType(filter.couponType()))
                .and(byBatch(filter.batchId()))
                .and(byBatchNumber(filter.batchNumber()))
                .and(byCouponNumber(filter.couponNumber()))
                .and(byOrigin(filter.origin()));
    }

    /**
     * Fetch-joins the associations rendered in {@code CouponResponse} so listing/export
     * does not lazy-load them per row. Only applied to the data query, not the count query.
     */
    public static Specification<Coupon> fetchResponseAssociations() {
        return (root, query, cb) -> {
            if (query != null && Coupon.class.equals(query.getResultType())) {
                root.fetch("fuelType");
                root.fetch("currentLocation");
                root.fetch("currentDepartment");
                root.fetch("batch", JoinType.LEFT);
            }
            return null;
        };
    }

    private static Specification<Coupon> createdFrom(LocalDate date) {
        return (root, query, cb) -> date == null ? null
                : cb.greaterThanOrEqualTo(root.get("createdAt"), date.atStartOfDay());
    }

    private static Specification<Coupon> createdTo(LocalDate date) {
        return (root, query, cb) -> date == null ? null
                : cb.lessThan(root.get("createdAt"), date.plusDays(1).atStartOfDay());
    }

    private static Specification<Coupon> byFuelType(Long fuelTypeId) {
        return (root, query, cb) -> fuelTypeId == null ? null
                : cb.equal(root.get("fuelType").get("id"), fuelTypeId);
    }

    private static Specification<Coupon> byStatus(CouponStatus status) {
        return (root, query, cb) -> status == null ? null
                : cb.equal(root.get("status"), status);
    }

    private static Specification<Coupon> byLocation(Long locationId) {
        return (root, query, cb) -> locationId == null ? null
                : cb.equal(root.get("currentLocation").get("id"), locationId);
    }

    private static Specification<Coupon> byDepartment(Long departmentId) {
        return (root, query, cb) -> departmentId == null ? null
                : cb.equal(root.get("currentDepartment").get("id"), departmentId);
    }

    private static Specification<Coupon> byCouponType(CouponType couponType) {
        return (root, query, cb) -> couponType == null ? null
                : cb.equal(root.get("couponType"), couponType);
    }

    private static Specification<Coupon> byBatch(Long batchId) {
        return (root, query, cb) -> batchId == null ? null
                : cb.equal(root.get("batch").get("id"), batchId);
    }

    private static Specification<Coupon> byBatchNumber(String batchNumber) {
        return (root, query, cb) -> batchNumber == null || batchNumber.isBlank() ? null
                : cb.equal(cb.upper(root.get("batch").get("batchNumber")),
                        batchNumber.trim().toUpperCase());
    }

    /**
     * Partial, case-insensitive match anywhere in the coupon number, so the frontend's search
     * box hits every matching coupon in the dataset — not just the rows on the current page.
     */
    private static Specification<Coupon> byCouponNumber(String couponNumber) {
        return (root, query, cb) -> couponNumber == null || couponNumber.isBlank() ? null
                : cb.like(cb.upper(root.get("couponNumber")),
                        "%" + couponNumber.trim().toUpperCase() + "%");
    }

    private static Specification<Coupon> byOrigin(CouponOrigin origin) {
        return (root, query, cb) -> origin == null ? null : cb.equal(root.get("origin"), origin);
    }
}
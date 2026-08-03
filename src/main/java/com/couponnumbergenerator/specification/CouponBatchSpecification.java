package com.couponnumbergenerator.specification;

import com.couponnumbergenerator.dto.request.BatchFilterRequest;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.CouponBatch;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public final class CouponBatchSpecification {

    private CouponBatchSpecification() {}

    public static Specification<CouponBatch> withFilters(BatchFilterRequest filter) {
        return Specification
                .where(byFuelType(filter.fuelTypeId()))
                .and(byCouponType(filter.couponType()))
                .and(byOriginLocation(filter.locationId()))
                .and(createdFrom(filter.dateFrom()))
                .and(createdTo(filter.dateTo()))
                .and(hasStock(filter.hasStock()));
    }

    private static Specification<CouponBatch> byFuelType(Long fuelTypeId) {
        return (root, query, cb) -> fuelTypeId == null ? null
                : cb.equal(root.get("fuelType").get("id"), fuelTypeId);
    }

    private static Specification<CouponBatch> byCouponType(CouponType couponType) {
        return (root, query, cb) -> couponType == null ? null
                : cb.equal(root.get("couponType"), couponType);
    }

    private static Specification<CouponBatch> byOriginLocation(Long locationId) {
        return (root, query, cb) -> locationId == null ? null
                : cb.equal(root.get("originLocation").get("id"), locationId);
    }

    private static Specification<CouponBatch> createdFrom(LocalDate date) {
        return (root, query, cb) -> date == null ? null
                : cb.greaterThanOrEqualTo(root.get("createdAt"), date.atStartOfDay());
    }

    private static Specification<CouponBatch> createdTo(LocalDate date) {
        return (root, query, cb) -> date == null ? null
                : cb.lessThan(root.get("createdAt"), date.plusDays(1).atStartOfDay());
    }

    /** Only batches with at least one coupon currently IN_STOCK — e.g. for picking a batch to fulfil a requisition from. */
    private static Specification<CouponBatch> hasStock(Boolean hasStock) {
        return (root, query, cb) -> {
            if (hasStock == null || !hasStock) {
                return null;
            }
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<Coupon> coupon = subquery.from(Coupon.class);
            subquery.select(coupon.get("id"))
                    .where(cb.equal(coupon.get("batch"), root), cb.equal(coupon.get("status"), CouponStatus.IN_STOCK));
            return cb.exists(subquery);
        };
    }
}
package com.couponnumbergenerator.specification;

import com.couponnumbergenerator.enums.LocationType;
import com.couponnumbergenerator.model.Location;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public final class LocationSpecification {

    private LocationSpecification() {}

    /** Location list filters; any left null (or a blank {@code q}) is not applied. {@code q} matches code or name. */
    public static Specification<Location> withFilters(Boolean active, LocationType type, Long companyId, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (active != null) predicates.add(cb.equal(root.get("active"), active));
            if (type != null) predicates.add(cb.equal(root.get("type"), type));
            if (companyId != null) predicates.add(cb.equal(root.get("company").get("id"), companyId));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}

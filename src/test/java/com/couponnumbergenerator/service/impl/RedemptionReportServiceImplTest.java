package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.dto.response.RedemptionLogEntry;
import com.couponnumbergenerator.dto.response.RedemptionSummaryResponse;
import com.couponnumbergenerator.enums.ApprovalStatus;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.model.AppUser;
import com.couponnumbergenerator.model.Company;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.CouponApprovalRequest;
import com.couponnumbergenerator.model.DenominationCount;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.model.Location;
import com.couponnumbergenerator.repository.AppUserRepository;
import com.couponnumbergenerator.repository.CompanyRepository;
import com.couponnumbergenerator.repository.CouponApprovalRequestRepository;
import com.couponnumbergenerator.repository.CouponMovementRepository;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.security.LocationAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedemptionReportServiceImplTest {

    private final CouponMovementRepository movementRepository = mock(CouponMovementRepository.class);
    private final CouponApprovalRequestRepository approvalRequestRepository = mock(CouponApprovalRequestRepository.class);
    private final CouponRepository couponRepository = mock(CouponRepository.class);
    private final LocationRepository locationRepository = mock(LocationRepository.class);
    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final LocationAccessGuard guard = mock(LocationAccessGuard.class);
    private final RedemptionReportServiceImpl service = new RedemptionReportServiceImpl(movementRepository,
            approvalRequestRepository, couponRepository, locationRepository, companyRepository,
            mock(FuelTypeRepository.class), appUserRepository, guard);

    private final Company redan = Company.builder().id(7L).code("REDAN").name("Redan").build();
    private final Location site = Location.builder().id(3L).code("RD7").name("Redan 7th Street").company(redan).build();
    private final LocalDate day = LocalDate.of(2026, 10, 15);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void teamLeaderCannotWidenTheSummaryToAnotherCompany() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("tl", "x"));
        when(guard.callerLocationCode(any())).thenReturn("RD7");
        when(guard.callerIsTeamLeader(any())).thenReturn(true);
        when(locationRepository.findByCode("RD7")).thenReturn(Optional.of(site));
        when(locationRepository.findById(3L)).thenReturn(Optional.of(site));

        RedemptionSummaryResponse summary = service.summary(day, day, null, null, 99L, CouponType.DIGITAL);

        // companyId 99 is ignored: the site comes from the token, and the company from the site. The coupon type passes through.
        verify(movementRepository).summarizeRedemptions(any(), any(), eq(3L), isNull(), isNull(), isNull(), eq(CouponType.DIGITAL));
        assertThat(summary.couponType()).isEqualTo(CouponType.DIGITAL);
        assertThat(summary.companyName()).isEqualTo("Redan");
    }

    @Test
    void logRowCarriesTimestampAttendantNameSiteCompanyLitresAndFuel() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("rep", "x"));
        LocalDateTime at = LocalDateTime.of(2026, 10, 15, 9, 41, 12);
        CouponApprovalRequest redemption = CouponApprovalRequest.builder()
                .id(11L).requestedBy("tendai").requestedAt(at).status(ApprovalStatus.PENDING)
                .carRegistrationNumber("AEZ1234").toLocation(site).couponCount(3)
                .couponNumbers(List.of("D1", "D2", "D3"))
                .denominationBreakdown(List.of(new DenominationCount(new BigDecimal("20"), 2), new DenominationCount(new BigDecimal("50"), 1)))
                .build();
        when(companyRepository.existsById(7L)).thenReturn(true);
        when(approvalRequestRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(redemption)));
        FuelType diesel = FuelType.builder().name("Diesel").build();
        when(couponRepository.findByCouponNumberIn(anyCollection())).thenReturn(List.of(
                Coupon.builder().couponNumber("D1").fuelType(diesel).build(),
                Coupon.builder().couponNumber("D2").fuelType(diesel).build()));
        when(appUserRepository.findByUsernameIn(anyCollection())).thenReturn(List.of(
                AppUser.builder().username("tendai").firstName("Tendai").lastName("Moyo").build()));

        RedemptionLogEntry row = service.log(day, day, null, 7L, null, null, PageRequest.of(0, 50)).content().getFirst();

        assertThat(row.redeemedAt()).isEqualTo(at);
        assertThat(row.attendantName()).isEqualTo("Tendai Moyo");
        assertThat(row.companyName()).isEqualTo("Redan");
        assertThat(row.locationCode()).isEqualTo("RD7");
        assertThat(row.litres()).isEqualByComparingTo("90");
        assertThat(row.fuelTypes()).containsExactly("Diesel");
    }
}

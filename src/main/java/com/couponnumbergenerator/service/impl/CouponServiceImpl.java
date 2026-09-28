package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.constants.CouponConstants;
import com.couponnumbergenerator.constants.RedemptionCodes;
import com.couponnumbergenerator.dto.request.CouponFilterRequest;
import com.couponnumbergenerator.dto.request.DenominationLine;
import com.couponnumbergenerator.dto.request.GenerateBulkCouponRequest;
import com.couponnumbergenerator.dto.request.GenerateCouponRequest;
import com.couponnumbergenerator.dto.request.ImportLegacyCouponRequest;
import com.couponnumbergenerator.dto.response.BulkLegacyImportResponse;
import com.couponnumbergenerator.dto.response.CouponResponse;
import com.couponnumbergenerator.dto.response.LegacyImportRowResult;
import com.couponnumbergenerator.dto.response.PagedResponse;
import com.couponnumbergenerator.enums.CouponOrigin;
import com.couponnumbergenerator.enums.CouponStatus;
import com.couponnumbergenerator.enums.CouponType;
import com.couponnumbergenerator.exception.CouponNotFoundException;
import com.couponnumbergenerator.exception.DepartmentNotFoundException;
import com.couponnumbergenerator.exception.FuelTypeNotFoundException;
import com.couponnumbergenerator.exception.LocationNotFoundException;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.model.CouponBatch;
import com.couponnumbergenerator.model.CouponSequence;
import com.couponnumbergenerator.model.Department;
import com.couponnumbergenerator.model.FuelType;
import com.couponnumbergenerator.model.Location;
import com.couponnumbergenerator.repository.CouponBatchRepository;
import com.couponnumbergenerator.repository.CouponRepository;
import com.couponnumbergenerator.repository.CouponSequenceRepository;
import com.couponnumbergenerator.repository.DepartmentRepository;
import com.couponnumbergenerator.repository.FuelTypeRepository;
import com.couponnumbergenerator.repository.LocationRepository;
import com.couponnumbergenerator.service.BulkConfigService;
import com.couponnumbergenerator.service.CouponLifecycleService;
import com.couponnumbergenerator.service.CouponService;
import com.couponnumbergenerator.specification.CouponSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponServiceImpl implements CouponService {

    private static final int RANDOM_MAX = 9_999_999;
    private static final int MAX_GENERATION_ATTEMPTS = 10;
    private static final DateTimeFormatter BATCH_NUMBER_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final CouponRepository couponRepository;
    private final CouponSequenceRepository couponSequenceRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final LocationRepository locationRepository;
    private final DepartmentRepository departmentRepository;
    private final CouponBatchRepository couponBatchRepository;
    private final BulkConfigService bulkConfigService;
    private final CouponLifecycleService couponLifecycleService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public CouponResponse generateCoupon(GenerateCouponRequest request) {
        List<DenominationLine> lines = List.of(new DenominationLine(request.denomination(), 1));
        return generateCouponsInternal(
                resolveFuelType(request.fuelTypeId()), lines, request.denomination(),
                request.locationId(), request.departmentId(), request.couponType(),
                request.expiryDate(), request.performedBy())
                .getFirst();
    }

    @Override
    @Transactional
    public List<CouponResponse> generateBulkCoupons(GenerateBulkCouponRequest request) {
        request.lines().forEach(DenominationLine::validateQuantity);
        // Physical coupons are printed and bound into books of BOOK_SIZE by the vendor, so each
        // denomination line must form whole books; digital coupons are never printed and are exempt.
        if (request.couponType() != CouponType.DIGITAL) {
            for (DenominationLine line : request.lines()) {
                if (line.resolvedCount() % CouponConstants.BOOK_SIZE != 0) {
                    throw new IllegalArgumentException(
                            "Denomination line %s L × %d does not form whole books — physical coupons are printed in books of %d, so request them in books"
                                    .formatted(line.denomination().toPlainString(), line.resolvedCount(),
                                            CouponConstants.BOOK_SIZE));
                }
            }
        }

        int totalCount = request.totalCount();
        int maxCount = bulkConfigService.getMaxCount();
        if (totalCount > maxCount) {
            throw new IllegalArgumentException(
                    "Bulk count %d exceeds the configured maximum of %d".formatted(totalCount, maxCount));
        }

        BigDecimal breakdownTotal = request.breakdownTotal();
        if (request.targetQuantity() != null && breakdownTotal.compareTo(request.targetQuantity()) != 0) {
            BigDecimal diff = breakdownTotal.subtract(request.targetQuantity()).abs();
            String direction = breakdownTotal.compareTo(request.targetQuantity()) > 0 ? "over" : "under";
            throw new IllegalArgumentException(
                    "Denomination breakdown totals %s but target quantity is %s (%s by %s)"
                            .formatted(breakdownTotal, request.targetQuantity(), direction, diff));
        }

        return generateCouponsInternal(
                resolveFuelType(request.fuelTypeId()), request.lines(), breakdownTotal,
                request.locationId(), request.departmentId(), request.couponType(),
                request.expiryDate(), request.performedBy());
    }

    @Override
    @Transactional
    public CouponResponse importLegacyCoupon(ImportLegacyCouponRequest request) {
        String couponNumber = request.couponNumber().trim();
        if (couponRepository.existsByCouponNumber(couponNumber)) {
            throw new IllegalArgumentException("Coupon number '%s' already exists".formatted(couponNumber));
        }

        FuelType fuelType = resolveFuelType(request.fuelTypeId());
        Location location = resolveOriginLocation(request.locationId());
        Department department = resolveOriginDepartment(request.departmentId());
        LocalDate expiryDate = request.expiryDate() != null
                ? request.expiryDate()
                : LocalDate.now().plusDays(bulkConfigService.getDefaultValidityDays());

        Coupon coupon = couponRepository.save(Coupon.builder()
                .couponNumber(couponNumber)
                .fuelType(fuelType)
                .denomination(request.denomination())
                .status(CouponStatus.ALLOCATED)
                .currentLocation(location)
                .currentDepartment(department)
                .couponType(CouponType.PHYSICAL)
                .expiryDate(expiryDate)
                .origin(CouponOrigin.LEGACY_IMPORT)
                .build());

        couponLifecycleService.recordGeneration(List.of(coupon), request.performedBy());
        log.info("Imported legacy coupon {} directly into ALLOCATED at {} ({})",
                couponNumber, location.getCode(), department.getCode());
        return CouponResponse.from(coupon);
    }

    private static final int COL_COUPON_NUMBER = 0;
    private static final int COL_FUEL_TYPE_CODE = 1;
    private static final int COL_DENOMINATION = 2;
    private static final int COL_LOCATION_CODE = 3;
    private static final int COL_DEPARTMENT_CODE = 4;
    private static final int COL_EXPIRY_DATE = 5;

    @Override
    @Transactional
    public BulkLegacyImportResponse importLegacyCoupons(MultipartFile file, boolean dryRun, String performedBy) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload file is required");
        }

        List<LegacyImportRowResult> results = new ArrayList<>();
        List<Coupon> toSave = new ArrayList<>();
        Set<String> seenInFile = new HashSet<>();
        DataFormatter formatter = new DataFormatter();

        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (Row row : sheet) {
                if (row.getRowNum() == 0) {
                    continue; // header row
                }
                int rowNum = row.getRowNum() + 1; // 1-indexed to match what ops sees in Excel
                String couponNumber = cellText(row, COL_COUPON_NUMBER, formatter);
                if (couponNumber.isEmpty()) {
                    continue; // trailing/blank row — not an error, just nothing to import
                }

                try {
                    toSave.add(parseLegacyRow(row, formatter, couponNumber, seenInFile));
                    seenInFile.add(couponNumber.toUpperCase());
                    results.add(LegacyImportRowResult.ok(rowNum, couponNumber));
                } catch (IllegalArgumentException ex) {
                    results.add(LegacyImportRowResult.failed(rowNum, couponNumber, ex.getMessage()));
                }
            }
        } catch (IOException | RuntimeException ex) {
            throw new IllegalArgumentException(
                    "Could not read the uploaded file — is it a valid .xlsx with the expected columns "
                            + "(couponNumber, fuelTypeCode, denomination, locationCode, departmentCode, expiryDate)?",
                    ex);
        }

        // toSave only holds rows that passed validation above, so failures here can only mean a
        // genuine race (another request took the same number between our check and this save) —
        // rare enough for an admin-only bulk load that we don't need per-row savepoints for it.
        if (!dryRun && !toSave.isEmpty()) {
            List<Coupon> saved = couponRepository.saveAll(toSave);
            couponLifecycleService.recordGeneration(saved, performedBy);
        }

        log.info("Legacy coupon spreadsheet processed: {} row(s), {} succeeded, {} failed (dryRun={})",
                results.size(), toSave.size(), results.size() - toSave.size(), dryRun);
        return BulkLegacyImportResponse.of(dryRun, results);
    }

    /** Validates one spreadsheet row and builds its (unsaved) {@link Coupon} — or throws with a row-specific reason. */
    private Coupon parseLegacyRow(Row row, DataFormatter formatter, String couponNumber, Set<String> seenInFile) {
        String trimmedNumber = couponNumber.trim();
        if (trimmedNumber.length() > 20) {
            throw new IllegalArgumentException("couponNumber must be at most 20 characters");
        }
        if (seenInFile.contains(trimmedNumber.toUpperCase())) {
            throw new IllegalArgumentException("Duplicate coupon number within this file");
        }
        if (couponRepository.existsByCouponNumber(trimmedNumber)) {
            throw new IllegalArgumentException("Coupon number already exists");
        }

        FuelType fuelType = resolveFuelTypeByCode(cellText(row, COL_FUEL_TYPE_CODE, formatter));
        BigDecimal denomination = readDenomination(row, COL_DENOMINATION, formatter);
        Location location = resolveLocationByCode(cellText(row, COL_LOCATION_CODE, formatter));
        Department department = resolveDepartmentByCode(cellText(row, COL_DEPARTMENT_CODE, formatter));
        LocalDate expiryDate = readExpiryDate(row, COL_EXPIRY_DATE, formatter);

        return Coupon.builder()
                .couponNumber(trimmedNumber)
                .fuelType(fuelType)
                .denomination(denomination)
                .status(CouponStatus.ALLOCATED)
                .currentLocation(location)
                .currentDepartment(department)
                .couponType(CouponType.PHYSICAL)
                .expiryDate(expiryDate)
                .origin(CouponOrigin.LEGACY_IMPORT)
                .build();
    }

    private FuelType resolveFuelTypeByCode(String typeCode) {
        if (typeCode.isEmpty()) {
            throw new IllegalArgumentException("fuelTypeCode is required");
        }
        FuelType fuelType = fuelTypeRepository.findByTypeCode(typeCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown fuel type code '%s'".formatted(typeCode)));
        if (!fuelType.isActive()) {
            throw new IllegalArgumentException("Fuel type '%s' is inactive".formatted(fuelType.getName()));
        }
        return fuelType;
    }

    private Location resolveLocationByCode(String code) {
        if (code.isEmpty()) {
            return resolveOriginLocation(null);
        }
        return locationRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown location code '%s'".formatted(code)));
    }

    private Department resolveDepartmentByCode(String code) {
        if (code.isEmpty()) {
            return resolveOriginDepartment(null);
        }
        return departmentRepository.findByCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Unknown department code '%s'".formatted(code)));
    }

    private String cellText(Row row, int colIndex, DataFormatter formatter) {
        Cell cell = row.getCell(colIndex);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private BigDecimal readDenomination(Row row, int colIndex, DataFormatter formatter) {
        Cell cell = row.getCell(colIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            throw new IllegalArgumentException("denomination is required");
        }
        BigDecimal value;
        if (cell.getCellType() == CellType.NUMERIC) {
            value = BigDecimal.valueOf(cell.getNumericCellValue());
        } else {
            String text = formatter.formatCellValue(cell).trim();
            try {
                value = new BigDecimal(text);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("denomination '%s' is not a number".formatted(text));
            }
        }
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("denomination must be greater than zero");
        }
        return value;
    }

    private LocalDate readExpiryDate(Row row, int colIndex, DataFormatter formatter) {
        Cell cell = row.getCell(colIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return LocalDate.now().plusDays(bulkConfigService.getDefaultValidityDays());
        }
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String text = formatter.formatCellValue(cell).trim();
        if (text.isEmpty()) {
            return LocalDate.now().plusDays(bulkConfigService.getDefaultValidityDays());
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("expiryDate '%s' is not a valid yyyy-MM-dd date".formatted(text));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CouponResponse getCouponByNumber(String couponNumber) {
        return couponRepository.findByCouponNumber(couponNumber)
                .map(CouponResponse::from)
                .orElseThrow(() -> new CouponNotFoundException("Coupon not found: " + couponNumber));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CouponResponse> getCoupons(CouponFilterRequest filter, Pageable pageable) {
        return PagedResponse.from(
                couponRepository.findAll(listSpec(filter), clampPageSize(pageable)).map(CouponResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public PagedResponse<CouponResponse> getCouponsByFuelType(Long fuelTypeId, Pageable pageable) {
        CouponFilterRequest filter = new CouponFilterRequest(null, null, fuelTypeId, null);
        return PagedResponse.from(
                couponRepository.findAll(listSpec(filter), clampPageSize(pageable)).map(CouponResponse::from));
    }

    @Override
    @Transactional(readOnly = true)
    public void exportCoupons(CouponFilterRequest filter, OutputStream outputStream) throws IOException {
        Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
        writer.write("id,couponNumber,fuelType,status,couponType,location,department,batchNumber,batchSequence,expiryDate,createdAt,origin\n");

        var spec = listSpec(filter);
        var sort = Sort.by(Sort.Direction.DESC, "createdAt");
        int page = 0;
        Page<Coupon> batch;
        do {
            batch = couponRepository.findAll(spec, PageRequest.of(page++, 1000, sort));
            for (Coupon coupon : batch.getContent()) {
                writer.write(toCsvRow(coupon));
            }
            writer.flush();
        } while (!batch.isLast());
    }

    private Specification<Coupon> listSpec(CouponFilterRequest filter) {
        return CouponSpecification.withFilters(filter)
                .and(CouponSpecification.fetchResponseAssociations());
    }

    private String toCsvRow(Coupon coupon) {
        return String.join(",",
                String.valueOf(coupon.getId()),
                csvEscape(coupon.getCouponNumber()),
                csvEscape(coupon.getFuelType().getName()),
                coupon.getStatus().name(),
                coupon.getCouponType().name(),
                csvEscape(coupon.getCurrentLocation().getCode()),
                csvEscape(coupon.getCurrentDepartment().getCode()),
                csvEscape(coupon.getBatch() == null ? "" : coupon.getBatch().getBatchNumber()),
                coupon.getBatchSequence() == null ? "" : coupon.getBatchSequence().toString(),
                coupon.getExpiryDate() == null ? "" : coupon.getExpiryDate().toString(),
                coupon.getCreatedAt().toString(),
                coupon.getOrigin().name()
        ) + "\n";
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private Pageable clampPageSize(Pageable pageable) {
        int max = bulkConfigService.getMaxPageSize();
        if (pageable.getPageSize() > max) {
            return PageRequest.of(pageable.getPageNumber(), max, pageable.getSort());
        }
        return pageable;
    }

    private FuelType resolveFuelType(Long fuelTypeId) {
        FuelType fuelType = fuelTypeRepository.findById(fuelTypeId)
                .orElseThrow(() -> new FuelTypeNotFoundException(fuelTypeId));
        if (!fuelType.isActive()) {
            throw new IllegalArgumentException(
                    "Fuel type '%s' is inactive and cannot be used for coupon generation".formatted(fuelType.getName()));
        }
        return fuelType;
    }

    private Location resolveOriginLocation(Long locationId) {
        if (locationId != null) {
            return locationRepository.findById(locationId)
                    .orElseThrow(() -> new LocationNotFoundException(locationId));
        }
        return locationRepository.findByCode(CouponConstants.DEFAULT_LOCATION_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "Default location '%s' is missing — check the database seed"
                                .formatted(CouponConstants.DEFAULT_LOCATION_CODE)));
    }

    private Department resolveOriginDepartment(Long departmentId) {
        if (departmentId != null) {
            return departmentRepository.findById(departmentId)
                    .orElseThrow(() -> new DepartmentNotFoundException(departmentId));
        }
        return departmentRepository.findByCode(CouponConstants.DEFAULT_DEPARTMENT_CODE)
                .orElseThrow(() -> new IllegalStateException(
                        "Default department '%s' is missing — check the database seed"
                                .formatted(CouponConstants.DEFAULT_DEPARTMENT_CODE)));
    }

    private List<CouponResponse> generateCouponsInternal(FuelType fuelType, List<DenominationLine> lines,
                                                         BigDecimal targetQuantity, Long locationId,
                                                         Long departmentId, CouponType requestedType,
                                                         LocalDate expiryDate, String performedBy) {
        if (expiryDate == null) {
            expiryDate = LocalDate.now().plusDays(bulkConfigService.getDefaultValidityDays());
        }
        int count = lines.stream().mapToInt(DenominationLine::resolvedCount).sum();

        CouponSequence sequence = couponSequenceRepository.findByFuelTypeIdWithLock(fuelType.getId())
                .orElseGet(() -> CouponSequence.initialFor(fuelType));

        if (sequence.getIssuedCount() >= CouponConstants.MAX_COUPONS_PER_LETTER) {
            sequence.advanceLetter();
            log.info("Letter advanced to '{}' for fuel type {}", sequence.getCurrentLetter(), fuelType.getName());
        }

        Location origin = resolveOriginLocation(locationId);
        Department department = resolveOriginDepartment(departmentId);
        CouponType couponType = requestedType == null ? CouponType.PHYSICAL : requestedType;
        // Physical coupons await printing/receipt; digital coupons have nothing to receive.
        CouponStatus initialStatus = couponType == CouponType.PHYSICAL
                ? CouponStatus.GENERATED
                : CouponStatus.IN_STOCK;

        CouponBatch batch = couponBatchRepository.save(CouponBatch.builder()
                .batchNumber(buildBatchNumber(fuelType))
                .sequenceNumber(sequence.nextBatchSequenceNumber())
                .fuelType(fuelType)
                .couponType(couponType)
                .quantity(count)
                .targetQuantity(targetQuantity)
                .originLocation(origin)
                .expiryDate(expiryDate)
                .createdBy(performedBy)
                .build());

        Set<String> generatedInBatch = new HashSet<>(count);
        List<Coupon> coupons = new ArrayList<>(count);
        int position = 0;
        int book = 0;
        Set<String> codesInBatch = new HashSet<>();
        for (DenominationLine line : lines) {
            int lineCount = line.resolvedCount();
            // The print vendor binds every BOOK_SIZE consecutive CSV rows of a denomination into a
            // book, so book numbers just follow generation order. A line that doesn't form whole
            // books (single-coupon batches, digital coupons) gets none.
            boolean wholeBooks = couponType != CouponType.DIGITAL
                    && lineCount % CouponConstants.BOOK_SIZE == 0;
            for (int i = 0; i < lineCount; i++) {
                if (wholeBooks && i % CouponConstants.BOOK_SIZE == 0) {
                    book++;
                }
                String couponNumber = generateUniqueCouponNumber(fuelType, sequence, generatedInBatch);
                generatedInBatch.add(couponNumber);
                coupons.add(Coupon.builder()
                        .couponNumber(couponNumber)
                        .fuelType(fuelType)
                        .denomination(line.denomination())
                        .status(initialStatus)
                        .batch(batch)
                        .currentLocation(origin)
                        .currentDepartment(department)
                        .couponType(couponType)
                        .expiryDate(expiryDate)
                        .batchSequence(++position)
                        .bookNumber(wholeBooks ? book : null)
                        .redemptionCode(couponType == CouponType.DIGITAL ? newRedemptionCode(codesInBatch) : null)
                        .build());
            }
        }

        sequence.setIssuedCount(sequence.getIssuedCount() + count);
        couponSequenceRepository.saveAndFlush(sequence);

        List<Coupon> saved = couponRepository.saveAll(coupons);
        couponLifecycleService.recordGeneration(saved, performedBy);
        log.info("Generated {} {} coupon(s) for fuel type {} in batch {} at {} under letter '{}'",
                count, couponType, fuelType.getName(), batch.getBatchNumber(), origin.getCode(),
                sequence.getCurrentLetter());
        return saved.stream().map(CouponResponse::from).toList();
    }

    // ponytail: no DB pre-check — a clash with an existing code (≈ live codes / 27.5 billion per
    // coupon) fails the unique index and the caller retries; add an existsBy loop if volumes make that visible.
    private String newRedemptionCode(Set<String> codesInBatch) {
        String code;
        do {
            code = RedemptionCodes.generate(secureRandom);
        } while (!codesInBatch.add(code));
        return code;
    }

    private String buildBatchNumber(FuelType fuelType) {
        return "BAT-" + BATCH_NUMBER_TIMESTAMP.format(LocalDateTime.now()) + "-" + fuelType.getTypeCode();
    }

    private String generateUniqueCouponNumber(FuelType fuelType, CouponSequence sequence, Set<String> alreadyInBatch) {
        Optional<String> result = tryGenerateCouponNumber(fuelType, sequence.getCurrentLetter(), alreadyInBatch);
        if (result.isPresent()) {
            return result.get();
        }
        log.warn("All {} attempts exhausted for letter '{}', fuel type {} — advancing letter early",
                MAX_GENERATION_ATTEMPTS, sequence.getCurrentLetter(), fuelType.getName());
        sequence.advanceLetter();
        return tryGenerateCouponNumber(fuelType, sequence.getCurrentLetter(), alreadyInBatch)
                .orElseThrow(() -> new IllegalStateException(
                        "Failed to generate a unique coupon number for fuel type %s even after advancing letter"
                                .formatted(fuelType.getName())));
    }

    private Optional<String> tryGenerateCouponNumber(FuelType fuelType, char letter, Set<String> alreadyInBatch) {
        for (int attempt = 1; attempt <= MAX_GENERATION_ATTEMPTS; attempt++) {
            int randomValue = secureRandom.nextInt(RANDOM_MAX + 1);
            String couponNumber = buildCouponNumber(fuelType, letter, randomValue);
            if (!alreadyInBatch.contains(couponNumber)
                    && !couponRepository.existsByCouponNumber(couponNumber)) {
                return Optional.of(couponNumber);
            }
            log.warn("Coupon collision on attempt {} (fuel={}, letter={})", attempt, fuelType.getName(), letter);
        }
        return Optional.empty();
    }

    private String buildCouponNumber(FuelType fuelType, char letter, int randomValue) {
        String paddedValue = String.format("%0" + CouponConstants.SEQUENCE_PADDING_LENGTH + "d", randomValue);
        return CouponConstants.COUPON_PREFIX + fuelType.getTypeCode() + letter + paddedValue;
    }
}
package com.couponnumbergenerator.constants;

public final class CouponConstants {

    private CouponConstants() {}

    public static final String COUPON_PREFIX = "PU";
    public static final char LETTER_START = 'M';
    public static final int SEQUENCE_PADDING_LENGTH = 7;
    public static final long MAX_COUPONS_PER_LETTER = 10_000_000L;

    public static final String API_BASE_PATH = "/api/v1";
    public static final String COUPONS_PATH = "/coupons";
    public static final String INSIGHTS_PATH = "/insights";
    public static final String AI_PATH = "/ai";
    public static final String LOCATIONS_PATH = "/locations";
    public static final String BATCHES_PATH = "/batches";
    public static final String INVENTORY_PATH = "/inventory";
    public static final String DEPARTMENTS_PATH = "/departments";
    public static final String APPROVALS_PATH = "/approvals";
    public static final String REQUISITIONS_PATH = "/requisitions";
    public static final String REDEMPTIONS_PATH = "/redemptions";

    public static final String DEFAULT_LOCATION_CODE = "HQ";
    public static final String DEFAULT_DEPARTMENT_CODE = "STOCKS";
}
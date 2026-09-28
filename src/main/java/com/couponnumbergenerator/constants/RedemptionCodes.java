package com.couponnumbergenerator.constants;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * The 7-character redemption code on a virtual coupon. The alphabet leaves out look-alikes
 * (0/O, 1/I/L) so a code read off a phone screen is typed right: 31^7 ≈ 27.5 billion codes.
 */
public final class RedemptionCodes {

    public static final int LENGTH = 7;
    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private RedemptionCodes() {}

    public static String generate(SecureRandom random) {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /** Uppercases and drops spaces/hyphens, so "k7m 4qx-2" matches "K7M4QX2". */
    public static String normalize(String input) {
        return input.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}

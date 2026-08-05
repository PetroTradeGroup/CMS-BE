package com.couponnumbergenerator.service;

import com.couponnumbergenerator.model.Coupon;

public interface QrCodeService {

    /**
     * Pipe-delimited coupon data (couponNumber, fuelType, denomination, expiryDate, batchNumber,
     * batchSequence) followed by an HMAC-SHA256 signature over that data — verifiable offline by
     * anyone holding the shared secret, without a round trip to this service.
     */
    String buildSignedPayload(Coupon coupon);

    /** Renders arbitrary text content as a PNG-encoded QR code image. */
    byte[] renderPng(String content);

    /**
     * Verifies a scanned {@link #buildSignedPayload(Coupon)} payload's HMAC signature and
     * returns the coupon number it encodes. Throws {@link com.couponnumbergenerator.exception.InvalidQrSignatureException}
     * if the signature doesn't match — a forged, corrupted, or otherwise tampered scan.
     */
    String decodeAndVerify(String payload);
}
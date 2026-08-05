package com.couponnumbergenerator.service.impl;

import com.couponnumbergenerator.exception.InvalidQrSignatureException;
import com.couponnumbergenerator.model.Coupon;
import com.couponnumbergenerator.qr.QrProperties;
import com.couponnumbergenerator.service.QrCodeService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class QrCodeServiceImpl implements QrCodeService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int QR_SIZE_PX = 300;

    private final QrProperties qrProperties;

    @Override
    public String buildSignedPayload(Coupon coupon) {
        String data = canonicalData(coupon);
        return data + "|" + sign(data);
    }

    @Override
    public String decodeAndVerify(String payload) {
        int splitIndex = payload == null ? -1 : payload.lastIndexOf('|');
        if (splitIndex < 0) {
            throw new InvalidQrSignatureException("Malformed QR payload");
        }
        String data = payload.substring(0, splitIndex);
        String claimedSignature = payload.substring(splitIndex + 1);
        if (!MessageDigest.isEqual(
                sign(data).getBytes(StandardCharsets.UTF_8), claimedSignature.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidQrSignatureException("QR signature invalid — possible tampering");
        }
        return data.split("\\|", 2)[0];
    }

    @Override
    public byte[] renderPng(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 1));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | IOException e) {
            throw new IllegalStateException("Failed to render QR code", e);
        }
    }

    private String canonicalData(Coupon coupon) {
        return String.join("|",
                coupon.getCouponNumber(),
                coupon.getFuelType().getName(),
                coupon.getDenomination().toPlainString(),
                coupon.getExpiryDate() == null ? "" : coupon.getExpiryDate().toString(),
                coupon.getBatch() == null ? "" : coupon.getBatch().getBatchNumber(),
                coupon.getBatchSequence() == null ? "" : coupon.getBatchSequence().toString());
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(qrProperties.secretKey().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to sign QR payload", e);
        }
    }
}
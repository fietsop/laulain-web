package com.laulain.rentals.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * S3Service stub — AWS S3 disabled for initial deployment.
 * All methods are no-ops that return placeholder values.
 * Re-enable full implementation when AWS S3 is configured.
 */
@Service
@Slf4j
public class S3Service {

    public String uploadItemImage(MultipartFile file, UUID itemId) {
        log.info("[S3 STUB] uploadItemImage called for item {} — S3 disabled", itemId);
        return "items/" + itemId + "/placeholder.jpg";
    }

    public String uploadPdf(byte[] pdfBytes, String folder, String filename) {
        log.info("[S3 STUB] uploadPdf called for {}/{} — S3 disabled", folder, filename);
        return folder + "/" + filename;
    }

    public String generatePresignedUrl(String s3Key) {
        log.debug("[S3 STUB] generatePresignedUrl called for {} — S3 disabled", s3Key);
        return "#";
    }

    public String generatePublicUrl(String s3Key) {
        log.debug("[S3 STUB] generatePublicUrl called for {} — S3 disabled", s3Key);
        return "#";
    }

    public void deleteFile(String s3Key) {
        log.info("[S3 STUB] deleteFile called for {} — S3 disabled", s3Key);
    }
}

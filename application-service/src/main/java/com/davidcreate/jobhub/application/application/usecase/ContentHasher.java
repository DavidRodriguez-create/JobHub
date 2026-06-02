package com.davidcreate.jobhub.application.application.usecase;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class ContentHasher {

    private ContentHasher() {
    }

    static String hash(String title, String company, String location, String description) {
        String raw = norm(title) + "|" + norm(company) + "|" + norm(location) + "|" + norm(description);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
}

package com.davidcreate.jobhub.auth.application.usecase;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates single-use alphanumeric backup (recovery) codes for 2FA setup
 * and regeneration (ADR 0012). Eight codes, eight characters each.
 */
final class BackupCodeGenerator {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final int CODE_COUNT = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private BackupCodeGenerator() {
    }

    static List<String> generate() {
        List<String> codes = new ArrayList<>(CODE_COUNT);
        for (int i = 0; i < CODE_COUNT; i++) {
            codes.add(generateOne());
        }
        return codes;
    }

    private static String generateOne() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}

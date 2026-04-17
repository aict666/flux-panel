package com.admin.common.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public final class AgentKeyUtil {

    private static final String CHARSET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int PREFIX_LENGTH = 12;
    private static final int SECRET_LENGTH = 32;

    private AgentKeyUtil() {
    }

    public static String generatePlaintextKey() {
        return "fpak_" + randomString(PREFIX_LENGTH) + "_" + randomString(SECRET_LENGTH);
    }

    public static String extractPrefix(String rawKey) {
        String[] parts = rawKey.split("_");
        if (parts.length != 3 || !"fpak".equals(parts[0])) {
            throw new IllegalArgumentException("非法 Agent Key 格式");
        }
        return parts[1];
    }

    public static String hashKey(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static String randomString(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            builder.append(CHARSET.charAt(SECURE_RANDOM.nextInt(CHARSET.length())));
        }
        return builder.toString();
    }
}

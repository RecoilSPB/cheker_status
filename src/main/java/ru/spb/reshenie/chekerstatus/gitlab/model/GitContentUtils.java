package ru.spb.reshenie.chekerstatus.gitlab.model;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class GitContentUtils {

    private GitContentUtils() {
    }

    public static String sha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content == null ? new byte[0] : content);
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String item = Integer.toHexString(0xff & b);
                if (item.length() == 1) {
                    hex.append('0');
                }
                hex.append(item);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static boolean looksBinary(byte[] content) {
        if (content == null || content.length == 0) {
            return false;
        }

        int suspicious = 0;
        int checked = Math.min(content.length, 8192);
        for (int i = 0; i < checked; i++) {
            int value = content[i] & 0xff;
            if (value == 0) {
                return true;
            }
            if (value < 32 && value != '\n' && value != '\r' && value != '\t' && value != '\f') {
                suspicious++;
            }
        }
        return suspicious > checked * 0.30;
    }
}

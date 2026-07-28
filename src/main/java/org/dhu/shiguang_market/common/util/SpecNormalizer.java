package org.dhu.shiguang_market.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class SpecNormalizer {
    private SpecNormalizer() {
    }

    public static Map<String, String> normalize(Map<String, String> spec) {
        if (spec == null || spec.isEmpty()) {
            throw new IllegalArgumentException("spec must not be empty");
        }
        if (spec.size() > 10) {
            throw new IllegalArgumentException("spec must contain at most 10 entries");
        }
        Map<String, String> sorted = new TreeMap<>();
        spec.forEach((key, value) -> {
            String normalizedKey = normalizePart(key);
            String normalizedValue = normalizePart(value);
            if (sorted.put(normalizedKey, normalizedValue) != null) {
                throw new IllegalArgumentException("duplicated normalized spec key");
            }
        });
        return new LinkedHashMap<>(sorted);
    }

    public static String key(Map<String, String> normalized) {
        String canonical = normalized.entrySet().stream()
                .map(entry -> escape(entry.getKey()) + "=" + escape(entry.getValue()))
                .collect(Collectors.joining("&"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static String normalizePart(String value) {
        if (value == null) {
            throw new IllegalArgumentException("spec key and value must not be null");
        }
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFC);
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new IllegalArgumentException("spec key and value length must be 1..64");
        }
        return normalized;
    }

    private static String escape(String value) {
        return value.replace("%", "%25").replace("&", "%26").replace("=", "%3D");
    }
}

package com.umik.tiktoksparkflow.utils;

public final class TextNormalizer {
    private TextNormalizer() {}

    public static String normalize(String value) {
        return value == null ? "" : value.replace('\u200B', ' ').trim();
    }
}

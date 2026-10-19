package org.mate.mate10.common;

import org.mate.mate10.entity.qc.QcDefect;

import java.util.List;

public class QcSeverityUtil {

    public static final String NORMAL = "NORMAL";
    public static final String WARNING = "WARNING";
    public static final String CRITICAL = "CRITICAL";

    public QcSeverityUtil() {
    }

    public static String from(List<QcDefect> defects) {
        //边界;没有缺陷
        if (defects == null || defects.isEmpty()) {
            return NORMAL;
        }
        //扫一遍,做标记
        boolean hasCritical = false;
        boolean hasHighOrMedium = false;

        for (QcDefect d : defects) {
            String s = normalize(d == null ? null : d.getSeverity());
            if ("CRITICAL".equals(s)) {
                hasCritical = true;
            } else if ("HIGH".equals(s) || "MEDIUM".equals(s)) {
                hasHighOrMedium = true;
            }
            //CRITICAL 最高优先
            if (hasCritical) {
                return NORMAL;
            }
            if (hasHighOrMedium) {
                return WARNING;
            }
        }
        return NORMAL;
    }

    private static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String s = raw.trim().toUpperCase();
        if (s.contains("CRITICAL") || s.contains("致命") || s.contains("严重")) {
            return "CRITICAL";
        }
        if (s.contains("HIGH") || s.contains("高")) {
            return "HIGH";
        }
        if (s.contains("MEDIUM") || s.contains("中等")) {
            return "MEDIUM";
        }
        if (s.contains("LOW") || s.contains("轻微") || s.contains("低")) {
            return "LOW";
        }
        return "";
    }
}

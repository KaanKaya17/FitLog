package com.example.fitlog.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Saat dilimine duyarlı tarih yardımcıları.
 *
 * Veritabanındaki zaman damgaları UTC'dir. "Bugün" kullanıcının yerel gününü
 * ifade eder; bu yüzden yerel gün sınırları UTC'ye çevrilerek sorgulanır.
 * (Eski kodda yerel tarih "T00:00:00Z" ile birleştiriliyordu; UTC+3'te gece
 * 00:00-03:00 arası kayıtlar yanlış güne düşüyordu.)
 */
public final class DateRanges {

    private static final DateTimeFormatter UTC_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private DateRanges() {}

    /** Yerel günün başlangıcını UTC ISO-8601 olarak döndürür. */
    public static String startOfDayUtc(LocalDate day, ZoneId zone) {
        return UTC_FORMAT.format(day.atStartOfDay(zone).toInstant());
    }

    /**
     * [from, toInclusive] yerel gün aralığı için PostgREST filtreleri:
     * "gte.<başlangıç>" ve "lt.<bitişten sonraki günün başı>".
     */
    public static List<String> rangeFilter(LocalDate from, LocalDate toInclusive, ZoneId zone) {
        List<String> f = new ArrayList<>(2);
        f.add("gte." + startOfDayUtc(from, zone));
        f.add("lt." + startOfDayUtc(toInclusive.plusDays(1), zone));
        return f;
    }

    public static List<String> dayFilter(LocalDate day, ZoneId zone) {
        return rangeFilter(day, day, zone);
    }

    /** Geçmiş bir güne eklenen kayıt için o yerel günün öğle saati (UTC). */
    public static String localNoonUtc(LocalDate day, ZoneId zone) {
        return UTC_FORMAT.format(day.atTime(LocalTime.NOON).atZone(zone).toInstant());
    }

    public static String nowUtc() {
        return UTC_FORMAT.format(Instant.now());
    }

    /**
     * Supabase'in döndürdüğü timestamptz değerini (ör. "2026-11-19T21:30:00.123+00:00")
     * yerel tarihe çevirir. Ayrıştırılamazsa null döner.
     */
    public static LocalDate toLocalDate(String timestamp, ZoneId zone) {
        if (timestamp == null || timestamp.isEmpty()) return null;
        try {
            return OffsetDateTime.parse(normalize(timestamp)).atZoneSameInstant(zone).toLocalDate();
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(timestamp.substring(0, Math.min(10, timestamp.length())));
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    public static Instant toInstant(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return null;
        try {
            return OffsetDateTime.parse(normalize(timestamp)).toInstant();
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** "+00" gibi kısa ofsetleri ve boşluk ayırıcıyı ISO biçimine getirir. */
    static String normalize(String ts) {
        String s = ts.trim().replace(' ', 'T');
        if (s.matches(".*[+-]\\d{2}$")) s = s + ":00";
        return s;
    }
}

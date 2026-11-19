package com.example.fitlog.domain;

import com.example.fitlog.MealLog;
import com.example.fitlog.WaterLog;
import com.example.fitlog.WeightLog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Kullanıcı verisini CSV olarak dışa aktarır (RFC 4180, UTF-8 BOM'lu: Excel Türkçe karakterleri doğru açar). */
public final class CsvExporter {

    private static final String BOM = String.valueOf((char) 0xFEFF);
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private CsvExporter() {}

    public static String meals(List<MealLog> meals, ZoneId zone) {
        StringBuilder sb = new StringBuilder(BOM)
                .append("date,meal_type,food,serving,calories,protein_g,carbs_g,fat_g,fiber_g\r\n");
        for (MealLog m : meals) {
            row(sb, local(m.getEaten_at(), zone), m.getMeal_type(), m.getFood_name(), m.getServing_size(),
                    String.valueOf(m.getCalories()), num(m.getProtein()), num(m.getCarbohydrates()),
                    num(m.getFat()), num(m.getFiber()));
        }
        return sb.toString();
    }

    public static String water(List<WaterLog> water, ZoneId zone) {
        StringBuilder sb = new StringBuilder(BOM).append("date,amount_ml\r\n");
        for (WaterLog w : water) row(sb, local(w.getConsumed_at(), zone), String.valueOf(w.getAmount_ml()));
        return sb.toString();
    }

    public static String weights(List<WeightLog> weights, ZoneId zone) {
        StringBuilder sb = new StringBuilder(BOM).append("date,weight_kg\r\n");
        for (WeightLog w : weights) row(sb, local(w.getLogged_at(), zone), num(w.getWeight_kg()));
        return sb.toString();
    }

    /** Üç CSV'yi tek ZIP'te toplar. */
    public static byte[] zip(String mealsCsv, String waterCsv, String weightCsv) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            put(zos, "meals.csv", mealsCsv);
            put(zos, "water.csv", waterCsv);
            put(zos, "weight.csv", weightCsv);
        }
        return bos.toByteArray();
    }

    private static void put(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    static void row(StringBuilder sb, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cells[i]));
        }
        sb.append("\r\n");
    }

    /**
     * RFC 4180 kaçışı + CSV/formül enjeksiyonu koruması: =, +, -, @ ile başlayan
     * metinler tablo programlarında formül olarak çalışmasın diye ' ile başlatılır.
     */
    static String escape(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !s.matches("-?\\d+(\\.\\d+)?")) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    private static String num(float f) {
        return String.format(Locale.ROOT, "%.1f", f);
    }

    private static String local(String ts, ZoneId zone) {
        Instant i = DateRanges.toInstant(ts);
        return i == null ? (ts != null ? ts : "") : LOCAL.format(i.atZone(zone));
    }
}

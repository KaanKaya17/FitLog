package com.example.fitlog.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.fitlog.WaterLog;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.util.Collections;
import java.util.zip.ZipInputStream;

public class CsvExporterTest {

    @Test
    public void escape_quotesCommasAndNewlines() {
        assertEquals("plain", CsvExporter.escape("plain"));
        assertEquals("\"a,b\"", CsvExporter.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvExporter.escape("say \"hi\""));
        assertEquals("\"line1\nline2\"", CsvExporter.escape("line1\nline2"));
        assertEquals("", CsvExporter.escape(null));
    }

    @Test
    public void escape_neutralizesFormulaInjection() {
        // Önce ' eklenir, sonra tırnak nedeniyle hücre tırnaklanır.
        assertEquals("\"'=HYPERLINK(\"\"x\"\")\"", CsvExporter.escape("=HYPERLINK(\"x\")"));
        assertEquals("'+cmd", CsvExporter.escape("+cmd"));
        assertEquals("'@SUM(A1)", CsvExporter.escape("@SUM(A1)"));
        assertEquals("-12.5", CsvExporter.escape("-12.5")); // negatif sayı dokunulmaz
    }

    @Test
    public void water_usesLocalTimeAndBom() {
        WaterLog w = new WaterLog(1, 250);
        w.setConsumed_at("2026-11-18T22:15:00+00:00");
        String csv = CsvExporter.water(Collections.singletonList(w), ZoneId.of("Europe/Istanbul"));
        assertTrue(csv.startsWith((char) 0xFEFF + "date,amount_ml\r\n"));
        assertTrue(csv.contains("2026-11-19 01:15,250\r\n"));
    }

    @Test
    public void zip_containsThreeFiles() throws Exception {
        byte[] z = CsvExporter.zip("m", "w", "k");
        int count = 0;
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(z))) {
            while (in.getNextEntry() != null) count++;
        }
        assertEquals(3, count);
    }
}

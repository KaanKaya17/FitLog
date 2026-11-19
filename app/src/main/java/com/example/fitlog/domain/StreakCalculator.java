package com.example.fitlog.domain;

import java.time.LocalDate;
import java.util.Set;

/** Ardışık gün serisi hesapları. */
public final class StreakCalculator {

    private StreakCalculator() {}

    /**
     * Bugünle biten güncel seri. Bugün henüz tamamlanmadıysa seri bozulmuş
     * sayılmaz; dünden geriye doğru sayılır.
     */
    public static int currentStreak(Set<LocalDate> doneDays, LocalDate today) {
        LocalDate d = doneDays.contains(today) ? today : today.minusDays(1);
        int count = 0;
        while (doneDays.contains(d)) {
            count++;
            d = d.minusDays(1);
        }
        return count;
    }

    /** Verilen günler içindeki en uzun seri. */
    public static int bestStreak(Set<LocalDate> doneDays) {
        int best = 0;
        for (LocalDate d : doneDays) {
            if (doneDays.contains(d.minusDays(1))) continue; // serinin başı değil
            int len = 0;
            LocalDate cur = d;
            while (doneDays.contains(cur)) {
                len++;
                cur = cur.plusDays(1);
            }
            best = Math.max(best, len);
        }
        return best;
    }
}

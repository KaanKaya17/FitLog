package com.example.fitlog.ui;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.fitlog.data.FitLogRepository;
import com.example.fitlog.domain.CsvExporter;

import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;

/** Kullanıcının son 1 yıllık verisini ZIP (CSV) olarak hazırlar. */
public class ExportViewModel extends BaseViewModel {

    static final int DAYS = 365;

    private final MutableLiveData<Resource<Uri>> export = new MutableLiveData<>();

    public ExportViewModel(@NonNull Application app) { super(app); }

    public LiveData<Resource<Uri>> export() { return export; }

    public void start() {
        Application app = getApplication();
        load(export, () -> {
            ZoneId zone = ZoneId.systemDefault();
            LocalDate today = LocalDate.now(zone);
            FitLogRepository.RangeData d = repo.getRange(today.minusDays(DAYS - 1), today);
            byte[] zip = CsvExporter.zip(
                    CsvExporter.meals(d.meals, zone),
                    CsvExporter.water(d.water, zone),
                    CsvExporter.weights(repo.getWeights(1000), zone));

            File dir = new File(app.getCacheDir(), "export");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("export dir");
            // Eski dışa aktarımları temizle (cihazda kişisel veri biriktirme).
            File[] old = dir.listFiles();
            if (old != null) for (File f : old) //noinspection ResultOfMethodCallIgnored
                f.delete();
            File out = new File(dir, "fitlog-export-" + today + ".zip");
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(zip);
            }
            return FileProvider.getUriForFile(app, app.getPackageName() + ".fileprovider", out);
        });
    }
}

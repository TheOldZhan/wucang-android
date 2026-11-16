package com.wucang.inventory.io;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Temporary CSV files granted to a chosen Android share destination. */
public final class ExportFiles {
    private ExportFiles() { }

    public static File writeCsv(Context context, String csv) throws IOException {
        if (csv == null) throw new IOException("库存清单为空");
        File directory = new File(context.getCacheDir(), "exports");
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("无法创建导出目录");
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        File file = File.createTempFile("Wucang_Inventory_" + timestamp + "_", ".csv", directory);
        boolean completed = false;
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(csv.getBytes(StandardCharsets.UTF_8));
            stream.flush();
            stream.getFD().sync();
            completed = true;
        } finally {
            if (!completed) file.delete();
        }
        return file;
    }

    public static Uri uri(Context context, File file) {
        try {
            File expectedDirectory = new File(context.getCacheDir(), "exports").getCanonicalFile();
            if (file == null || !expectedDirectory.equals(file.getCanonicalFile().getParentFile())) {
                throw new IllegalArgumentException("导出文件路径无效");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法解析导出文件路径", exception);
        }
        return FileProvider.getUriForFile(context, context.getPackageName() + ".files", file);
    }
}

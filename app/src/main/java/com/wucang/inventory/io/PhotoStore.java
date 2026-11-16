package com.wucang.inventory.io;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/** Owns local, normalized item photos. Call importPhoto/loadThumbnail off the UI thread. */
public final class PhotoStore {
    private static final int PHOTO_EDGE = 1600;
    private static final long MAX_SOURCE_BYTES = 64L * 1024 * 1024;
    private final Context context;
    private final File photosDirectory;
    private final File cameraDirectory;

    public PhotoStore(Context context) {
        this.context = context.getApplicationContext();
        photosDirectory = new File(this.context.getFilesDir(), "photos");
        cameraDirectory = new File(this.context.getCacheDir(), "camera");
    }

    public File newCameraFile() throws IOException {
        ensureDirectory(cameraDirectory);
        return File.createTempFile("capture-", ".jpg", cameraDirectory);
    }

    public Uri cameraUri(File file) {
        if (!isFileWithin(file, cameraDirectory)) {
            throw new IllegalArgumentException("拍照文件路径无效");
        }
        return FileProvider.getUriForFile(context, context.getPackageName() + ".files", file);
    }

    /** Copies an external image into app storage and returns its permanent absolute path. */
    public String importPhoto(Uri uri) throws IOException {
        if (uri == null) throw new IOException("没有选择照片");
        ensureDirectory(photosDirectory);
        File importDirectory = new File(context.getCacheDir(), "photo-imports");
        ensureDirectory(importDirectory);
        File source = File.createTempFile("import-", ".source", importDirectory);
        File output = null;
        Bitmap decoded = null;
        Bitmap normalized = null;
        Bitmap scaled = null;
        boolean complete = false;
        try {
            copySource(uri, source);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(source.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0
                    || bounds.outWidth > 100000 || bounds.outHeight > 100000) {
                throw new IOException("无法读取此照片，请选择 JPEG、PNG 或设备支持的图片");
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, PHOTO_EDGE);
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            decoded = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
            if (decoded == null) throw new IOException("照片解码失败");

            int orientation = ExifInterface.ORIENTATION_NORMAL;
            try {
                ExifInterface exif = new ExifInterface(source.getAbsolutePath());
                orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
            } catch (IOException ignored) {
                // Some valid image formats carry no EXIF metadata.
            }
            Matrix transform = orientationMatrix(orientation);
            normalized = transform.isIdentity() ? decoded : Bitmap.createBitmap(decoded, 0, 0,
                    decoded.getWidth(), decoded.getHeight(), transform, true);

            int longest = Math.max(normalized.getWidth(), normalized.getHeight());
            if (longest > PHOTO_EDGE) {
                float scale = PHOTO_EDGE / (float) longest;
                scaled = Bitmap.createScaledBitmap(normalized,
                        Math.max(1, Math.round(normalized.getWidth() * scale)),
                        Math.max(1, Math.round(normalized.getHeight() * scale)), true);
            } else {
                scaled = normalized;
            }

            output = File.createTempFile(".import-", ".jpg", photosDirectory);
            try (FileOutputStream stream = new FileOutputStream(output)) {
                if (!scaled.compress(Bitmap.CompressFormat.JPEG, 85, stream)) {
                    throw new IOException("照片保存失败");
                }
                stream.flush();
                stream.getFD().sync();
            }
            File destination = new File(photosDirectory, UUID.randomUUID() + ".jpg");
            if (!output.renameTo(destination)) throw new IOException("无法保存照片文件");
            complete = true;
            return destination.getAbsolutePath();
        } catch (SecurityException exception) {
            throw new IOException("无法访问这张照片，请重新选择", exception);
        } catch (IllegalArgumentException exception) {
            throw new IOException("无法读取这张照片，请重新选择", exception);
        } catch (OutOfMemoryError error) {
            throw new IOException("照片过大，暂时无法处理，请选择较小的图片", error);
        } finally {
            if (scaled != null && scaled != normalized && scaled != decoded) scaled.recycle();
            if (normalized != null && normalized != decoded) normalized.recycle();
            if (decoded != null) decoded.recycle();
            source.delete();
            if (!complete && output != null) output.delete();
        }
    }

    /** Returns a bounded bitmap or null for an absent/invalid photo. The caller owns the bitmap. */
    public Bitmap loadThumbnail(String absolutePath, int size) {
        if (absolutePath == null || absolutePath.isEmpty()) return null;
        File file = new File(absolutePath);
        if (!isFileWithin(file, photosDirectory) || !file.isFile()) return null;
        int edge = Math.max(32, Math.min(size, 1600));
        Bitmap decoded = null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, edge);
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            decoded = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            if (decoded == null) return null;
            int longest = Math.max(decoded.getWidth(), decoded.getHeight());
            if (longest <= edge) return decoded;
            float ratio = edge / (float) longest;
            Bitmap thumbnail = Bitmap.createScaledBitmap(decoded,
                    Math.max(1, Math.round(decoded.getWidth() * ratio)),
                    Math.max(1, Math.round(decoded.getHeight() * ratio)), true);
            if (thumbnail != decoded) decoded.recycle();
            return thumbnail;
        } catch (RuntimeException | OutOfMemoryError error) {
            if (decoded != null && !decoded.isRecycled()) decoded.recycle();
            return null;
        }
    }

    /** Deletes only photos owned by this app; never follows arbitrary stored paths. */
    public void deletePhoto(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) return;
        File file = new File(absolutePath);
        if (isFileWithin(file, photosDirectory) && file.isFile()) file.delete();
    }

    public void cleanupCameraFile(File file) {
        if (file != null && isFileWithin(file, cameraDirectory) && file.isFile()) file.delete();
    }

    private void copySource(Uri uri, File destination) throws IOException {
        try (InputStream input = context.getContentResolver().openInputStream(uri);
             FileOutputStream output = new FileOutputStream(destination)) {
            if (input == null) throw new IOException("无法打开所选照片");
            byte[] buffer = new byte[32 * 1024];
            long total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_SOURCE_BYTES) throw new IOException("照片不能超过 64 MB");
                output.write(buffer, 0, count);
            }
            if (total == 0) throw new IOException("照片文件为空，请重新拍摄或选择");
        }
    }

    private static int sampleSize(int width, int height, int edge) {
        int sample = 1;
        while (Math.max(width, height) / (sample * 2) >= edge) sample *= 2;
        return sample;
    }

    private static Matrix orientationMatrix(int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                matrix.setScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.setRotate(180);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                matrix.setScale(1, -1);
                break;
            case ExifInterface.ORIENTATION_TRANSPOSE:
                matrix.setRotate(90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.setRotate(90);
                break;
            case ExifInterface.ORIENTATION_TRANSVERSE:
                matrix.setRotate(-90);
                matrix.postScale(-1, 1);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.setRotate(-90);
                break;
            default:
                break;
        }
        return matrix;
    }

    private static boolean isFileWithin(File file, File directory) {
        try {
            return file.getCanonicalFile().getParentFile().equals(directory.getCanonicalFile());
        } catch (IOException | NullPointerException ignored) {
            return false;
        }
    }

    private static void ensureDirectory(File directory) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("无法创建本地照片目录，请检查可用空间");
        }
    }
}

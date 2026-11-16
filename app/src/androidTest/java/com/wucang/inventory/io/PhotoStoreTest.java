package com.wucang.inventory.io;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.media.ExifInterface;
import android.net.Uri;
import android.test.AndroidTestCase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class PhotoStoreTest extends AndroidTestCase {
    private PhotoStore store;

    @Override protected void setUp() throws Exception {
        super.setUp();
        store = new PhotoStore(getContext());
    }

    public void testImportsAndNormalizesRotationAndSize() throws Exception {
        File source = File.createTempFile("orientation-test-", ".jpg", getContext().getCacheDir());
        String imported = null;
        try {
            writeTestImage(source, 2400, 800);
            ExifInterface exif = new ExifInterface(source.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION,
                    Integer.toString(ExifInterface.ORIENTATION_ROTATE_90));
            exif.saveAttributes();

            imported = store.importPhoto(Uri.fromFile(source));
            assertTrue("Photo stored inside app private files", imported.startsWith(
                    new File(getContext().getFilesDir(), "photos").getAbsolutePath() + File.separator));
            assertTrue(source.delete());
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(imported, bounds);
            assertEquals(1600, bounds.outHeight);
            assertTrue(bounds.outWidth >= 532 && bounds.outWidth <= 534);
            Bitmap thumbnail = store.loadThumbnail(imported, 120);
            assertNotNull(thumbnail);
            assertEquals(120, thumbnail.getHeight());
            assertTrue(thumbnail.getWidth() < thumbnail.getHeight());
            thumbnail.recycle();
        } finally {
            source.delete();
            store.deletePhoto(imported);
        }
    }

    public void testInvalidImageFailsWithoutCreatingPhoto() throws Exception {
        File source = File.createTempFile("invalid-test-", ".jpg", getContext().getCacheDir());
        File directory = new File(getContext().getFilesDir(), "photos");
        int before = childCount(directory);
        try {
            Files.write(source.toPath(), "not an image".getBytes(StandardCharsets.UTF_8));
            try {
                store.importPhoto(Uri.fromFile(source));
                fail("Invalid image must not be accepted");
            } catch (IOException expected) {
                assertNotNull(expected.getMessage());
            }
            assertEquals(before, childCount(directory));
            assertNull(store.loadThumbnail(source.getAbsolutePath(), 80));
        } finally {
            source.delete();
        }
    }

    public void testDeleteAndCameraUriRejectUnownedPaths() throws Exception {
        File outside = File.createTempFile("keep-test-", ".jpg", getContext().getFilesDir());
        try {
            File photos = new File(getContext().getFilesDir(), "photos");
            assertTrue(photos.isDirectory() || photos.mkdirs());
            String traversal = new File(photos, "../" + outside.getName()).getAbsolutePath();
            store.deletePhoto(traversal);
            store.cleanupCameraFile(outside);
            assertTrue("Unowned files must survive cleanup", outside.exists());
            try {
                store.cameraUri(outside);
                fail("Outside camera URI should be rejected");
            } catch (IllegalArgumentException expected) {
                assertNotNull(expected.getMessage());
            }
        } finally {
            outside.delete();
        }
    }

    public void testCameraFileUsesContentUriAndCanBeCleaned() throws Exception {
        File camera = store.newCameraFile();
        try {
            Uri uri = store.cameraUri(camera);
            assertEquals("content", uri.getScheme());
            assertEquals(getContext().getPackageName() + ".files", uri.getAuthority());
            assertTrue(camera.exists());
        } finally {
            store.cleanupCameraFile(camera);
        }
        assertFalse(camera.exists());
    }

    public void testExportPreservesChineseBomAndCreatesUniqueFiles() throws Exception {
        String csv = "\uFEFF名称,数量\r\n\"标签：备用\",3\r\n";
        File first = ExportFiles.writeCsv(getContext(), csv);
        File second = ExportFiles.writeCsv(getContext(), csv);
        try {
            assertFalse(first.equals(second));
            assertEquals(csv, new String(Files.readAllBytes(first.toPath()), StandardCharsets.UTF_8));
            assertEquals("content", ExportFiles.uri(getContext(), first).getScheme());
            try {
                ExportFiles.uri(getContext(), new File(getContext().getFilesDir(), "private.csv"));
                fail("A share URI must not expose private files");
            } catch (IllegalArgumentException expected) {
                assertNotNull(expected.getMessage());
            }
        } finally {
            first.delete();
            second.delete();
        }
    }

    private static int childCount(File directory) {
        String[] names = directory.list();
        return names == null ? 0 : names.length;
    }

    private static void writeTestImage(File destination, int width, int height) throws IOException {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.rgb(47, 102, 78));
        try (FileOutputStream output = new FileOutputStream(destination)) {
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) {
                throw new IOException("Unable to create fixture image");
            }
        } finally {
            bitmap.recycle();
        }
    }
}

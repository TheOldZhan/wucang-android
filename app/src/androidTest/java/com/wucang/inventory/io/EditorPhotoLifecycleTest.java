package com.wucang.inventory.io;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;

import com.wucang.inventory.ItemEditorActivity;
import com.wucang.inventory.data.InventoryItem;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Checks the camera-source lifetime across the real editor's pause/resume callbacks. */
public final class EditorPhotoLifecycleTest
        extends ActivityInstrumentationTestCase2<ItemEditorActivity> {
    public EditorPhotoLifecycleTest() { super(ItemEditorActivity.class); }

    public void testPausedImportKeepsRecoverableSourceUntilResumed() throws Exception {
        setActivityIntent(new Intent(Intent.ACTION_MAIN));
        ItemEditorActivity activity = getActivity();
        PhotoStore store = new PhotoStore(activity);
        File camera = store.newCameraFile();
        Bitmap image = Bitmap.createBitmap(40, 20, Bitmap.Config.ARGB_8888);
        image.eraseColor(Color.GREEN);
        try (FileOutputStream stream = new FileOutputStream(camera)) {
            assertTrue(image.compress(Bitmap.CompressFormat.JPEG, 90, stream));
        } finally {
            image.recycle();
        }
        Bundle saved = new Bundle();
        Method result = ItemEditorActivity.class.getDeclaredMethod("onActivityResult",
                int.class, int.class, Intent.class);
        result.setAccessible(true);
        Field cameraField = field("cameraFile");
        cameraField.set(activity, camera);
        String[] outputPath = new String[1];
        try {
            getInstrumentation().runOnMainSync(() -> {
                getInstrumentation().callActivityOnPause(activity);
                try { result.invoke(activity, 51, Activity.RESULT_OK, null); }
                catch (Exception error) { throw new RuntimeException(error); }
                getInstrumentation().callActivityOnSaveInstanceState(activity, saved);
            });

            long deadline = SystemClock.uptimeMillis() + 10000;
            boolean[] ready = new boolean[1];
            while (!ready[0] && SystemClock.uptimeMillis() < deadline) {
                getInstrumentation().runOnMainSync(() -> {
                    try { ready[0] = field("importReady").getBoolean(activity); }
                    catch (Exception error) { throw new RuntimeException(error); }
                });
                if (!ready[0]) SystemClock.sleep(25);
            }
            assertTrue("Photo import should complete in background", ready[0]);
            assertTrue("Saved camera source remains readable while stopped", camera.exists());
            assertNotNull("Saved state retains import URI", saved.getString("import"));
            assertEquals(camera.getAbsolutePath(), saved.getString("camera"));

            getInstrumentation().runOnMainSync(() -> {
                getInstrumentation().callActivityOnResume(activity);
                try {
                    InventoryItem draft = (InventoryItem) field("draft").get(activity);
                    outputPath[0] = draft.photoPath;
                    assertFalse(field("importing").getBoolean(activity));
                    assertNull(field("pendingImport").get(activity));
                } catch (Exception error) { throw new RuntimeException(error); }
            });
            assertNotNull(outputPath[0]);
            assertTrue("Resumed editor uses a durable private photo", new File(outputPath[0]).isFile());
            assertFalse("Camera cache is cleaned after resume", camera.exists());
        } finally {
            store.cleanupCameraFile(camera);
            store.deletePhoto(outputPath[0]);
            getInstrumentation().runOnMainSync(activity::finish);
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = ItemEditorActivity.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
}

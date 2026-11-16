package com.wucang.inventory;

import android.app.Activity;
import android.app.Instrumentation;
import android.test.ActivityInstrumentationTestCase2;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.wucang.inventory.data.InventoryItem;
import com.wucang.inventory.data.InventoryRepository;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Real UI regression checks. Only records bearing this test's unique name are removed. */
@SuppressWarnings("deprecation")
public final class InventoryUiTest extends ActivityInstrumentationTestCase2<MainActivity> {
    private InventoryRepository repository;
    private String ownPrefix;
    private Activity editor;
    private MainActivity inventoryActivity;

    public InventoryUiTest() {
        super(MainActivity.class);
    }

    @Override protected void setUp() throws Exception {
        super.setUp();
        setActivityInitialTouchMode(false);
        ownPrefix = "UI回归-" + UUID.randomUUID().toString() + "-";
        repository = new InventoryRepository(getInstrumentation().getTargetContext());
    }

    @Override protected void tearDown() throws Exception {
        try {
            try {
                getInstrumentation().runOnMainSync(() -> {
                    if (editor != null && !editor.isDestroyed() && !editor.isFinishing()) editor.finish();
                    if (inventoryActivity != null && !inventoryActivity.isDestroyed()
                            && !inventoryActivity.isFinishing()) inventoryActivity.finish();
                });
                getInstrumentation().waitForIdleSync();
            } finally {
                // ActivityTestCase.tearDown scrubs subclass fields. Clean before calling it.
                if (repository != null) {
                    try {
                        for (InventoryItem item : repository.getItems()) {
                            if (item.name.startsWith(ownPrefix)) repository.deleteItem(item.id);
                        }
                        for (InventoryItem item : repository.getItems()) {
                            assertFalse("UI test must remove its own temporary record",
                                    item.name.startsWith(ownPrefix));
                        }
                    } finally {
                        repository.close();
                    }
                }
            }
        } finally {
            super.tearDown();
        }
    }

    public void testTwoRapidClicksOnSameCardIncrementTwice() {
        InventoryItem item = new InventoryItem();
        item.name = ownPrefix + "连点物品";
        item.quantity = 7;
        repository.saveItem(item);

        MainActivity activity = getActivity();
        inventoryActivity = activity;
        getInstrumentation().waitForIdleSync();
        AtomicReference<View> increment = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> increment.set(findByDescription(
                activity.getWindow().getDecorView(), "增加 " + item.name + " 数量")));
        assertNotNull("Expected the newest item's increment button in the visible inventory list", increment.get());

        AtomicBoolean firstClicked = new AtomicBoolean();
        AtomicBoolean secondClicked = new AtomicBoolean();
        getInstrumentation().runOnMainSync(() -> {
            // Keep the same pre-layout View: the previous implementation reused its stale model twice.
            firstClicked.set(increment.get().performClick());
            secondClicked.set(increment.get().performClick());
        });
        assertTrue(firstClicked.get());
        assertTrue(secondClicked.get());
        getInstrumentation().waitForIdleSync();
        assertEquals("Both clicks must persist even before ListView replaces the old card", 9,
                repository.getItem(item.id).quantity);
    }

    public void testNewItemFormSavesEditedFields() {
        MainActivity activity = getActivity();
        inventoryActivity = activity;
        getInstrumentation().waitForIdleSync();
        AtomicReference<View> add = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> add.set(findByText(
                activity.getWindow().getDecorView(), "＋  新建物品")));
        assertNotNull("Expected the new-item action on the inventory screen", add.get());
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(
                ItemEditorActivity.class.getName(), null, false);
        try {
            getInstrumentation().runOnMainSync(() -> add.get().performClick());
            editor = monitor.waitForActivityWithTimeout(5000);
            assertNotNull("New-item action should open the editor", editor);
        } finally {
            getInstrumentation().removeMonitor(monitor);
        }
        getInstrumentation().waitForIdleSync();

        String name = ownPrefix + "表单物品";
        EditText[] fields = new EditText[6];
        int[] ids = {R.id.item_name, R.id.item_quantity, R.id.item_unit,
                R.id.item_threshold, R.id.item_location, R.id.item_note};
        AtomicReference<View> save = new AtomicReference<>();
        getInstrumentation().runOnMainSync(() -> {
            for (int i = 0; i < fields.length; i++) fields[i] = editor.findViewById(ids[i]);
            save.set(findByText(editor.getWindow().getDecorView(), "保存"));
        });
        for (int i = 0; i < fields.length; i++) assertNotNull("Expected form field " + ids[i], fields[i]);
        assertNotNull("Expected the editor's save action", save.get());
        AtomicBoolean savedClick = new AtomicBoolean();
        getInstrumentation().runOnMainSync(() -> {
            fields[0].setText(name);
            fields[1].setText("14");
            fields[2].setText("盒");
            fields[3].setText("3");
            fields[4].setText("书房第二层");
            fields[5].setText("表单保存验证\n保留换行");
            savedClick.set(save.get().performClick());
        });
        assertTrue(savedClick.get());
        getInstrumentation().waitForIdleSync();

        InventoryItem saved = null;
        for (InventoryItem candidate : repository.getItems()) {
            if (candidate.name.equals(name)) {
                assertNull("The form should create exactly one record", saved);
                saved = candidate;
            }
        }
        assertNotNull("Saving the actual editor form should create a database record", saved);
        assertEquals(14, saved.quantity);
        assertEquals("盒", saved.unit);
        assertEquals(3, saved.lowStockThreshold);
        assertEquals("书房第二层", saved.location);
        assertEquals("表单保存验证\n保留换行", saved.note);
        assertEquals("", saved.category);
        assertTrue(saved.tags.isEmpty());
        assertTrue(saved.createdAt > 0);
    }

    private static View findByDescription(View root, String wanted) {
        CharSequence description = root.getContentDescription();
        if (description != null && wanted.contentEquals(description)) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findByDescription(group.getChildAt(i), wanted);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static View findByText(View root, String wanted) {
        if (root instanceof TextView && wanted.contentEquals(((TextView) root).getText())) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findByText(group.getChildAt(i), wanted);
                if (found != null) return found;
            }
        }
        return null;
    }
}

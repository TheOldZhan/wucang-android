package com.wucang.inventory.data;

import android.content.Context;
import android.content.ContextWrapper;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.test.AndroidTestCase;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Exercises the real SQLite implementation on an Android device, isolated from user data. */
@SuppressWarnings("deprecation")
public final class InventoryRepositoryTest extends AndroidTestCase {
    private InventoryRepository repository;
    private Context isolated;

    @Override protected void setUp() throws Exception {
        super.setUp();
        isolated = new IsolatedDatabaseContext(getContext());
        isolated.deleteDatabase(InventoryRepository.DATABASE_NAME);
        repository = new InventoryRepository(isolated);
        assertEquals("Repository must open this test's private database path",
                isolated.getDatabasePath(InventoryRepository.DATABASE_NAME).getAbsolutePath(),
                repository.getReadableDatabase().getPath());
    }

    @Override protected void tearDown() throws Exception {
        if (repository != null) repository.close();
        isolated.deleteDatabase(InventoryRepository.DATABASE_NAME);
        super.tearDown();
    }

    public void testNewWarehouseHasNoFakeItems() {
        assertEquals(0, repository.getItems().size());
        assertTrue(repository.getCategories().contains("数码设备"));
        assertTrue(repository.getTags().contains("常用"));
    }

    public void testCreateEditAndReopenPreservesAllFields() {
        InventoryItem item = item("  相机  ", "数码设备", 3, "常用", "旅行");
        item.photoPath = "/data/user/0/com.wucang.inventory/files/photos/camera.jpg";
        item.location = "玄关第二格";
        item.note = "备用电池\n已充电";
        item.unit = "台";
        item.lowStockThreshold = 2;
        long id = repository.saveItem(item);
        assertTrue(id > 0);
        assertEquals("相机", item.name);
        long created = item.createdAt;
        long before = item.updatedAt;
        item.name = "数码相机";
        item.quantity = 2;
        item.tags = Collections.singletonList("旅行");
        assertEquals(id, repository.saveItem(item));
        assertTrue(item.updatedAt > before);
        repository.close();
        repository = new InventoryRepository(isolated);
        InventoryItem loaded = repository.getItem(id);
        assertNotNull(loaded);
        assertEquals("数码相机", loaded.name);
        assertEquals("数码设备", loaded.category);
        assertEquals(item.photoPath, loaded.photoPath);
        assertEquals("玄关第二格", loaded.location);
        assertEquals("备用电池\n已充电", loaded.note);
        assertEquals("台", loaded.unit);
        assertEquals(2, loaded.quantity);
        assertEquals(2, loaded.lowStockThreshold);
        assertEquals(created, loaded.createdAt);
        assertEquals(Collections.singletonList("旅行"), loaded.tags);
        assertEquals(1, repository.getItems().size());
    }

    public void testQuantityChangeCrossesStatusBoundariesAndPersists() {
        InventoryItem item = item("胶带", "办公文具", 5);
        item.lowStockThreshold = 2;
        repository.saveItem(item);
        long initialTime = item.updatedAt;
        repository.updateQuantity(item.id, 2);
        assertEquals("low", repository.getItem(item.id).status());
        assertEquals("库存不足", repository.getItem(item.id).statusLabel());
        assertTrue(repository.getItem(item.id).updatedAt > initialTime);
        repository.updateQuantity(item.id, 0);
        assertEquals("out", repository.getItem(item.id).status());
        assertEquals("已用尽", repository.getItem(item.id).statusLabel());
        repository.updateQuantity(item.id, 3);
        assertEquals("normal", repository.getItem(item.id).status());
        assertEquals("库存充足", repository.getItem(item.id).statusLabel());
        assertEquals(3, repository.getItems().get(0).quantity);
    }

    public void testInvalidEditDoesNotPartiallyWriteOrCreateTags() {
        InventoryItem item = item("灯", "生活用品", 1, "常用");
        repository.saveItem(item);
        item.name = "";
        item.category = "未提交分类";
        item.tags = Collections.singletonList("未提交标签");
        expectInvalid(() -> repository.saveItem(item));
        assertEquals("灯", repository.getItem(item.id).name);
        assertFalse(repository.getCategories().contains("未提交分类"));
        assertFalse(repository.getTags().contains("未提交标签"));
        assertEquals(Collections.singletonList("常用"), repository.getItem(item.id).tags);
    }

    public void testBoundsAndMissingIdsRejectedWithoutDamage() {
        InventoryItem item = item("测试物品", "", 0);
        repository.saveItem(item);
        expectInvalid(() -> repository.updateQuantity(item.id, -1));
        expectInvalid(() -> repository.updateQuantity(item.id, 1_000_000_000));
        expectInvalid(() -> repository.updateQuantity(item.id + 500, 1));
        assertEquals(0, repository.getItem(item.id).quantity);
        repository.updateQuantity(item.id, InventoryRepository.MAX_QUANTITY);
        assertEquals(InventoryRepository.MAX_QUANTITY, repository.getItem(item.id).quantity);
        item.lowStockThreshold = -1;
        expectInvalid(() -> repository.saveItem(item));
        assertEquals(1, repository.getItem(item.id).lowStockThreshold);
    }

    public void testCategoryRenameAndDeletePreserveItem() {
        InventoryItem item = item("路由器", "网络设备", 1);
        repository.saveItem(item);
        repository.renameCategory("网络设备", "网络配件");
        assertEquals("网络配件", repository.getItem(item.id).category);
        assertFalse(repository.getCategories().contains("网络设备"));
        repository.deleteCategory("网络配件");
        assertEquals("", repository.getItem(item.id).category);
        assertEquals(1, repository.getItems().size());
        assertEquals("路由器", repository.getItem(item.id).name);
    }

    public void testTagRenameDeleteAndItemDeletionKeepUnrelatedData() {
        InventoryItem first = item("电池", "生活用品", 2, "易耗品", "常用");
        InventoryItem second = item("遥控器", "数码设备", 1, "常用");
        repository.saveItem(first);
        repository.saveItem(second);
        repository.renameTag("常用", "日常使用");
        assertTrue(repository.getItem(first.id).tags.contains("日常使用"));
        assertEquals(Collections.singletonList("日常使用"), repository.getItem(second.id).tags);
        repository.deleteTag("日常使用");
        assertEquals(Collections.singletonList("易耗品"), repository.getItem(first.id).tags);
        assertTrue(repository.getItem(second.id).tags.isEmpty());
        repository.deleteItem(first.id);
        assertNull(repository.getItem(first.id));
        assertNotNull(repository.getItem(second.id));
        assertTrue(repository.getTags().contains("易耗品"));
    }

    public void testTaxonomyCollisionDoesNotMergeOrEraseLinks() {
        InventoryItem first = item("A", "第一类", 1, "第一签");
        InventoryItem second = item("B", "第二类", 1, "第二签");
        repository.saveItem(first);
        repository.saveItem(second);
        expectInvalid(() -> repository.renameCategory("第一类", "第二类"));
        expectInvalid(() -> repository.renameTag("第一签", "第二签"));
        expectInvalid(() -> repository.addCategory(" 第一类 "));
        assertEquals("第一类", repository.getItem(first.id).category);
        assertEquals(Collections.singletonList("第一签"), repository.getItem(first.id).tags);
        assertEquals("第二类", repository.getItem(second.id).category);
    }

    public void testCaseOnlyRenamesPropagateAndRepeatedTagsNormalize() {
        InventoryItem item = item("Cable", "USB", 1, "Office", "office", " Office ");
        repository.saveItem(item);
        assertEquals(1, item.tags.size());
        repository.renameCategory("USB", "Usb");
        repository.renameTag("Office", "OFFICE");
        assertEquals("Usb", repository.getItem(item.id).category);
        assertEquals(Collections.singletonList("OFFICE"), repository.getItem(item.id).tags);
    }

    public void testCombinedSearchCategoryStatusAndAllTags() {
        InventoryItem first = item("USB 连接线", "数码设备", 2, "常用", "备用");
        first.lowStockThreshold = 2;
        first.location = "书房抽屉";
        InventoryItem second = item("USB 充电头", "数码设备", 1, "常用");
        InventoryItem third = item("USB 转接头", "", 0, "常用", "备用");
        List<InventoryItem> source = Arrays.asList(first, second, third);
        List<InventoryItem> result = InventoryQuery.filter(source, "usb", "数码设备",
                new HashSet<>(Arrays.asList("常用", "备用")), "low", "recent");
        assertEquals(Collections.singletonList(first), result);
        assertEquals(Collections.singletonList(first), InventoryQuery.filter(source, "书房", "",
                Collections.emptySet(), "all", "recent"));
        assertEquals(Collections.singletonList(third), InventoryQuery.filter(source, "", "__uncategorized__",
                Collections.emptySet(), "out", "recent"));
        assertTrue(InventoryQuery.filter(source, "不存在", "", Collections.emptySet(), "all", "recent").isEmpty());
        assertEquals(3, source.size());
    }

    public void testSortingAndZeroThreshold() {
        InventoryItem first = item("B", "", 3);
        first.updatedAt = 1;
        InventoryItem second = item("A", "", 1);
        second.updatedAt = 2;
        second.lowStockThreshold = 0;
        List<InventoryItem> source = Arrays.asList(first, second);
        assertEquals("normal", second.status());
        assertEquals(second, InventoryQuery.filter(source, "", "", null, "all", "quantityAsc").get(0));
        assertEquals(first, InventoryQuery.filter(source, "", "", null, "all", "quantityDesc").get(0));
        assertEquals(second, InventoryQuery.filter(source, "", "", null, "all", "recent").get(0));
        assertEquals(second, InventoryQuery.filter(source, "", "", null, "all", "name").get(0));
    }

    public void testCsvEscapesChineseMultilineQuotesAndFormulaText() {
        InventoryItem item = item("中文,\"标签\"", "+危险公式", 42, "@SUM(A1)");
        item.unit = "=1+1";
        item.location = " \t=HYPERLINK(\"https://example.invalid\")";
        item.note = "第一行\n第二行,\"备注\"";
        item.createdAt = 1_700_000_000_000L;
        item.updatedAt = 1_700_000_100_000L;
        String csv = CsvExporter.create(Collections.singletonList(item), 1_700_000_200_000L);
        assertTrue(csv.startsWith("\uFEFF\"名称\""));
        assertTrue(csv.contains("\"中文,\"\"标签\"\"\""));
        assertTrue(csv.contains("\"'+危险公式\""));
        assertTrue(csv.contains("\"'@SUM(A1)\""));
        assertTrue(csv.contains("\"'=1+1\""));
        assertTrue(csv.contains("\"' \t=HYPERLINK("));
        assertTrue(csv.contains("\"第一行\n第二行,\"\"备注\"\"\""));
        assertTrue(csv.contains("\"42\""));
        assertTrue(csv.endsWith("\r\n"));
        item.name = "-1+2";
        item.note = "\t=1+1";
        String guarded = CsvExporter.create(Collections.singletonList(item), 0);
        assertTrue(guarded.contains("\"'-1+2\""));
        assertTrue(guarded.contains("\"'\t=1+1\""));
    }

    public void testCsvExportUsesLatestQuantityAfterReopen() {
        InventoryItem item = item("补货后的纸巾", "生活用品", 1);
        repository.saveItem(item);
        repository.updateQuantity(item.id, 24);
        repository.close();
        repository = new InventoryRepository(isolated);
        String csv = CsvExporter.create(repository.getItems(), System.currentTimeMillis());
        assertTrue(csv.contains("\"24\",\"件\""));
        assertFalse(csv.contains("\"1\",\"件\""));
    }

    private static InventoryItem item(String name, String category, int quantity, String... tags) {
        InventoryItem item = new InventoryItem();
        item.name = name;
        item.category = category;
        item.quantity = quantity;
        item.tags = Arrays.asList(tags);
        return item;
    }

    private static void expectInvalid(Runnable action) {
        try {
            action.run();
            fail("Expected invalid input to be rejected");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    /** Modern SQLiteOpenHelper opens getDatabasePath directly, bypassing legacy renaming contexts. */
    private static final class IsolatedDatabaseContext extends ContextWrapper {
        private final String prefix = "inventory_test_" + UUID.randomUUID().toString() + "_";

        IsolatedDatabaseContext(Context base) { super(base); }

        private String isolatedName(String name) {
            if (!InventoryRepository.DATABASE_NAME.equals(name)) {
                throw new IllegalArgumentException("Unexpected test database: " + name);
            }
            return prefix + name;
        }

        @Override public Context getApplicationContext() { return this; }

        @Override public File getDatabasePath(String name) {
            return getBaseContext().getDatabasePath(isolatedName(name));
        }

        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                SQLiteDatabase.CursorFactory factory) {
            return getBaseContext().openOrCreateDatabase(isolatedName(name), mode, factory);
        }

        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                SQLiteDatabase.CursorFactory factory, DatabaseErrorHandler errorHandler) {
            return getBaseContext().openOrCreateDatabase(isolatedName(name), mode, factory, errorHandler);
        }

        @Override public boolean deleteDatabase(String name) {
            return getBaseContext().deleteDatabase(isolatedName(name));
        }
    }
}

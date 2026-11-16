package com.wucang.inventory.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Database is private to the app; item edits and all tag links commit together. */
public final class InventoryRepository extends SQLiteOpenHelper {
    public static final String DATABASE_NAME = "inventory.db";
    public static final int MAX_QUANTITY = 999_999_999;

    public InventoryRepository(Context context) {
        super(context, DATABASE_NAME, null, 1);
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE categories (name TEXT PRIMARY KEY COLLATE NOCASE NOT NULL)");
        db.execSQL("CREATE TABLE tags (name TEXT PRIMARY KEY COLLATE NOCASE NOT NULL)");
        db.execSQL("CREATE TABLE items (id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, "
                + "category TEXT COLLATE NOCASE REFERENCES categories(name) ON UPDATE CASCADE ON DELETE SET NULL, "
                + "photo_path TEXT NOT NULL DEFAULT '', quantity INTEGER NOT NULL CHECK(quantity BETWEEN 0 AND 999999999), "
                + "low_stock_threshold INTEGER NOT NULL CHECK(low_stock_threshold BETWEEN 0 AND 999999999), "
                + "unit TEXT NOT NULL DEFAULT '件', location TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '', "
                + "created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE item_tags (item_id INTEGER NOT NULL REFERENCES items(id) ON DELETE CASCADE, "
                + "tag_name TEXT COLLATE NOCASE NOT NULL REFERENCES tags(name) ON UPDATE CASCADE ON DELETE CASCADE, "
                + "PRIMARY KEY (item_id, tag_name))");
        db.execSQL("CREATE INDEX items_updated_at ON items(updated_at DESC)");
        db.execSQL("CREATE INDEX item_tags_tag ON item_tags(tag_name)");
        for (String name : new String[]{"数码设备", "生活用品", "办公文具", "其他"}) insertName(db, "categories", name);
        for (String name : new String[]{"常用", "备用", "易耗品"}) insertName(db, "tags", name);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new IllegalStateException("不支持的数据库版本：" + oldVersion + " → " + newVersion);
    }

    public synchronized List<InventoryItem> getItems() {
        SQLiteDatabase db = getReadableDatabase();
        Map<Long, InventoryItem> items = new LinkedHashMap<>();
        try (Cursor cursor = db.query("items", null, null, null, null, null, "updated_at DESC, id DESC")) {
            while (cursor.moveToNext()) {
                InventoryItem item = readItem(cursor);
                items.put(item.id, item);
            }
        }
        try (Cursor cursor = db.rawQuery("SELECT item_id, tag_name FROM item_tags ORDER BY tag_name COLLATE LOCALIZED", null)) {
            while (cursor.moveToNext()) {
                InventoryItem item = items.get(cursor.getLong(0));
                if (item != null) item.tags.add(cursor.getString(1));
            }
        }
        return new ArrayList<>(items.values());
    }

    public synchronized InventoryItem getItem(long id) {
        SQLiteDatabase db = getReadableDatabase();
        InventoryItem item;
        try (Cursor cursor = db.query("items", null, "id = ?", new String[]{Long.toString(id)}, null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            item = readItem(cursor);
        }
        try (Cursor cursor = db.rawQuery("SELECT tag_name FROM item_tags WHERE item_id = ? ORDER BY tag_name COLLATE LOCALIZED",
                new String[]{Long.toString(id)})) {
            while (cursor.moveToNext()) item.tags.add(cursor.getString(0));
        }
        return item;
    }

    public synchronized long saveItem(InventoryItem original) {
        if (original == null) throw new IllegalArgumentException("物品不能为空");
        InventoryItem item = validate(original.copy());
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            if (item.id > 0) {
                InventoryItem existing = getItem(item.id);
                if (existing == null) throw new IllegalArgumentException("物品已不存在，请刷新列表");
                item.createdAt = existing.createdAt;
                item.updatedAt = Math.max(now, existing.updatedAt + 1);
            } else {
                item.createdAt = now;
                item.updatedAt = now;
            }
            if (!item.category.isEmpty()) {
                insertName(db, "categories", item.category);
                item.category = findName(db, "categories", item.category);
            }
            List<String> canonicalTags = new ArrayList<>();
            for (String tag : item.tags) {
                insertName(db, "tags", tag);
                canonicalTags.add(findName(db, "tags", tag));
            }
            item.tags = canonicalTags;
            ContentValues values = itemValues(item);
            if (item.id > 0) {
                db.update("items", values, "id = ?", new String[]{Long.toString(item.id)});
                db.delete("item_tags", "item_id = ?", new String[]{Long.toString(item.id)});
            } else {
                item.id = db.insertOrThrow("items", null, values);
            }
            for (String tag : item.tags) {
                ContentValues link = new ContentValues();
                link.put("item_id", item.id);
                link.put("tag_name", tag);
                db.insertOrThrow("item_tags", null, link);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        copyInto(item, original);
        return item.id;
    }

    public synchronized void updateQuantity(long id, int quantity) {
        checkQuantity(quantity, "数量");
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("quantity", quantity);
        db.beginTransaction();
        try {
            InventoryItem existing = getItem(id);
            if (existing == null) throw new IllegalArgumentException("物品已不存在，请刷新列表");
            values.put("updated_at", Math.max(System.currentTimeMillis(), existing.updatedAt + 1));
            db.update("items", values, "id = ?", new String[]{Long.toString(id)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    public synchronized void deleteItem(long id) {
        getWritableDatabase().delete("items", "id = ?", new String[]{Long.toString(id)});
    }

    public synchronized List<String> getCategories() { return names("categories"); }
    public synchronized List<String> getTags() { return names("tags"); }
    public synchronized void addCategory(String name) { addName("categories", name); }
    public synchronized void addTag(String name) { addName("tags", name); }
    public synchronized void renameCategory(String oldName, String newName) { rename("categories", oldName, newName); }
    public synchronized void renameTag(String oldName, String newName) { rename("tags", oldName, newName); }
    public synchronized void deleteCategory(String name) { deleteName("categories", name); }
    public synchronized void deleteTag(String name) { deleteName("tags", name); }

    private List<String> names(String table) {
        List<String> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(table, new String[]{"name"}, null, null, null, null, "name COLLATE LOCALIZED")) {
            while (cursor.moveToNext()) result.add(cursor.getString(0));
        }
        return result;
    }

    private void addName(String table, String input) {
        String name = validName(input);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (findName(db, table, name) != null) throw new IllegalArgumentException("名称已存在");
            insertName(db, table, name);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private void rename(String table, String oldInput, String newInput) {
        String oldName = validName(oldInput), newName = validName(newInput);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String existing = findName(db, table, oldName);
            if (existing == null) throw new IllegalArgumentException("名称已不存在，请刷新列表");
            String collision = findName(db, table, newName);
            if (collision != null && !existing.equals(collision)) throw new IllegalArgumentException("名称已存在");
            if (!existing.equals(newName)) {
                long now = System.currentTimeMillis();
                if ("categories".equals(table)) {
                    db.execSQL("UPDATE items SET updated_at = MAX(updated_at + 1, ?) WHERE category = ?", new Object[]{now, existing});
                } else {
                    db.execSQL("UPDATE items SET updated_at = MAX(updated_at + 1, ?) WHERE id IN (SELECT item_id FROM item_tags WHERE tag_name = ?)", new Object[]{now, existing});
                }
                ContentValues values = new ContentValues();
                values.put("name", newName);
                db.update(table, values, "name = ?", new String[]{existing});
                // NOCASE keys can compare equal for a case-only rename; explicitly normalize links too.
                if ("categories".equals(table)) {
                    ContentValues linked = new ContentValues();
                    linked.put("category", newName);
                    db.update("items", linked, "category = ?", new String[]{existing});
                } else {
                    ContentValues linked = new ContentValues();
                    linked.put("tag_name", newName);
                    db.update("item_tags", linked, "tag_name = ?", new String[]{existing});
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private void deleteName(String table, String input) {
        String name = validName(input);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            if ("categories".equals(table)) {
                db.execSQL("UPDATE items SET updated_at = MAX(updated_at + 1, ?) WHERE category = ?", new Object[]{now, name});
            } else {
                db.execSQL("UPDATE items SET updated_at = MAX(updated_at + 1, ?) WHERE id IN (SELECT item_id FROM item_tags WHERE tag_name = ?)", new Object[]{now, name});
            }
            db.delete(table, "name = ?", new String[]{name});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private static void insertName(SQLiteDatabase db, String table, String name) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static String findName(SQLiteDatabase db, String table, String name) {
        try (Cursor cursor = db.query(table, new String[]{"name"}, "name = ?", new String[]{name}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    private static InventoryItem readItem(Cursor cursor) {
        InventoryItem item = new InventoryItem();
        item.id = cursor.getLong(cursor.getColumnIndexOrThrow("id"));
        item.name = string(cursor, "name");
        item.category = string(cursor, "category");
        item.photoPath = string(cursor, "photo_path");
        item.quantity = cursor.getInt(cursor.getColumnIndexOrThrow("quantity"));
        item.lowStockThreshold = cursor.getInt(cursor.getColumnIndexOrThrow("low_stock_threshold"));
        item.unit = string(cursor, "unit");
        item.location = string(cursor, "location");
        item.note = string(cursor, "note");
        item.createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at"));
        item.updatedAt = cursor.getLong(cursor.getColumnIndexOrThrow("updated_at"));
        return item;
    }

    private static String string(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? "" : cursor.getString(index);
    }

    private static ContentValues itemValues(InventoryItem item) {
        ContentValues values = new ContentValues();
        values.put("name", item.name);
        if (item.category.isEmpty()) values.putNull("category"); else values.put("category", item.category);
        values.put("photo_path", item.photoPath);
        values.put("quantity", item.quantity);
        values.put("low_stock_threshold", item.lowStockThreshold);
        values.put("unit", item.unit);
        values.put("location", item.location);
        values.put("note", item.note);
        values.put("created_at", item.createdAt);
        values.put("updated_at", item.updatedAt);
        return values;
    }

    private static InventoryItem validate(InventoryItem item) {
        if (item.id < 0) throw new IllegalArgumentException("物品编号无效");
        item.name = bounded(item.name, 120, "物品名称");
        if (item.name.isEmpty()) throw new IllegalArgumentException("请输入物品名称");
        item.category = bounded(item.category, 40, "分类名称");
        if (InventoryQuery.UNCATEGORIZED.equals(item.category)) throw new IllegalArgumentException("此分类名称为系统保留名称");
        item.photoPath = bounded(item.photoPath, 4096, "照片路径");
        item.location = bounded(item.location, 120, "存放位置");
        item.note = item.note == null ? "" : item.note;
        if (item.note.length() > 10000) throw new IllegalArgumentException("备注不能超过 10000 个字符");
        item.unit = bounded(item.unit, 20, "单位");
        if (item.unit.isEmpty()) item.unit = "件";
        checkQuantity(item.quantity, "数量");
        checkQuantity(item.lowStockThreshold, "低库存阈值");
        Map<String, String> tags = new LinkedHashMap<>();
        if (item.tags != null) for (String raw : item.tags) {
            String tag = validName(raw);
            tags.putIfAbsent(tag.toLowerCase(Locale.ROOT), tag);
        }
        item.tags = new ArrayList<>(tags.values());
        return item;
    }

    private static String validName(String value) {
        String name = bounded(value, 40, "分类或标签名称");
        if (name.isEmpty()) throw new IllegalArgumentException("名称不能为空");
        if (InventoryQuery.UNCATEGORIZED.equals(name)) throw new IllegalArgumentException("此名称为系统保留名称");
        return name;
    }

    private static String bounded(String value, int max, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.length() > max) throw new IllegalArgumentException(label + "不能超过 " + max + " 个字符");
        return normalized;
    }

    private static void checkQuantity(int value, String label) {
        if (value < 0 || value > MAX_QUANTITY) throw new IllegalArgumentException(label + "必须在 0–999999999 之间");
    }

    private static void copyInto(InventoryItem source, InventoryItem target) {
        target.id = source.id;
        target.name = source.name;
        target.category = source.category;
        target.photoPath = source.photoPath;
        target.quantity = source.quantity;
        target.lowStockThreshold = source.lowStockThreshold;
        target.unit = source.unit;
        target.location = source.location;
        target.note = source.note;
        target.createdAt = source.createdAt;
        target.updatedAt = source.updatedAt;
        target.tags = new ArrayList<>(source.tags);
    }
}

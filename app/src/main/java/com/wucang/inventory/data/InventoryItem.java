package com.wucang.inventory.data;

import java.util.ArrayList;
import java.util.List;

/** One locally stored inventory record. Quantities are whole nonnegative units. */
public final class InventoryItem {
    public long id;
    public long createdAt;
    public long updatedAt;
    public String name = "";
    public String category = "";
    public String photoPath = "";
    public String location = "";
    public String note = "";
    public String unit = "件";
    public int quantity;
    public int lowStockThreshold = 1;
    public List<String> tags = new ArrayList<>();

    public InventoryItem copy() {
        InventoryItem item = new InventoryItem();
        item.id = id;
        item.createdAt = createdAt;
        item.updatedAt = updatedAt;
        item.name = name;
        item.category = category;
        item.photoPath = photoPath;
        item.location = location;
        item.note = note;
        item.unit = unit;
        item.quantity = quantity;
        item.lowStockThreshold = lowStockThreshold;
        item.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
        return item;
    }

    public String status() {
        if (quantity == 0) return "out";
        return quantity <= lowStockThreshold ? "low" : "normal";
    }

    public String statusLabel() {
        switch (status()) {
            case "out": return "已用尽";
            case "low": return "库存不足";
            default: return "库存充足";
        }
    }
}

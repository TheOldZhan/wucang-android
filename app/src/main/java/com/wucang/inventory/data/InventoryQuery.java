package com.wucang.inventory.data;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Pure filtering shared by the list, summary and filtered export. */
public final class InventoryQuery {
    public static final String UNCATEGORIZED = "__uncategorized__";

    private InventoryQuery() {}

    public static List<InventoryItem> filter(List<InventoryItem> items, String search,
            String category, Set<String> tags, String status, String sort) {
        String needle = clean(search).trim().toLowerCase(Locale.ROOT);
        List<InventoryItem> result = new ArrayList<>();
        if (items == null) return result;
        for (InventoryItem item : items) {
            if (item == null) continue;
            if (UNCATEGORIZED.equals(category)) {
                if (!clean(item.category).isEmpty()) continue;
            } else if (!clean(category).isEmpty()
                    && !category.equalsIgnoreCase(clean(item.category))) continue;
            if (!clean(status).isEmpty() && !"all".equals(status)
                    && !status.equals(item.status())) continue;
            boolean matchesTags = true;
            if (tags != null) {
                for (String selected : tags) {
                    boolean found = false;
                    if (item.tags != null) {
                        for (String tag : item.tags) {
                            if (clean(selected).equalsIgnoreCase(clean(tag))) {
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) { matchesTags = false; break; }
                }
            }
            if (!matchesTags) continue;
            if (!needle.isEmpty()) {
                StringBuilder searchable = new StringBuilder();
                searchable.append(clean(item.name)).append('\n')
                        .append(clean(item.category)).append('\n')
                        .append(clean(item.location)).append('\n')
                        .append(clean(item.note));
                if (item.tags != null) for (String tag : item.tags) {
                    searchable.append('\n').append(clean(tag));
                }
                if (!searchable.toString().toLowerCase(Locale.ROOT).contains(needle)) continue;
            }
            result.add(item);
        }
        Comparator<InventoryItem> recent = Comparator
                .comparingLong((InventoryItem item) -> item.updatedAt).reversed()
                .thenComparing(Comparator.comparingLong((InventoryItem item) -> item.id).reversed());
        if ("name".equals(sort)) {
            Collator collator = Collator.getInstance(Locale.SIMPLIFIED_CHINESE);
            result.sort((a, b) -> {
                int order = collator.compare(clean(a.name), clean(b.name));
                return order == 0 ? recent.compare(a, b) : order;
            });
        } else if ("quantityAsc".equals(sort)) {
            result.sort(Comparator.comparingInt((InventoryItem item) -> item.quantity).thenComparing(recent));
        } else if ("quantityDesc".equals(sort)) {
            result.sort(Comparator.comparingInt((InventoryItem item) -> item.quantity).reversed().thenComparing(recent));
        } else {
            result.sort(recent);
        }
        return result;
    }

    private static String clean(String value) { return value == null ? "" : value; }
}

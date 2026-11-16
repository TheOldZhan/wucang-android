package com.wucang.inventory.data;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** UTF-8 BOM and RFC 4180 quoting keep Chinese text and multiline notes readable in Excel. */
public final class CsvExporter {
    private CsvExporter() {}

    public static String create(List<InventoryItem> items, long exportedAt) {
        SimpleDateFormat dates = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        String exported = dates.format(new Date(exportedAt));
        StringBuilder csv = new StringBuilder("\uFEFF");
        row(csv, "名称", "分类", "标签", "数量", "单位", "低库存阈值", "库存状态", "存放位置", "备注", "创建时间", "更新时间", "导出时间");
        if (items == null) return csv.toString();
        for (InventoryItem item : items) {
            String tags = item.tags == null ? "" : String.join(" / ", item.tags);
            row(csv, protect(item.name), protect(item.category), protect(tags),
                    Integer.toString(item.quantity), protect(item.unit),
                    Integer.toString(item.lowStockThreshold), item.statusLabel(),
                    protect(item.location), protect(item.note),
                    dates.format(new Date(item.createdAt)), dates.format(new Date(item.updatedAt)), exported);
        }
        return csv.toString();
    }

    /** Prefix potentially executable spreadsheet cells, including whitespace-prefixed formulas. */
    private static String protect(String value) {
        if (value == null) return "";
        int first = 0;
        while (first < value.length() && (Character.isWhitespace(value.charAt(first))
                || Character.isISOControl(value.charAt(first))
                || Character.isSpaceChar(value.charAt(first)) || value.charAt(first) == '\uFEFF')) first++;
        if (first < value.length() && "=+-@".indexOf(value.charAt(first)) >= 0) return "'" + value;
        // Tabs and CR/LF at the start can become formula escape prefixes in spreadsheet importers.
        if (!value.isEmpty() && "\t\r\n".indexOf(value.charAt(0)) >= 0) return "'" + value;
        return value;
    }

    private static void row(StringBuilder csv, String... cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) csv.append(',');
            String value = cells[i] == null ? "" : cells[i];
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }
}

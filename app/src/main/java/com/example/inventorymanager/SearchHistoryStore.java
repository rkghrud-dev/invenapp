package com.example.inventorymanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SearchHistoryStore {
    private static final String PREFS_NAME = "inventory_manager_prefs";
    private static final String KEY_HISTORY = "inventory_search_history";
    private static final int MAX_ENTRIES = 20;

    private final SharedPreferences preferences;

    public SearchHistoryStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void save(String query, String preview, int resultCount) {
        String cleanQuery = clean(query);
        if (TextUtils.isEmpty(cleanQuery)) {
            return;
        }

        List<Entry> entries = loadEntries();
        List<Entry> updatedEntries = new ArrayList<>();
        for (Entry entry : entries) {
            if (!cleanQuery.equalsIgnoreCase(entry.query)) {
                updatedEntries.add(entry);
            }
        }

        updatedEntries.add(0, new Entry(
                cleanQuery,
                clean(preview),
                Math.max(resultCount, 0),
                System.currentTimeMillis()
        ));

        while (updatedEntries.size() > MAX_ENTRIES) {
            updatedEntries.remove(updatedEntries.size() - 1);
        }

        persist(updatedEntries);
    }

    public List<Entry> loadEntries() {
        List<Entry> entries = new ArrayList<>();
        String raw = preferences.getString(KEY_HISTORY, "[]");
        if (TextUtils.isEmpty(raw)) {
            return entries;
        }

        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }

                String query = clean(object.optString("query", ""));
                if (TextUtils.isEmpty(query)) {
                    continue;
                }

                entries.add(new Entry(
                        query,
                        clean(object.optString("preview", "")),
                        Math.max(object.optInt("resultCount", 0), 0),
                        object.optLong("savedAt", System.currentTimeMillis())
                ));
            }
        } catch (Exception ignored) {
            preferences.edit().remove(KEY_HISTORY).apply();
        }
        return entries;
    }

    private void persist(List<Entry> entries) {
        JSONArray array = new JSONArray();
        for (Entry entry : entries) {
            try {
                JSONObject object = new JSONObject();
                object.put("query", entry.query);
                object.put("preview", entry.preview);
                object.put("resultCount", entry.resultCount);
                object.put("savedAt", entry.savedAtMillis);
                array.put(object);
            } catch (Exception ignored) {
            }
        }
        preferences.edit().putString(KEY_HISTORY, array.toString()).apply();
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Entry {
        private final String query;
        private final String preview;
        private final int resultCount;
        private final long savedAtMillis;

        private Entry(String query, String preview, int resultCount, long savedAtMillis) {
            this.query = query;
            this.preview = preview;
            this.resultCount = resultCount;
            this.savedAtMillis = savedAtMillis;
        }

        public String getQuery() {
            return query;
        }

        public String getPreview() {
            return preview;
        }

        public int getResultCount() {
            return resultCount;
        }

        public String getSavedAtLabel() {
            return new SimpleDateFormat("MM-dd HH:mm", Locale.KOREA)
                    .format(new Date(savedAtMillis));
        }
    }
}

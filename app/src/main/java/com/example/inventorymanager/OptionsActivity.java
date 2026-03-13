package com.example.inventorymanager;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;

public class OptionsActivity extends ThemedActivity {
    private SettingsStore settingsStore;
    private SwitchCompat darkModeSwitch;
    private TextView statusText;
    private View[] paletteCards;
    private TextView[] paletteStatusViews;
    private int[] paletteIds;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_options);

        settingsStore = new SettingsStore(this);
        darkModeSwitch = findViewById(R.id.dark_mode_switch);
        statusText = findViewById(R.id.options_status);
        paletteCards = new View[] {
                findViewById(R.id.palette_card_1),
                findViewById(R.id.palette_card_2),
                findViewById(R.id.palette_card_3),
                findViewById(R.id.palette_card_4)
        };
        paletteStatusViews = new TextView[] {
                findViewById(R.id.palette_status_1),
                findViewById(R.id.palette_status_2),
                findViewById(R.id.palette_status_3),
                findViewById(R.id.palette_status_4)
        };
        paletteIds = new int[] {
                SettingsStore.THEME_PALETTE_1,
                SettingsStore.THEME_PALETTE_2,
                SettingsStore.THEME_PALETTE_3,
                SettingsStore.THEME_PALETTE_4
        };

        setupListeners();
        bindState();
    }

    @Override
    protected void onResume() {
        super.onResume();
        bindState();
    }

    private void setupListeners() {
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> toggleDarkMode(isChecked));
        for (int index = 0; index < paletteCards.length; index++) {
            final int paletteId = paletteIds[index];
            paletteCards[index].setOnClickListener(view -> selectPalette(paletteId));
        }
    }

    private void bindState() {
        darkModeSwitch.setOnCheckedChangeListener(null);
        darkModeSwitch.setChecked(settingsStore.isDarkModeEnabled());
        darkModeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> toggleDarkMode(isChecked));
        statusText.setText(settingsStore.isDarkModeEnabled()
                ? R.string.options_dark_mode_on_status
                : R.string.options_dark_mode_off_status);
        bindPaletteState();
    }

    private void bindPaletteState() {
        int selectedPalette = settingsStore.getThemePaletteId();
        for (int index = 0; index < paletteCards.length; index++) {
            boolean isSelected = paletteIds[index] == selectedPalette;
            paletteStatusViews[index].setVisibility(isSelected ? View.VISIBLE : View.GONE);
            paletteCards[index].setAlpha(isSelected ? 1f : 0.88f);
        }
    }

    private void selectPalette(int paletteId) {
        if (settingsStore.getThemePaletteId() == paletteId) {
            return;
        }
        settingsStore.setThemePaletteId(paletteId);
        recreate();
    }

    private void toggleDarkMode(boolean enabled) {
        if (settingsStore.isDarkModeEnabled() == enabled) {
            return;
        }

        settingsStore.setDarkModeEnabled(enabled);
        AppCompatDelegate.setDefaultNightMode(
                enabled ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO
        );
        recreate();
    }
}

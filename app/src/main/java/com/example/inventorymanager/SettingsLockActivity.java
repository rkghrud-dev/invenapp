package com.example.inventorymanager;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class SettingsLockActivity extends ThemedActivity {
    public static final String EXTRA_REASON = "reason";
    private static final String FIXED_PASSWORD = "2701";

    private TextView titleView;
    private TextView summaryView;
    private EditText passwordInput;
    private EditText confirmInput;
    private TextView statusText;
    private Button submitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings_lock);

        titleView = findViewById(R.id.lock_title);
        summaryView = findViewById(R.id.lock_summary);
        passwordInput = findViewById(R.id.password_input);
        confirmInput = findViewById(R.id.password_confirm_input);
        statusText = findViewById(R.id.lock_status);
        submitButton = findViewById(R.id.lock_button);

        titleView.setText(R.string.admin_title);
        summaryView.setText(R.string.settings_lock_fixed_summary);
        confirmInput.setVisibility(android.view.View.GONE);
        submitButton.setText(R.string.settings_lock_button);

        passwordInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit();
                return true;
            }
            return false;
        });
        submitButton.setOnClickListener(v -> submit());
    }

    private void submit() {
        String password = passwordInput.getText().toString().trim();
        if (!TextUtils.equals(FIXED_PASSWORD, password)) {
            statusText.setText(R.string.settings_lock_password_wrong);
            return;
        }
        openAdmin();
    }

    private void openAdmin() {
        Intent intent = new Intent(this, AdminActivity.class);
        intent.putExtra(EXTRA_REASON, getIntent().getStringExtra(EXTRA_REASON));
        startActivity(intent);
        finish();
    }
}
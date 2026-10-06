package com.caodaming.hand_note;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.caodaming.hand_note.data.AppDatabase;
import com.caodaming.hand_note.data.User;
import com.caodaming.hand_note.data.UserDao;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername;
    private EditText etPassword;
    private Button btnLogin;
    private Button btnRegister;

    private UserDao userDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        btnLogin = findViewById(R.id.btn_login);
        btnRegister = findViewById(R.id.btn_register);

        userDao = AppDatabase.getInstance(this).userDao();

        btnLogin.setOnClickListener(v -> login());
        btnRegister.setOnClickListener(v -> register());
    }

    private void login() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.input_required, Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            User user = userDao.login(username, password);
            runOnUiThread(() -> {
                if (user != null) {
                    Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                    intent.putExtra("user_id", user.id);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(this, R.string.login_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void register() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.input_required, Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            User existing = userDao.getUserByUsername(username);
            if (existing != null) {
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.register_failed, Toast.LENGTH_SHORT).show());
                return;
            }

            User user = new User();
            user.username = username;
            user.password = password;
            userDao.insert(user);

            runOnUiThread(() ->
                    Toast.makeText(this, R.string.register_success, Toast.LENGTH_SHORT).show());
        });
    }
}

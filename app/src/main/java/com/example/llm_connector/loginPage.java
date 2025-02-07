package com.example.llm_connector;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.os.Bundle;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.MalformedURLException;

public class loginPage extends AppCompatActivity {

    private EditText editTextIp;
    private Button buttonCheckIp;
    private TextView textViewStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_page);

        editTextIp = findViewById(R.id.editTextIp);
        buttonCheckIp = findViewById(R.id.buttonCheckIp);
        textViewStatus = findViewById(R.id.textViewStatus);

        buttonCheckIp.setOnClickListener(v -> {
            String ipAddress = editTextIp.getText().toString();
            if (ipAddress.isEmpty()) {
                Toast.makeText(loginPage.this, R.string.ip_address_empty, Toast.LENGTH_SHORT).show();
                return;
            }
            checkConnection(ipAddress);
        });
    }

    private void checkConnection(String ipAddress) {
        new CheckConnectionTask().execute(ipAddress);
    }

    private class CheckConnectionTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {
            String ipAddress = params[0];
            String apiUrl = "http://" + ipAddress + "/v1/models";
            Log.d("CheckConnectionTask", "API URL: " + apiUrl);

            try {
                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                int responseCode = connection.getResponseCode();
                Log.d("CheckConnectionTask", "Response Code: " + responseCode);
                connection.disconnect();

                return responseCode == HttpURLConnection.HTTP_OK;

            } catch (MalformedURLException e) {
                Log.e("CheckConnectionTask", "URL tidak valid", e);
                return false;
            } catch (IOException e) {
                Log.e("CheckConnectionTask", "Gagal terhubung", e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean isConnected) {
            if (isConnected) {
                textViewStatus.setText(R.string.connection_success);
                textViewStatus.setTextColor(ContextCompat.getColor(loginPage.this, R.color.success_color));
                Thread thread = new Thread(){
                    public void run() {
                        try {
                            sleep(2000);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        } finally {
                            Intent intent = new Intent(loginPage.this, ChatUIactivity.class);
                            String ipAddress = editTextIp.getText().toString();
                            intent.putExtra("ipAddress", ipAddress);
                            startActivity(intent);
                            finish();
                        }
                    }
                };
                thread.start();
            } else {
                textViewStatus.setText(R.string.connection_failed);
                textViewStatus.setTextColor(ContextCompat.getColor(loginPage.this, R.color.fail_color));
            }
        }
    }
}
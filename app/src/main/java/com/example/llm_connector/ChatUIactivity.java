package com.example.llm_connector;

import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.MalformedURLException;

public class ChatUIactivity extends AppCompatActivity {

    private EditText messageInput;
    private Button sendBtn;
    private TextView responseFromAI;
    private String ipAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_chat_uiactivity);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        messageInput = findViewById(R.id.messageInput);
        sendBtn = findViewById(R.id.sendBtn);
        responseFromAI = findViewById(R.id.responseFromAI);

        ipAddress = getIntent().getStringExtra("ipAddress");
        if (ipAddress == null || ipAddress.isEmpty()) {
            Toast.makeText(this, R.string.ip_address_not_detected, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        sendBtn.setOnClickListener(v -> {
            String message = messageInput.getText().toString();
            if (!message.isEmpty()) {
                sendMessageToAI(message);
                messageInput.getText().clear();
            } else {
                Toast.makeText(ChatUIactivity.this, R.string.empty_message, Toast.LENGTH_SHORT).show();
            }
        });

        checkConnection(ipAddress);
    }

    private void checkConnection(String ipAddress) {
        new CheckConnectionTask().execute(ipAddress, ChatUIactivity.this);
    }

    private static class CheckConnectionTask extends AsyncTask<String, Void, Boolean> {

        private WeakReference<AppCompatActivity> contextReference;

        public AsyncTask<String, Void, Boolean> execute(String ipAddress, AppCompatActivity context) {
            this.contextReference = new WeakReference<>(context);
            return super.execute(ipAddress);
        }

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
                Log.e("CheckConnectionTask", "Gagal terhubung saat pengecekan ulang", e);
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean isConnected) {
            AppCompatActivity activity = contextReference.get();
            if (activity != null) {
                if (!isConnected) {
                    Toast.makeText(activity, R.string.connection_failed_lm_studio, Toast.LENGTH_LONG).show();
                    activity.finish();
                } else {
                    Toast.makeText(activity, R.string.connected_to_lm_studio, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }


    private void sendMessageToAI(String message) {
        responseFromAI.setText(R.string.waiting_for_ai_response);
        new SendMessageTask(ChatUIactivity.this).execute(message);
    }

    private static class SendMessageTask extends AsyncTask<String, String, Void> {
        private StringBuilder fullResponse = new StringBuilder();
        private ChatUIactivity activity;

        public SendMessageTask(ChatUIactivity activity) {
            this.activity = activity;
        }


        @Override
        protected Void doInBackground(String... messages) {
            String message = messages[0];
            String apiUrl = "http://" + activity.ipAddress + "/v1/chat/completions";

            try {
                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.setDoInput(true);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(30000);

                JSONObject jsonPayload = new JSONObject();
                jsonPayload.put("model", "deepseek-coder-v2-lite-instruct.gguf");
                JSONObject messageObject = new JSONObject();
                messageObject.put("role", "user");
                messageObject.put("content", message);

                JSONArray messagesArray = new JSONArray();
                messagesArray.put(messageObject);
                jsonPayload.put("messages", messagesArray);

                jsonPayload.put("temperature", 0.7);
                jsonPayload.put("max_tokens", -1);
                jsonPayload.put("stream", true);

                Log.d("SendMessageTask", "JSON Payload: " + jsonPayload.toString());

                OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
                writer.write(jsonPayload.toString());
                writer.flush();
                writer.close();

                int responseCode = connection.getResponseCode();
                Log.d("SendMessageTask", "Response Code: " + responseCode);

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    InputStream inputStream = connection.getInputStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    fullResponse.setLength(0);
                    while ((line = reader.readLine()) != null) {
                        if (line.startsWith("data: ")) {
                            String jsonChunk = line.substring(6);
                            if (jsonChunk.trim().equals("[DONE]")) {
                                Log.d("SendMessageTask", "Stream ended.");
                                break;
                            }
                            try {
                                JSONObject chunkData = new JSONObject(new JSONTokener(jsonChunk));
                                String content = chunkData.getJSONArray("choices").getJSONObject(0).getJSONObject("delta").optString("content", "");
                                if (!content.isEmpty()) {
                                    fullResponse.append(content);
                                    publishProgress(fullResponse.toString());
                                }
                            } catch (org.json.JSONException e) {
                                Log.e("SendMessageTask", "Error parsing JSON chunk: ", e);
                                Log.e("SendMessageTask", "Chunk data: " + jsonChunk);
                            }
                        } else if (line.trim().isEmpty()) {
                        } else {
                            Log.w("SendMessageTask", "Unexpected line format: " + line);
                        }
                    }
                    reader.close();
                    inputStream.close();
                } else {
                    InputStream errorStream = connection.getErrorStream();
                    BufferedReader errorReader = new BufferedReader(new InputStreamReader(errorStream));
                    StringBuilder errorResult = new StringBuilder();
                    String errorLine;
                    while ((errorLine = errorReader.readLine()) != null) {
                        errorResult.append(errorLine);
                    }
                    errorReader.close();
                    errorStream.close();
                    String errorMessage = "Error: HTTP " + responseCode + " - " + errorResult;
                    Log.e("SendMessageTask", "HTTP Error Response: " + responseCode + " " + errorResult);
                    publishProgress(errorMessage);
                }
                connection.disconnect();


            } catch (MalformedURLException e) {
                Log.e("SendMessageTask", "URL tidak valid", e);
                publishProgress(activity.getString(R.string.error_invalid_url));
            } catch (IOException e) {
                Log.e("SendMessageTask", "Gagal mengirim pesan atau menerima respon", e);
                publishProgress(activity.getString(R.string.error_connection_failed) + e.getMessage());
            } catch (org.json.JSONException e) {
                Log.e("SendMessageTask", "Error membuat JSON payload", e);
                publishProgress(activity.getString(R.string.error_json_payload));
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(String... values) {
            String currentResponse = values[0];
            activity.responseFromAI.setText(currentResponse);
        }


        @Override
        protected void onPostExecute(Void result) {
            if (activity.responseFromAI.getText().toString().startsWith("Error:")) {
                Toast.makeText(activity, R.string.ai_response_failed_toast, Toast.LENGTH_LONG).show();
            } else if (activity.responseFromAI.getText().toString().equals(activity.getString(R.string.waiting_for_ai_response))) {
                activity.responseFromAI.setText(R.string.no_ai_response);
            }
        }


        @Override
        protected void onCancelled() {
            super.onCancelled();
            Toast.makeText(activity, R.string.message_cancelled, Toast.LENGTH_SHORT).show();
        }
    }
}
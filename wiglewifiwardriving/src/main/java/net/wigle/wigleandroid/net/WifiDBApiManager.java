package net.wigle.wigleandroid.net;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import net.wigle.wigleandroid.background.BackgroundGuiHandler;
import net.wigle.wigleandroid.background.CountingRequestBody;
import net.wigle.wigleandroid.model.api.UploadReseponse;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class WifiDBApiManager {
    private static final int LOCAL_FAILURE_CODE = 999;
    private final OkHttpClient client;
    private final Context context;

    public WifiDBApiManager(Context context) {
        this.client = new OkHttpClient();
        this.context = context;
    }

    public void uploadToWifiDB(@NotNull final String filename,
                               @NotNull final Map<String, String> params,
                               final Handler handler,
                               @NotNull final RequestCompletedListener<UploadReseponse,
            JSONObject> completedListener) {
        final SharedPreferences prefs = context.getSharedPreferences(net.wigle.wigleandroid.util.PreferenceKeys.SHARED_PREFS, 0);
        final String url = prefs.getString(net.wigle.wigleandroid.util.PreferenceKeys.PREF_WIFIDB_URL, "");
        if (url == null || url.isEmpty()) {
            completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
            return;
        }

        MultipartBody.Builder builder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", new File(filename).getName(),
                        RequestBody.create(new File(filename), MediaType.parse("text/csv")));

        // add configured username/apikey if present
        final String wuser = prefs.getString(net.wigle.wigleandroid.util.PreferenceKeys.PREF_WIFIDB_USERNAME, "");
        final String wapikey = prefs.getString(net.wigle.wigleandroid.util.PreferenceKeys.PREF_WIFIDB_APIKEY, "");
        if (wuser != null && !wuser.isEmpty()) builder.addFormDataPart("username", wuser);
        if (wapikey != null && !wapikey.isEmpty()) builder.addFormDataPart("apikey", wapikey);

        // add user-supplied params
        if (!params.isEmpty()) {
            for ( Map.Entry<String, String> entry : params.entrySet() ) {
                builder.addFormDataPart(entry.getKey(), entry.getValue());
            }
        }

        MultipartBody requestBody = builder.build();
        CountingRequestBody countingBody
                = new CountingRequestBody(requestBody, (bytesWritten, contentLength) -> {
            int progress = (int)((bytesWritten*1000) / contentLength );
            Logging.info("progress: "+ progress + "("+bytesWritten +" /"+contentLength+")");
            if ( handler != null && progress >= 0 ) {
                handler.sendEmptyMessage( BackgroundGuiHandler.WRITING_PERCENT_START + progress );
            }
        });

        Request request = new Request.Builder()
                .url(url)
                .post(countingBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Logging.error("Failed to upload file to WifiDB: " + response.code() + " " + response.message());
                    completedListener.onTaskFailed(response.code(), null);
                } else {
                    if (null != response.body()) {
                        try (ResponseBody responseBody = response.body()) {
                            final String responseBodyString = responseBody.string();
                            UploadReseponse r =  new Gson().fromJson(responseBodyString,
                                    UploadReseponse.class);
                            completedListener.onTaskSucceeded(r);
                        } catch (JsonSyntaxException e) {
                            completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                        }
                    } else {
                        completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (null != e) {
                    Logging.error("Failed to upload to WifiDB - client exception: "+ e.getClass() + " - " + e.getMessage());
                } else {
                    Logging.error("Failed to upload to WifiDB - client call failed.");
                }
                completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
            }
        });
    }

    public void getSchedule(final RequestCompletedListener<String, JSONObject> completedListener) {
        final SharedPreferences prefs = context.getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
        final String url = prefs.getString(PreferenceKeys.PREF_WIFIDB_URL, "");
        if (url == null || url.isEmpty()) {
            completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
            return;
        }

        Request request = new Request.Builder()
                .url(url.replace("import.php", "schedule.php") + "?func=waiting")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Logging.error("Failed to get schedule from WifiDB: " + response.code() + " " + response.message());
                    completedListener.onTaskFailed(response.code(), null);
                } else {
                    if (null != response.body()) {
                        try (ResponseBody responseBody = response.body()) {
                            completedListener.onTaskSucceeded(responseBody.string());
                        }
                    } else {
                        completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (null != e) {
                    Logging.error("Failed to get schedule from WifiDB - client exception: "+ e.getClass() + " - " + e.getMessage());
                } else {
                    Logging.error("Failed to get schedule from WifiDB - client call failed.");
                }
                completedListener.onTaskFailed(LOCAL_FAILURE_CODE, null);
            }
        });
    }
}

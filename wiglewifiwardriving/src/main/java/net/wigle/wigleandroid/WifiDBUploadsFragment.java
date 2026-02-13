package net.wigle.wigleandroid;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import net.wigle.wigleandroid.background.BackgroundGuiHandler;
import net.wigle.wigleandroid.background.ObservationUploader;
import net.wigle.wigleandroid.model.api.UploadReseponse;
import net.wigle.wigleandroid.net.RequestCompletedListener;
import net.wigle.wigleandroid.net.WifiDBApiManager;
import net.wigle.wigleandroid.util.Logging;
import net.wigle.wigleandroid.util.PreferenceKeys;

import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class WifiDBUploadsFragment extends Fragment {

    private WifiDBApiManager wifiDBApiManager;
    private TextView scheduleDetails;
    private BroadcastReceiver fileReadyReceiver;
    private boolean clearAfterThisUpload = false;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_wifidb_uploads, container, false);

        wifiDBApiManager = new WifiDBApiManager(getContext());

        Button uploadButton = view.findViewById(R.id.button_upload);
        uploadButton.setOnClickListener(v -> startWriteAndUpload(false));

        Button uploadAndClearButton = view.findViewById(R.id.button_upload_and_clear);
        uploadAndClearButton.setOnClickListener(v -> startWriteAndUpload(true));

        scheduleDetails = view.findViewById(R.id.text_schedule_details);
        getSchedule();

        fileReadyReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) return;
                final String filename = intent.getStringExtra(BackgroundGuiHandler.FILENAME);
                final String filepath = intent.getStringExtra(BackgroundGuiHandler.FILEPATH);
                if (filename != null) {
                    String absPath;
                    if (filepath != null) {
                        absPath = filepath + filename;
                    } else {
                        File f = getActivity().getFileStreamPath(filename);
                        absPath = f != null ? f.getAbsolutePath() : filename;
                    }
                    scheduleDetails.setText(getString(R.string.upload_file_ready_toast) + filename + "\nUploading...");
                    uploadFileToWifiDB(absPath);
                }
            }
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getActivity().registerReceiver(fileReadyReceiver, new IntentFilter("net.wigle.wigleandroid.FILE_READY"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            getActivity().registerReceiver(fileReadyReceiver, new IntentFilter("net.wigle.wigleandroid.FILE_READY"));
        }


        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            getActivity().unregisterReceiver(fileReadyReceiver);
        } catch (Exception ignored) {}
    }

    private void startWriteAndUpload(final boolean clearAfter) {
        this.clearAfterThisUpload = clearAfter;
        // start an ObservationUploader that only writes the current run file (justWriteFile=true, writeRun=true)
        try {
            final SharedPreferences prefs = getActivity().getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
            final String uploadPath = prefs.getString(PreferenceKeys.PREF_WIFIDB_UPLOAD_FOLDER,
                    Environment.getExternalStorageDirectory().getAbsolutePath() + "/wifidb");
            final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
            final String filename = "WigleWifi_" + sdf.format(new Date()) + ".csv";
            final ObservationUploader ou = new ObservationUploader(getActivity(), ListFragment.lameStatic.dbHelper, null, true, false, true, uploadPath, filename);
            ou.startDownload(null);
            scheduleDetails.setText(R.string.upload_preparing_toast);
        } catch (Exception ex) {
            Logging.error("Failed to start export: ", ex);
            scheduleDetails.setText(R.string.upload_export_fail_toast);
        }
    }

    private void uploadFileToWifiDB(final String absPath) {
        final Map<String,String> params = new HashMap<>();
        wifiDBApiManager.uploadToWifiDB(absPath, params, new Handler(Looper.getMainLooper()), new RequestCompletedListener<UploadReseponse, JSONObject>() {
            @Override
            public void onTaskSucceeded(UploadReseponse response) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), R.string.upload_successful_toast, Toast.LENGTH_SHORT).show();
                        scheduleDetails.setText(R.string.upload_successful_toast);
                    });
                }
                if (clearAfterThisUpload) {
                    try {
                        ListFragment.lameStatic.dbHelper.clearDatabase();
                        final SharedPreferences prefs = getActivity().getSharedPreferences(PreferenceKeys.SHARED_PREFS, 0);
                        final SharedPreferences.Editor editor = prefs.edit();
                        editor.putLong(PreferenceKeys.PREF_DB_MARKER, 0L);
                        editor.apply();
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> scheduleDetails.append("\n" + getString(R.string.upload_cleared_toast)));
                        }
                    } catch (Exception ex) {
                        Logging.error("Failed to clear DB after upload: ", ex);
                    }
                }
            }

            @Override
            public void onTaskFailed(int status, JSONObject error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        Toast.makeText(getContext(), R.string.upload_failed_toast, Toast.LENGTH_SHORT).show();
                        scheduleDetails.setText(getString(R.string.upload_failed_toast) + ": " + status);
                    });
                }
            }

            @Override
            public void onTaskCompleted() {
                //
            }
        });
    }

    private void getSchedule() {
        wifiDBApiManager.getSchedule(new RequestCompletedListener<String, JSONObject>() {
            @Override
            public void onTaskSucceeded(String response) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> scheduleDetails.setText(response));
                }
            }

            @Override
            public void onTaskFailed(int status, JSONObject error) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> scheduleDetails.setText(R.string.uploads_no_schedule));
                }
            }

            @Override
            public void onTaskCompleted() {
                //
            }
        });
    }
}

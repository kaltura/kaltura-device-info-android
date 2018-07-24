package com.kaltura.kalturadeviceinfo;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaDrm;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;


public class MainActivity extends AppCompatActivity {

    String report;

    private void showReport(String report) {
        TextView reportView = (TextView) findViewById(R.id.textView);
        assert reportView != null;
        reportView.setText(report);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Collect data
        new CollectorTask().execute(false);

        FloatingActionButton fab = findViewById(R.id.fab);
        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            
            @Override
            public void onClick(View view) {
                showActionsDialog();
            }
        });

    }

    private void showActionsDialog() {
        String[] actions = {
                "Share...",
                "Refresh",
//                "Refresh with SafetyNet",
                "Provision Widevine"
        };
        new AlertDialog.Builder(this).setTitle("Select action").setItems(actions, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        shareReport();
                        break;
                    case 1:
                        new CollectorTask().execute(false);
                        break;
                    case 2:
//                        new CollectorTask().execute(true);
//                        break;
//                    case 3:
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                            new AlertDialog.Builder(MainActivity.this).setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    startProvision();
                                }
                            }).setNegativeButton("No", null).setMessage("Are you sure you want to attempt Widevine Provisioning?").show();
                        }
                        break;
                }
            }
        }).show();
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    private void startProvision() {
        new ProvisionTask(this).execute();
    }

    private void provisionFailed(Exception e) {
    }

    private void provisionSuccessful() {
    }

    private void shareReport() {
        String subject = "Kaltura Device Info - Report" + Build.BRAND + "/" + Build.MODEL + "/" + Build.VERSION.RELEASE + "/" + Build.VERSION.SDK_INT;
        Intent shareIntent = intentWithText(subject, report);
        startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.send_to)));
    }

    private Intent intentWithAttachment(String subject, String report) {
        File reportsDir = new File(getFilesDir(), "reports");
        reportsDir.mkdirs();
        File reportFile = new File(reportsDir, "report.json");
        try {
            FileWriter writer = new FileWriter(reportFile);
            writer.write(report);
            writer.close();
        } catch (IOException e) {
            Log.e("ERROR", "Error creating report file", e);
            return null;
        }
        Uri fileUri = FileProvider.getUriForFile(MainActivity.this, "com.kaltura.kalturadeviceinfo.fileprovider", reportFile);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("application/json");
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, subject);

        PackageManager packageManager = getPackageManager();
        if (shareIntent.resolveActivity(packageManager) == null) {
            return null;
        }
        
        return shareIntent;
    }

    private Intent intentWithText(String subject, String report) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, report);
        sendIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        sendIntent.setType("text/plain");
        return sendIntent;
    }

    private class CollectorTask extends AsyncTask<Boolean, Void, String> {

        @Override
        protected String doInBackground(Boolean... params) {
            return Collector.getReport(MainActivity.this, params[0]);
        }

        @Override
        protected void onPostExecute(String jsonString) {
            report = jsonString;
            showReport(jsonString);
            File output = new File(getExternalFilesDir(null), "report.json");
            try {
                FileWriter writer;
                writer = new FileWriter(output);
                writer.write(report);
                writer.close();
                Toast.makeText(MainActivity.this, "Wrote report to " + output, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(MainActivity.this, "Failed writing report: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private static class ProvisionTask extends AsyncTask<Context, Void, String> {

        private final Context context;

        public ProvisionTask(Context context) {
            this.context = context;
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        @Override
        protected String doInBackground(Context... contexts) {
            try {
                provisionWidevine();
                return null;
            } catch (Exception e) {
                return e.toString();
            }
        }

        @Override
        protected void onPostExecute(String s) {
            if (s == null) {
                Toast.makeText(context, "Provision Successful", Toast.LENGTH_LONG).show();
            } else {
                new AlertDialog.Builder(context).setTitle("Provision Failed").setMessage(s).show();
            }
        }

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
        private void provisionWidevine() throws Exception {
            MediaDrm mediaDrm = new MediaDrm(Collector.WIDEVINE_UUID);
            MediaDrm.ProvisionRequest provisionRequest = mediaDrm.getProvisionRequest();
            String url = provisionRequest.getDefaultUrl() + "&signedRequest=" + new String(provisionRequest.getData());

            // send as empty post
            final HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
            con.setRequestMethod("POST");
            con.setDoOutput(true);
            int responseCode = con.getResponseCode();
            if (responseCode >= 300) {
                throw new Exception("Bad response code " + responseCode);
            }
            BufferedInputStream bis = new BufferedInputStream(con.getInputStream());
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();

            int b;
            while ((b = bis.read()) >= 0) {
                baos.write(b);
            }
            bis.close();

            final byte[] response = baos.toByteArray();
            Log.d("RESULT", Base64.encodeToString(response, Base64.NO_WRAP));
            baos.close();
            
            mediaDrm.provideProvisionResponse(response);
            mediaDrm.release();
        }
    }
}

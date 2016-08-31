package com.kaltura.kalturadeviceinfo;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


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
        
        
        new AsyncTask<Void, Void, String>() {

            @Override
            protected String doInBackground(Void... params) {
                final JSONObject info = new Collector(MainActivity.this).collect();

                Log.d("JSON", info.toString());

                String jsonString;
                try {
                    jsonString = info.toString(4);
                } catch (JSONException e) {
                    jsonString = "{}";
                }
                
                return jsonString;
            }

            @Override
            protected void onPostExecute(String jsonString) {
                report = jsonString;
                showReport(jsonString);
            }
        }.execute();

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        assert fab != null;
        fab.setOnClickListener(new View.OnClickListener() {
            
            @Override
            public void onClick(View view) {

                String subject = "Kaltura Device Info - " + Build.BRAND + "/" + Build.MODEL + "/" + Build.VERSION.RELEASE + "/" + Build.VERSION.SDK_INT;
                Intent shareIntent = intentWithAttachment(subject, report);
                if (shareIntent == null) {
                    Toast.makeText(MainActivity.this, "Can't share report as attachment", Toast.LENGTH_SHORT).show();
                    shareIntent = intentWithText(subject, report);
                }

                startActivity(Intent.createChooser(shareIntent, getResources().getText(R.string.send_to)));
            }
        });

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
}

package com.kaltura.kalturadeviceinfo;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;


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
                Intent sendIntent = new Intent();
                sendIntent.setAction(Intent.ACTION_SEND);
                sendIntent.putExtra(Intent.EXTRA_TEXT, report);
                sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Kaltura Device Info - " + Build.BRAND + "/" + Build.MODEL + "/" + Build.VERSION.RELEASE + "/" + Build.VERSION.SDK_INT);
                sendIntent.setType("application/json");
                startActivity(Intent.createChooser(sendIntent, getResources().getText(R.string.send_to)));
            }
        });

    }
}

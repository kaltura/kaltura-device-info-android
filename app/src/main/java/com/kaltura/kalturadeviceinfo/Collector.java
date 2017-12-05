package com.kaltura.kalturadeviceinfo;

import android.annotation.TargetApi;
import android.content.Context;
import android.drm.DrmInfo;
import android.drm.DrmInfoRequest;
import android.drm.DrmManagerClient;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaDrm;
import android.media.UnsupportedSchemeException;
import android.os.Build;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.safetynet.SafetyNet;
import com.google.android.gms.safetynet.SafetyNetApi;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Random;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


/**
 * Created by noamt on 17/05/2016.
 */
class Collector {
    private static final String TAG = "Collector";
    static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
    private final Context mContext;
    private final boolean includeSafetyNet;
    private final JSONObject mRoot = new JSONObject();
    
    private static String sReport;

    static String getReport(Context ctx, boolean includeSafetyNet) {
        sReport = null;
        if (sReport == null) {
            Collector collector = new Collector(ctx, includeSafetyNet);
            JSONObject jsonReport = collector.collect();

            try {
                sReport = jsonReport.toString(4);
                sReport = sReport.replace("\\/", "/");
                
            } catch (JSONException e) {
                sReport = "{}";
            }
        }

        return sReport;
    }
    
    Collector(Context context, boolean includeSafetyNet) {
        mContext = context;
        this.includeSafetyNet = includeSafetyNet;
    }
    
    JSONObject collect() {
        final JSONObject[] safetyNetResult = new JSONObject[1];
        Thread safetyNetThread = new Thread() {
            @Override
            public void run() {
                try {
                    safetyNetResult[0]=collectSafetyNet();
                } catch (JSONException e) {
                    Log.e(TAG, "Failed converting safetyNet response to JSON");
                }
            }
        };
        
        if (includeSafetyNet) {
            safetyNetThread.start();
        }
        try {
            JSONObject root = mRoot;
            root.put("meta", meta());
            root.put("system", systemInfo());
            root.put("drm", drmInfo());
            root.put("display", displayInfo());
            root.put("media", mediaCodecInfo());
            root.put("root", rootInfo());
            
            if (includeSafetyNet) {
                safetyNetThread.join(20 * 1000);
                root.put("safetyNet", safetyNetResult[0]);
            }
            
        } catch (JSONException e) {
            Log.e(TAG, "Error");
        } catch (InterruptedException e) {
            Log.d(TAG, "Interrupted");
        }
        return mRoot;
    }

    private JSONObject meta() throws JSONException {
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        df.setTimeZone(tz);
        String nowAsISO = df.format(new Date());
        return new JSONObject()
                .put("versionName", BuildConfig.VERSION_NAME)
                .put("versionCode", BuildConfig.VERSION_CODE)
                .put("timestamp", nowAsISO);
    }

    private JSONObject displayInfo() throws JSONException {
        return new JSONObject().put("metrics", mContext.getResources().getDisplayMetrics().toString());
    }

    private JSONObject drmInfo() throws JSONException {
        return new JSONObject()
                .put("modular", modularDrmInfo())
                .put("classic", classicDrmInfo());
                
    }

    private JSONObject classicDrmInfo() throws JSONException {
        JSONObject json = new JSONObject();
        
        DrmManagerClient drmManagerClient = new DrmManagerClient(mContext);
        String[] availableDrmEngines = drmManagerClient.getAvailableDrmEngines();

        JSONArray engines = jsonArray(availableDrmEngines);
        json.put("engines", engines);
        
        try {
            if (drmManagerClient.canHandle("", "video/wvm")) {
                DrmInfoRequest request = new DrmInfoRequest(DrmInfoRequest.TYPE_REGISTRATION_INFO, "video/wvm");
                request.put("WVPortalKey", "OEM");
                DrmInfo response = drmManagerClient.acquireDrmInfo(request);
                String status = (String) response.get("WVDrmInfoRequestStatusKey");
                
                status = new String[]{"HD_SD", null, "SD"}[Integer.parseInt(status)];
                json.put("widevine", 
                        new JSONObject()
                                .put("version", response.get("WVDrmInfoRequestVersionKey"))
                                .put("status", status)
                );
            }
        } catch (Exception e) {
            json.put("error", e.getMessage() + '\n' + Log.getStackTraceString(e));
        }

        drmManagerClient.release();
        
        return json;
    }

    @NonNull
    private JSONArray jsonArray(String[] stringArray) {
        JSONArray jsonArray = new JSONArray();
        for (String string : stringArray) {
            if (!TextUtils.isEmpty(string)) {
                jsonArray.put(string);
            }
        }
        return jsonArray;
    }

    private JSONObject mediaCodecInfo(MediaCodecInfo mediaCodec) throws JSONException {
        return new JSONObject()
//                .put("isEncoder", mediaCodec.isEncoder())
                .put("supportedTypes", jsonArray(mediaCodec.getSupportedTypes()));
    }
    
    private JSONObject mediaCodecInfo() throws JSONException {

        ArrayList<MediaCodecInfo> mediaCodecs = new ArrayList<>();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
            MediaCodecInfo[] codecInfos = mediaCodecList.getCodecInfos();
            
            Collections.addAll(mediaCodecs, codecInfos);
        } else {
            for (int i=0, n=MediaCodecList.getCodecCount(); i<n; i++) {
                mediaCodecs.add(MediaCodecList.getCodecInfoAt(i));
            }
        }
        
        ArrayList<MediaCodecInfo> encoders = new ArrayList<>();
        ArrayList<MediaCodecInfo> decoders = new ArrayList<>();
        for (MediaCodecInfo mediaCodec : mediaCodecs) {
            if (mediaCodec.isEncoder()) {
                encoders.add(mediaCodec); 
            } else {
                decoders.add(mediaCodec);
            }
        }

        JSONObject info = new JSONObject();
        JSONObject jsonDecoders = new JSONObject();
        for (MediaCodecInfo mediaCodec : decoders) {
            jsonDecoders.put(mediaCodec.getName(), mediaCodecInfo(mediaCodec));
        }
        info.put("decoders", jsonDecoders);
        
        return info;
        
    }

    private JSONObject modularDrmInfo() throws JSONException {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            return new JSONObject()
                    .put("widevine", widevineModularDrmInfo());
        } else {
            return null;
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    private JSONObject widevineModularDrmInfo() throws JSONException {
        if (!MediaDrm.isCryptoSchemeSupported(WIDEVINE_UUID)) {
            return null;
        }

        MediaDrm mediaDrm;
        try {
            mediaDrm = new MediaDrm(WIDEVINE_UUID);
        } catch (UnsupportedSchemeException e) {
            return null;
        }
        
        final JSONArray mediaDrmEvents = new JSONArray();
        
        mediaDrm.setOnEventListener(new MediaDrm.OnEventListener() {
            @Override
            public void onEvent(@NonNull MediaDrm md, byte[] sessionId, int event, int extra, byte[] data) {
                try {
                    String encodedData = data == null ? null : Base64.encodeToString(data, Base64.NO_WRAP);
                    
                    mediaDrmEvents.put(new JSONObject().put("event", event).put("extra", extra).put("data", encodedData));
                } catch (JSONException e) {
                    Log.e(TAG, "JSONError", e);
                }
            }
        });

        try {
            byte[] session;
            session = mediaDrm.openSession();
            mediaDrm.closeSession(session);
        } catch (Exception e) {
            mediaDrmEvents.put(new JSONObject().put("Exception(openSession)", e.toString()));
        }


        String[] stringProps = {MediaDrm.PROPERTY_VENDOR, MediaDrm.PROPERTY_VERSION, MediaDrm.PROPERTY_DESCRIPTION, MediaDrm.PROPERTY_ALGORITHMS, "securityLevel", "systemId", "privacyMode", "sessionSharing", "usageReportingSupport", "appId", "origin", "hdcpLevel", "maxHdcpLevel", "maxNumberOfSessions", "numberOfOpenSessions"};
        String[] byteArrayProps = {MediaDrm.PROPERTY_DEVICE_UNIQUE_ID, "provisioningUniqueId", "serviceCertificate"};
        
        JSONObject props = new JSONObject();
        
        for (String prop : stringProps) {
            String value;
            try {
                value = mediaDrm.getPropertyString(prop);
            } catch (IllegalStateException e) {
                value = "<unknown>";
            }
            props.put(prop, value);
        }
        for (String prop : byteArrayProps) {
            String value;
            try {
                value = Base64.encodeToString(mediaDrm.getPropertyByteArray(prop), Base64.NO_WRAP);
            } catch (IllegalStateException|NullPointerException e) {
                value = "<unknown>";
            }
            props.put(prop, value);
        }

        JSONObject response = new JSONObject();
        response.put("properties", props);
        response.put("events", mediaDrmEvents);

        return response;
    }
    
    private JSONObject systemInfo() throws JSONException {
        JSONObject arch = new JSONObject().put("os.arch", System.getProperty("os.arch"));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            arch.put("SUPPORTED_ABIS", new JSONArray(Build.SUPPORTED_ABIS));
            arch.put("SUPPORTED_32_BIT_ABIS", new JSONArray(Build.SUPPORTED_32_BIT_ABIS));
            arch.put("SUPPORTED_64_BIT_ABIS", new JSONArray(Build.SUPPORTED_64_BIT_ABIS));
        }
        return new JSONObject()
                .put("RELEASE", Build.VERSION.RELEASE)
                .put("SDK_INT", Build.VERSION.SDK_INT)
                .put("BRAND", Build.BRAND)
                .put("MODEL", Build.MODEL)
                .put("MANUFACTURER", Build.MANUFACTURER)
                .put("TAGS", Build.TAGS)
                .put("FINGERPRINT", Build.FINGERPRINT)
                .put("ARCH", arch);
    }
    
    private JSONObject rootInfo() throws JSONException {

        JSONObject info = new JSONObject();

        String[] paths = { "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su", "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su", "/system/sd/xbin/su",
                "/system/bin/failsafe/su", "/data/local/su" };
        JSONArray files = new JSONArray();
        for (String path : paths) {
            if (new File(path).exists()) {
                files.put(path);
            }
        }
        info.put("existingFiles", files);

        return info;
    }

    // NOTE: this application is meant for user-initiated diagnostics. 
    // It doesn't attempt to use the best security practices or to validate the result. 
    private JSONObject collectSafetyNet() throws JSONException {
        GoogleApiClient client = new GoogleApiClient.Builder(mContext)
                .addApi(SafetyNet.API)
                .build();
        ConnectionResult connectionResult = client.blockingConnect(20, TimeUnit.SECONDS);
        if (!connectionResult.isSuccess()) {
            return new JSONObject().put("connectionError", connectionResult.toString());
        }
        byte[] nonce = getRequestNonce(); 
        SafetyNetApi.AttestationResult result = SafetyNet.SafetyNetApi.attest(client, nonce).await(20, TimeUnit.SECONDS);
                        Status status = result.getStatus();
        if (!status.isSuccess()) {
            return new JSONObject().put("testingError", status.toString());
        }
        String jwsResult = result.getJwsResult();
        
        // Extract the payload, ignore the rest.
        String[] parts = jwsResult.split("\\.");
        if (parts.length != 3) {
            return new JSONObject().put("invalidResponse", jwsResult);
        }
        
        String decoded = new String(Base64.decode(parts[1], Base64.URL_SAFE));
        
        JSONObject jsonObject = new JSONObject(decoded);

        // Remove the boring keys
        for (String key : new String[]{"nonce", "timestampMs", "apkPackageName", "apkCertificateDigestSha256", "apkDigestSha256"}) {
            jsonObject.remove(key);
        }

        return jsonObject;
    }

    private byte[] getRequestNonce() {
        byte[] bytes = new byte[32];
        new Random().nextBytes(bytes);
        return bytes;
    }
}


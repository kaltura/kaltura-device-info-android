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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

/**
 * Created by noamt on 17/05/2016.
 */
class Collector {
    private static final String TAG = "Collector";
    public static final UUID WIDEVINE_UUID = new UUID(0xEDEF8BA979D64ACEL, 0xA3C827DCD51D21EDL);
    private final Context mContext;

    Collector(Context context) {
        mContext = context;
    }
    
    JSONObject collect() {
        JSONObject root = new JSONObject();
        try {
            root.put("build", buildInfo());
            root.put("drm", drmInfo());
            root.put("display", displayInfo());
            root.put("media", mediaCodecInfo());
        } catch (JSONException e) {
            Log.e(TAG, "Error");
        }
        return root;
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
                request.put("WVPortalKey", "kaltura");
                DrmInfo response = drmManagerClient.acquireDrmInfo(request);
                json.put("widevine", response.get("WVDrmInfoRequestVersionKey"));
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
            } catch (IllegalStateException e) {
                value = "<unknown>";
            }
            props.put(prop, value);
        }
        
        return props;
    }
    
    private JSONObject buildInfo() throws JSONException {
        return new JSONObject()
                .put("RELEASE", Build.VERSION.RELEASE)
                .put("SDK_INT", Build.VERSION.SDK_INT)
                .put("BRAND", Build.BRAND)
                .put("MODEL", Build.MODEL)
                .put("MANUFACTURER", Build.MANUFACTURER)
                .put("FINGERPRINT", Build.FINGERPRINT);
    }
}

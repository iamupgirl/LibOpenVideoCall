package io.agora.propeller;

import io.agora.rtc.RtcEngine;

public class Constant {

    public static final String MEDIA_SDK_VERSION;
    public static final String IS_CHATTING = "IS_CHATTING";
    public static final String TOKEN = "TOKEN";
    public static final String APP_ID = "APP_ID";
    public static final String GET_TOKEN_URL = "GET_TOKEN_URL";
    public static final String MY_UID = "MY_UID";

    static {
        String sdk = "undefined";
        try {
            sdk = RtcEngine.getSdkVersion();
        } catch (Throwable e) {
        }
        MEDIA_SDK_VERSION = sdk;
    }

    public static boolean PRP_ENABLED = true;
    public static float PRP_DEFAULT_LIGHTNESS = 1.1f;
    public static int PRP_DEFAULT_SMOOTHNESS = 12;
    public static final float PRP_MAX_LIGHTNESS = 1.5f;
    public static final int PRP_MAX_SMOOTHNESS = 15;

    public static boolean SHOW_VIDEO_INFO = true;
}

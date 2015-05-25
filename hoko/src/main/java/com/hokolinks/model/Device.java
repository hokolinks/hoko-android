package com.hokolinks.model;

import android.Manifest;
import android.content.Context;
import android.graphics.Point;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.telephony.TelephonyManager;
import android.view.Display;
import android.view.WindowManager;

import com.hokolinks.utils.DateUtils;
import com.hokolinks.utils.Utils;
import com.hokolinks.utils.log.HokoLog;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Date;
import java.util.Locale;

/**
 * Device is a helper class to get all the necessary information of the user's Device.
 */
public class Device {

    // Platform name
    private static final String PLATFORM = "Android";

    // Shared preferences keys
    private static final String UUID_KEY = "UUID";

    // String values for connectivity state
    private static final String CONNECTIVITY_WIFI = "Wifi";
    private static final String CONNECTIVITY_CELLULAR = "Cellular";
    private static final String CONNECTIVITY_NO_CONNECTIVITY = "No Connectivity";
    private static final String CONNECTIVITY_NO_PERMISSION = "No Permission";

    /**
     * Returns the vendor of the device Hoko is being run on.
     *
     * @return The vendor of the device.
     */
    public static String getVendor() {
        return android.os.Build.MANUFACTURER;
    }

    /**
     * Returns the Android platform.
     *
     * @return The Android platform.
     */
    public static String getPlatform() {
        return PLATFORM;
    }

    /**
     * Returns the model of the device Hoko is being run on.
     *
     * @return The model of the device.
     */
    public static String getModel() {
        return android.os.Build.MODEL;
    }

    /**
     * Returns the Android version of the device Hoko is being run on.
     *
     * @return The Android version.
     */
    public static String getSystemVersion() {
        return String.valueOf(android.os.Build.VERSION.SDK_INT);
    }

    /**
     * Returns the system language of the device Hoko is being run on.
     *
     * @return The system language of the device.
     */
    public static String getSystemLanguage() {
        return Locale.getDefault().getLanguage();
    }

    /**
     * Returns the system locale of the device Hoko is being run on.
     *
     * @return The system locale of the device.
     */
    public static String getLocale() {
        return Locale.getDefault().toString();
    }

    /**
     * Returns the screen size of the device Hoko is being run on.
     *
     * @param context A context object.
     * @return The screen size of the device.
     */
    public static String getScreenSize(Context context) {
        try {
            WindowManager windowManager = (WindowManager) context
                    .getSystemService(Context.WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            int width = size.x;
            int height = size.y;
            return height + "x" + width;
        } catch (Exception e) {
            return "0x0";
        }
    }

    /**
     * Returns the carrier network of the device Hoko is being run on.
     *
     * @param context A context object.
     * @return The carrier network of the device.
     */
    public static String getCarrier(Context context) {
        try {
            TelephonyManager telephonyManager = ((TelephonyManager) context
                    .getSystemService(Context.TELEPHONY_SERVICE));
            return telephonyManager.getNetworkOperatorName();
        } catch (Exception e) {
            return "Unknown Carrier";
        }
    }

    /**
     * Returns the current internet connectivity of the device Hoko is being run on.
     *
     * @param context A context object.
     * @return The current internet connectivity of the device.
     */
    public static String getInternetConnectivity(Context context) {
        try {
            if (Utils.hasPermission(Manifest.permission.ACCESS_NETWORK_STATE, context)) {
                ConnectivityManager connManager = (ConnectivityManager) context
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo mWifi = connManager
                        .getNetworkInfo(ConnectivityManager.TYPE_WIFI);
                NetworkInfo mNetwork = connManager
                        .getNetworkInfo(ConnectivityManager.TYPE_MOBILE);
                if (mWifi != null && mWifi.isConnected()) {
                    return CONNECTIVITY_WIFI;
                } else if (mNetwork != null && mNetwork.isConnected()) {
                    return CONNECTIVITY_CELLULAR;
                } else {
                    return CONNECTIVITY_NO_CONNECTIVITY;
                }
            } else {
                return CONNECTIVITY_NO_PERMISSION;
            }
        } catch (Exception e) {
            return CONNECTIVITY_NO_CONNECTIVITY;
        }
    }

    /**
     * Checks if the device has internet connectivity regardless how it is connected.
     *
     * @param context A context object.
     * @return true if the device has internet connectivity and false otherwise.
     */
    public static boolean hasInternetConnectivity(Context context) {
        String internetConnectivity = getInternetConnectivity(context);
        return internetConnectivity.equals(CONNECTIVITY_CELLULAR)
                || internetConnectivity.equals(CONNECTIVITY_WIFI);
    }

    /**
     * Returns a one-time generated device ID which is saved in the shared preferences for later
     * usage. This guarantees it is unique but will not persist when the application is reinstalled.
     *
     * @param context A context object.
     * @return The one-time generated device ID.
     */
    public static synchronized String getDeviceID(Context context) {
        String uid = Utils.getString(UUID_KEY, context);
        if (uid == null) {
            uid = Utils.generateUUID();
            Utils.saveString(uid, UUID_KEY, context);
        }
        return uid;
    }

    /**
     * Converts all the Device information into a JSONObject to be sent to the Hoko backend
     * service.
     *
     * @param context A context object.
     * @return The JSONObject representation of Device.
     */
    public static JSONObject json(Context context) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.putOpt("timestamp", DateUtils.format(new Date()));
            jsonObject.putOpt("vendor", getVendor());
            jsonObject.putOpt("platform", getPlatform());
            jsonObject.putOpt("model", getModel());
            jsonObject.putOpt("system_version", getSystemVersion());
            jsonObject.putOpt("system_language", getSystemLanguage());
            jsonObject.putOpt("locale", getLocale());
            //jsonObject.putOpt("device_name", getDeviceName()) Not available on Android
            jsonObject.putOpt("screen_size", getScreenSize(context));
            jsonObject.putOpt("carrier", getCarrier(context));
            jsonObject.putOpt("internet_connectivity", getInternetConnectivity(context));
            jsonObject.putOpt("uid", getDeviceID(context));
            jsonObject.putOpt("application", App.json(context));
            return jsonObject;
        } catch (JSONException e) {
            HokoLog.e(e);
        }
        return null;
    }

}

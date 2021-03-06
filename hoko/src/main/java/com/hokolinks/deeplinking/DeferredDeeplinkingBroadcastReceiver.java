package com.hokolinks.deeplinking;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.hokolinks.Hoko;
import com.hokolinks.utils.log.HokoLog;

import java.net.URLDecoder;


public class DeferredDeeplinkingBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String referrer = intent.getExtras().getString("referrer");
            referrer = URLDecoder.decode(referrer, "UTF-8");
            Uri uri = Uri.parse(referrer);
            HokoLog.d("Opening deferred deeplink " + uri.toString());

            Hoko.deeplinking().openDeferredURL(uri.toString());
        } catch (Exception e) {
            HokoLog.e(e);
        }


    }
}

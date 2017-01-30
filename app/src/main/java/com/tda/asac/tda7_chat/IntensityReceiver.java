package com.tda.asac.tda7_chat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import asacIntents.AsacIntents;

/**
 * Created by michele on 19/07/16.
 */

public class IntensityReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String strength = intent.getStringExtra(AsacIntents.RADIO_INTENSITY_CHANGED_STRENGTH);
        String workMode = intent.getStringExtra(AsacIntents.RADIO_INTENSITY_CHANGED_WORK_MODE);
        Log.i("IntensityReceiver", "WorkMode: " + workMode + " strength: " + strength);

    }
}
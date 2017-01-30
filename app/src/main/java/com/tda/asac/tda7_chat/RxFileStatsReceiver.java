package com.tda.asac.tda7_chat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import asacIntents.AsacIntents;

/**
 * Created by michele on 19/07/16.
 */
public class RxFileStatsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        long nBytesRx = intent.getLongExtra(AsacIntents.RX_FILE_STATS_CHANGED_NUM_OF_BYTES_RX, 0);
        long lHostFileSize = intent.getLongExtra(AsacIntents.RX_FILE_STATS_CHANGED_HOST_FILE_SIZE, 0);
        Log.i("RxFileStatsReceiver", "#bytes rx: " + nBytesRx + " host file size: " + lHostFileSize);

    }
}

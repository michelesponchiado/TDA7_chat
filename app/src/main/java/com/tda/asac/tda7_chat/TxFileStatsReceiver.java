package com.tda.asac.tda7_chat;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import asacIntents.AsacIntents;

/**
 * Created by michele on 19/07/16.
 */
public class TxFileStatsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        long nBytesTx = intent.getLongExtra(AsacIntents.TX_FILE_STATS_CHANGED_NUM_OF_BYTES_TX, 0);
        long localFileSize = intent.getLongExtra(AsacIntents.TX_FILE_STATS_CHANGED_LOCAL_FILE_SIZE, 0);
        Log.i("TxFileStatsReceiver", "#bytes tx: " + nBytesTx + " local file size: " + localFileSize);

    }
}
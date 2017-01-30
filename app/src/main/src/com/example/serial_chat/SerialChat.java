package com.example.serial_chat;

import java.io.DataOutputStream;
import java.io.File;

import asacEEPROM.Asac_EEPROM;
import asacIntents.AsacIntents;
import asacLEDs.Asac_LEDs;
import asac_radio_transport_layer.LogTransportLayer.EnumTransportLogEnable;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;

import diagnostic_thread.Diagnostic_thread;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.RadioRF;
import android.os.EEPROM;

import android.os.Bundle;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;


import android.os.Handler;
import android.os.Message;

import android.os.RemoteException;
import android.view.KeyEvent;

import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import asac_radio_module_commands.Asac_radio_module_commands; 
import asac_radio_module_commands.Asac_radio_module_commands.enum_module_reply_type;
import asac_radio_network_layer_message.Asac_radio_network_layer_message;

import asac_radio_transport_layer.AsacRadioTransportLayer;
import asac_radio_transport_layer.AsacRadioTransportLayer.EnumReceiveFileMode;
import asac_radio_transport_layer.AsacRadioTransportLayer.EnumReceivefileReturnCode;
import asac_radio_transport_layer.AsacRadioTransportLayer.EnumTransmitAndReceiveReturnCode;
import asac_radio_transport_layer.AsacRadioTransportLayer.EnumSendfileReturnCode;
//import asac_radio_transport_layer.Asac_radio_transport_layer.enum_sendfile_return_code;
import asac_radio_network_layer.AsacRadioNetworkLayer;
import asac_radio_network_layer.AsacRadioNetworkLayer.enum_rnl_message_destination_type;
import asac_radio_network_layer.AsacRadioNetworkLayer.enum_rnl_rx_return_code;
import asac_radio_network_layer.AsacRadioNetworkLayer.EnumRnlReturnCode;

import android.view.inputmethod.InputMethodManager;

import com.tda.asac.tda7_chat.R;

public class SerialChat extends Activity implements Runnable, TextView.OnEditorActionListener {

    private static final String TAG = "SerialChat";

    private TextView mLog;
    private EditText mEditText;
    
    
    
    private Asac_radio_module_commands asac_radio_module_commands;

    private static final int MESSAGE_LOG = 1; 
    private static final boolean i_receive_messages_from_radio=true;
    public boolean b_radio_exists;
    
    private AsacRadioTransportLayer asac_radio_transport_layer;
    private Diagnostic_thread diagnostic_thread;
	Asac_radio_network_layer_message msg_to_transmit;
	Asac_radio_network_layer_message msg_received;
	
	private AsacRadioNetworkLayer.AsacRadioRxStats rx_stats;
	private AsacRadioNetworkLayer.Asac_radio_tx_stats tx_stats;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		if (false)
		{
			final int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;

			getWindow().getDecorView().setSystemUiVisibility(uiOptions);
		}
		if(false)
		{
			Asac_EEPROM asac_EEPROM = new Asac_EEPROM(this);

			int sn_read = asac_EEPROM.readTDA7_SN();
			int sn_written  = 0x23325665;
			asac_EEPROM.writeTDA7_SN(sn_written);
			int sn_reread = asac_EEPROM.readTDA7_SN();
			if (sn_reread != sn_written)
			{
				int wow_error_SN = 1;
			}
			int tn_read = asac_EEPROM.readTDA7_terminalNumber();
			int tn_written = 0x1881a22a;
			asac_EEPROM.writeTDA7_terminalNumber(tn_written);
			int tn_reread = asac_EEPROM.readTDA7_terminalNumber();
			if (tn_reread != tn_written)
			{
				int wow_error_terminalNumber = 1;
			}
			byte [] byteArray;
			byteArray = new byte[256];
			// read the serial number
			try
			{
				asac_EEPROM.readTDA7_EEPROM_BYTES(Asac_EEPROM.Enum_EEPROM_area.enum_EEPROM_area_factory, 0, byteArray, 4, 0);
			}
			catch(RuntimeException e)
			{
				String s = e.toString();
			}
			int sn_read_byte = ((((byteArray[3]*256)+byteArray[2])*256+byteArray[1])*256)+byteArray[0];
			if (sn_read_byte != sn_read)
			{

			}
			// write the terminal number
			byteArray[0] = 3;
			byteArray[1] = 0;
			byteArray[2] = 0;
			byteArray[3] = 0;
			try
			{
				asac_EEPROM.writeTDA7_EEPROM_BYTES(Asac_EEPROM.Enum_EEPROM_area.enum_EEPROM_area_user, 0, byteArray, 4, 0);
			}
			catch(RuntimeException e)
			{
				String s = e.toString();
			}
			// read the terminal number
			try
			{
				asac_EEPROM.readTDA7_EEPROM_BYTES(Asac_EEPROM.Enum_EEPROM_area.enum_EEPROM_area_user, 0, byteArray, 4, 0);
			}
			catch(RuntimeException e)
			{
				String s = e.toString();
			}
			int tn_read_byte = ((((byteArray[3]*256)+byteArray[2])*256+byteArray[1])*256)+byteArray[0];
			if (tn_read_byte != tn_read)
			{

			}
		}
        if (false)
		{
			EEPROM eeprom = (EEPROM)getSystemService(SerialChat.EEPROM_SERVICE);
			if (!eeprom.exists())
			{

			}
			else
			{
//  EEPROM factory area: 2k x 32bit words total
//  WORD OFFSET    	READ PASSOWRD				WRITE PASSWORD			NOTE
//     0 .. 1023 	no password required 		factory					read-all, write-factory
//  1024 .. 1535 	tech or factory				factory					read-tech/factory, write-factory
//  1536 .. 2047 	factory						factory					read-factory, write-factory

//  EEPROM tech area   : 4k x 32bit words total
//  WORD OFFSET    	READ PASSOWRD				WRITE PASSWORD			NOTE
//     0 .. 2047 	no password required 		tech or factory			read-all, write-tech/factory
//  2048 .. 4095 	tech or factory				tech or factory			read-tech/factory, write-tech/factory

//  EEPROM user area   : 10k x 32bit words total
//  WORD OFFSET    	READ PASSOWRD				WRITE PASSWORD			NOTE
//     0 ..10239 	no password required 		no password required	read-all, write-all
				// public int readEEPROM(int area, int word_index, int password_value_in) throws RemoteException
				// public void writeEEPROM(int area, int word_index, int value_to_write, int password_value_in) throws RemoteException
				//typedef enum
				//{
				//	enum_EEPROM_area_user = 0,
				//			enum_EEPROM_area_tech,
				//			enum_EEPROM_area_factory,
				//			enum_EEPROM_area_numof,
				//}enum_EEPROM_area;
				//#define def_PASSWORD_FACTORY 0x25ACFAC0
				//#define def_PASSWORD_TECH 0x1EC4A3EA
				int nloop = 3;
				int i;
				int area = 0;
				int word_index = 0;
				int password = 0;
				int word_write = 0x12345678;
				for (i = 0; i < nloop; i++)
				{
					String s = "";
					try
					{
						eeprom.writeEEPROM(area, word_index, word_write, password);

					}
					catch(RemoteException e)
					{
						s = e.toString();
					}
					int word_read = 0;
					try
					{
						word_read = eeprom.readEEPROM(area, word_index, password);
						if (word_read != word_write)
						{
							int error = 1;
						}
					}
					catch(RemoteException e)
					{
						s = e.toString();
					}
				}

			}
		}
    	// to enable/disable the very simple radio service test
    	final boolean b_do_radio_service_test=false;
        // very simple radio service test
        if (b_do_radio_service_test){
        	// check if radio exists and if 
	        b_radio_exists=false;
	        RadioRF radioRF = (RadioRF)getSystemService(SerialChat.RADIORF_SERVICE);
	        if ((radioRF!=null) && (radioRF.exists())){
	        	b_radio_exists=true;
	            // radio on/off
	            radioRF.off();         
	            radioRF.on();         
	            radioRF.off();         
	            radioRF.on();         
	        }
        }
		// testing the LEDs
		if (true)
		{
			Asac_LEDs asacLEDs = new Asac_LEDs(this);
			if (asacLEDs != null)
			{
				// both LEDs off
				asacLEDs.blueOff();
				asacLEDs.greenOff();
				try{
					Thread.sleep(1000);
				}catch(InterruptedException e){

				}

				// blue ON
				asacLEDs.blueOn();
				asacLEDs.greenOff();
				try{
					Thread.sleep(1000);
				}catch(InterruptedException e){

				}

				// green ON
				asacLEDs.blueOff();
				asacLEDs.greenOn();
				try{
					Thread.sleep(1000);
				}catch(InterruptedException e){

				}

				// both blue and green ON
				asacLEDs.blueOn();
				asacLEDs.greenOn();
				try{
					Thread.sleep(100);
				}catch(InterruptedException e){

				}
				// both LEDs off
				asacLEDs.blueOff();
				asacLEDs.greenOff();
			}

		}
		// to enable/disable the very simple radio service test
		final boolean b_do_radio_on=true;
		// very simple radio service test
		if (b_do_radio_on){
			// check if radio exists and if
			b_radio_exists=false;
			RadioRF radioRF = (RadioRF)getSystemService(SerialChat.RADIORF_SERVICE);
			if ((radioRF!=null) && (radioRF.exists())){
				b_radio_exists=true;

				// radio first OFF then ON
				radioRF.off();
				try{
					Thread.sleep(500);
				}catch(InterruptedException e){

				}

				radioRF.on();
				try{
					Thread.sleep(500);
				}catch(InterruptedException e) {

				}
			}
		}

        

        asac_radio_transport_layer=new AsacRadioTransportLayer(this);
        diagnostic_thread=new Diagnostic_thread(asac_radio_transport_layer, this);
        receive_file_test=new Run_receive_file_test(this,asac_radio_transport_layer);
        tx_and_rx_message = new Run_tx_and_rx_message(this,asac_radio_transport_layer);
        run_sendFile=new Run_sendFile(this,asac_radio_transport_layer);
        loop_send_receive_file_test= new Run_loop_send_receive_file_test(this,asac_radio_transport_layer);

        msg_to_transmit = new Asac_radio_network_layer_message();
        msg_received = new Asac_radio_network_layer_message();
        
        asac_radio_module_commands= new Asac_radio_module_commands(this);
       
        setContentView(R.layout.activity_main);

		if (false) {
			View decorView = getWindow().getDecorView();
			int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
			decorView.setSystemUiVisibility(uiOptions);
			decorView.setOnSystemUiVisibilityChangeListener
					(new View.OnSystemUiVisibilityChangeListener() {
						@Override
						public void onSystemUiVisibilityChange(int visibility) {
							if ((visibility & View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == 0) {
								final Handler handler = new Handler();
								handler.postDelayed(new Runnable() {
									@Override
									public void run() {
										int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
										View decorView = getWindow().getDecorView();
										decorView.setSystemUiVisibility(uiOptions);
									}
								}, 5000);
							}
						}
					});


		}
		mLog = (TextView) findViewById(R.id.log);
		//mLog.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
		mEditText = (EditText) findViewById(R.id.message);
		mEditText.setOnEditorActionListener(this);
        queue_receive_file_messages= new  ArrayBlockingQueue<String>(64);

        
        // starts the thread which waits for incoming messages from the asac radio
        new Thread(this).start();

      
        //asacradio_c.init_crc();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    
    }

    @Override
    public void onDestroy() {
		this.asac_radio_transport_layer.stop();
        super.onDestroy();
    }
    private ArrayBlockingQueue<String> queue_receive_file_messages; 
    
    public class Run_sendFile implements Runnable {
    	Context c;
    	AsacRadioTransportLayer artl;
    	boolean b_running;
    	boolean b_stopping;
    	public Run_sendFile(Context c, AsacRadioTransportLayer artl){
    		this.c=c;
    		this.artl=artl;
          	b_running=false;
    		b_stopping=false;
    	}
    	public boolean is_running(){
    		return b_running;
    	}
    	public void stop(){
    		if (is_running()){
    			b_stopping=true;
    			this.artl.stopSendfileThread(8000);
    			try{
    				Thread.sleep(500);
    			}catch(InterruptedException e){
    				
    			}
    			if (is_running()){
    				Message m = Message.obtain(mHandler, MESSAGE_LOG);
		    		m.obj = "error stopping send file thread";
		            mHandler.sendMessage(m);
    			}
    		}
    		
    	}
    	public void run(){
			String fileName="test.txt";
			b_running=true;
			b_stopping=false;
			artl.logEnable.enable(EnumTransportLogEnable.send_file);
			try{
				String path_file_name = this.c.getFilesDir() + File.separator +  fileName;
				EnumSendfileReturnCode ret_code;
				{
		    		Message m = Message.obtain(mHandler, MESSAGE_LOG);
		    		m.obj = "file send is running...";
		            mHandler.sendMessage(m);
		            Thread.sleep(100);
				}
				ret_code=this.artl.sendFile(path_file_name, fileName);
				if (b_stopping){
	        		Message m = Message.obtain(mHandler, MESSAGE_LOG);
	        		m.obj = "file send stopped OK";
	                mHandler.sendMessage(m);
				}
				else if (ret_code==EnumSendfileReturnCode.ok){
	        		Message m = Message.obtain(mHandler, MESSAGE_LOG);
	        		m.obj = "file sent OK, "+this.artl.getSendfile_num_bytes_tx()+" bytes TX";
	                mHandler.sendMessage(m);
					
				}
				else{
	        		Message m = Message.obtain(mHandler, MESSAGE_LOG);
	        		m.obj = "file send error: "+ret_code.toString();
	                mHandler.sendMessage(m);
					
				}
			}
			catch (Exception e){
				
			}
			finally{
				artl.logEnable.disable(EnumTransportLogEnable.send_file);
				b_running=false;
			}
    	}
    }



    public class Run_tx_and_rx_message implements Runnable {
    	Context c;
    	AsacRadioTransportLayer artl;
    	private int i_msg_idx;
    	private int i_num_tx_and_rx_loops;
    	boolean b_running;
    	boolean b_stopping;
		boolean radio_on_between_txrx = true;
    	public Run_tx_and_rx_message(Context c, AsacRadioTransportLayer artl){
          	this.c=c;
          	this.artl=artl;
          	i_msg_idx=1;
          	b_running=false;
    		setI_num_tx_and_rx_loops(1);
    		b_stopping=false;
        }
		public void set_radio_off_between_txrx(boolean r)
		{
			radio_on_between_txrx = r;
		}
    	public boolean is_running(){
    		return b_running;
    	}
    	public void stop(){
    		if (is_running()){
			    {
					Message m = Message.obtain(mHandler, MESSAGE_LOG);
		            String mytext = String.format(Locale.US,"STOPPING THE TX&RX THREAD");                
		            m.obj = mytext;
		            mHandler.sendMessage(m);	
				}
			    b_stopping=true;
    			this.artl.stopTransmitAndReceive(5000);
    			try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
    			if (is_running()){
					Message m = Message.obtain(mHandler, MESSAGE_LOG);
		            String mytext = String.format(Locale.US,"UNABLE TO STOP THE TX&RX THREAD");                
		            m.obj = mytext;
		            mHandler.sendMessage(m);	
    			}
    		}
    	}
    	public void run(){
    		b_running=true;
    		b_stopping=false;
			EnumTransmitAndReceiveReturnCode retcode=EnumTransmitAndReceiveReturnCode.ok;
			long l_max_time_txrx_ms = -1;
			long l_min_time_txrx_ms = 100000000;
			final int base_time_txrx_ms = 500;
			final int max_time_txrx_ms = 5000;
			final int num_elem_txrx_ms = (max_time_txrx_ms / base_time_txrx_ms) + 1;
			long very_long_txrx_times_100ms = 0;
			long total_txrx_times = 0;
			long total_time_ms = 0;
			long[] array_txrx_times_100ms= new long[num_elem_txrx_ms];
    		try{
				// clear the message row
			    {
					Message m = Message.obtain(mHandler, MESSAGE_LOG);
		            String mytext = String.format(Locale.US,"SINGLE MESSAGE THREAD STARTS ON "+getI_num_tx_and_rx_loops()+" LOOPS");                
		            m.obj = mytext;
		            mHandler.sendMessage(m);	
				}
        		for (int i=0;i<getI_num_tx_and_rx_loops();i++){
				    //this.asac_radio_transport_layer.log_enable.enable(enum_artl_log_enable.data_link_layer_receiving_thread);
					//this.artl.logEnable.enable(EnumTransportLogEnable.transmit_and_receive);
					//this.artl.logEnable.enable(EnumTransportLogEnable.network_layer_post_messages);
					//this.artl.logEnable.enable(EnumTransportLogEnable.network_layer_receiving_thread);

					if (++i_msg_idx>9){
						i_msg_idx=1;
					}
					//String txrxstr="F_TESTR\001#TX&RX ["+i_msg_idx+"]* TEST * hello from TDA7!";
					String txrxstr="ASAC_CK\001Controllo";

					final boolean change_timeouts = false;
					int save_max_repeatitions = 10;
					int save_timeout_ms = 3000;
					if (change_timeouts)
					{
						save_max_repeatitions = artl.params.transmitAndReceive.getMaxRepetitionsNumOf();
						artl.params.transmitAndReceive.setMaxRepetitionsNumOf(3);
						save_timeout_ms = artl.params.transmitAndReceive.getTimeoutMs();
						artl.params.transmitAndReceive.setTimeoutMs(300);
					}

					msg_to_transmit.message=txrxstr.getBytes();
					msg_to_transmit.numBytesInMessage=txrxstr.length();
					long start_time_ms = System.currentTimeMillis();
					retcode=artl.transmitAndReceive(enum_rnl_message_destination_type.message_destination_server, msg_to_transmit, msg_received);
					long end_time_ms = System.currentTimeMillis();
					long diff_time_ms = end_time_ms - start_time_ms;
					{
						if (diff_time_ms > l_max_time_txrx_ms)
						{
							l_max_time_txrx_ms = diff_time_ms;
						}
						if (diff_time_ms < l_min_time_txrx_ms)
						{
							l_min_time_txrx_ms = diff_time_ms;
						}
						long idx = diff_time_ms / base_time_txrx_ms;
						if (idx >= num_elem_txrx_ms )
						{
							very_long_txrx_times_100ms++;
						}
						else
						{
							int i_index = (int)idx;
							array_txrx_times_100ms[i_index] ++;
						}
						total_txrx_times++;
						total_time_ms += diff_time_ms;
					}

					if (change_timeouts)
					{
						artl.params.transmitAndReceive.setTimeoutMs(save_timeout_ms);
						artl.params.transmitAndReceive.setMaxRepetitionsNumOf(save_max_repeatitions);
					}

				    if (retcode==EnumTransmitAndReceiveReturnCode.ok){
						Message m = Message.obtain(mHandler, MESSAGE_LOG);
			            String mymsg = new String(msg_received.message, 0, msg_received.numBytesInMessage);
			            int i_trip_ms=asac_radio_transport_layer.getLastTripTimeMs();
						Asac_radio_module_commands.enum_radio_strength rs = artl.arnl.handleNORpackets.getRadio_strength();
			            String mytext = "SINGLE MESSAGE TX&RX loop "+Integer.valueOf(i+1)+"/"+getI_num_tx_and_rx_loops()+" OK\n"
										+ "RSSI:" + rs.ordinal() + "/"+ Asac_radio_module_commands.enum_radio_strength.excellent.ordinal()+"\n"
										+String.format(Locale.US,"%s\ntriptime %d ms, total time %d ms\n"
			        			,mymsg
			        			,i_trip_ms
								,(int)diff_time_ms
			            );                
			            m.obj = mytext;
			            mHandler.sendMessage(m);	
			            // little delay between transmissions

// switch radio off
						if (!radio_on_between_txrx)
						{
							artl.OFF_radio_power();

						}
// wait after radio off
// wait between min_pause_ms and max_pause_ms ms
						if (i + 1 <getI_num_tx_and_rx_loops())
						{
							final int max_pause_ms = 1500;
							final int min_pause_ms = 100;
							int diff_pause_ms = max_pause_ms - min_pause_ms +1;
							if (diff_pause_ms < 0)
							{
								diff_pause_ms = 10;
							}
							try {
								Thread.sleep(min_pause_ms + (int)(Math.random() * diff_pause_ms));
								//Thread.sleep(10);
								//Thread.sleep(2500);
							} catch (InterruptedException e) {
							}
						}

			        }
				    // error? exit
				    else{
						Message m = Message.obtain(mHandler, MESSAGE_LOG);

			            String mymsg = "SINGLE MESSAGE TX&RX error: "+retcode.toString();
			            m.obj = mymsg;
			            mHandler.sendMessage(m);		
			            break;
				    }
				    if (b_stopping){
						Message m = Message.obtain(mHandler, MESSAGE_LOG);
			            String mymsg = "SINGLE MESSAGE TX&RX stopped";
			            m.obj = mymsg;
			            mHandler.sendMessage(m);		
			            break;
				    }
		    	}
        		if (retcode==EnumTransmitAndReceiveReturnCode.ok){
//        			// little delay at the end of transmissions
		            try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
					}
 					Message m = Message.obtain(mHandler, MESSAGE_LOG);
		            String mymsg = "SINGLE MESSAGE TX&RX ENDS OK";
		            m.obj = mymsg;
		            mHandler.sendMessage(m);
        		}
				else
				{
					Message m = Message.obtain(mHandler, MESSAGE_LOG);
					String mymsg = "SINGLE MESSAGE TX&RX ENDS KO ****";
					m.obj = mymsg;
					mHandler.sendMessage(m);
				}
        		// reset num loop to 1
        		setI_num_tx_and_rx_loops(1);
    		}
        	finally{
        		b_running=false;
				Message m = Message.obtain(mHandler, MESSAGE_LOG);
				String s = "result: ";
				if (retcode==EnumTransmitAndReceiveReturnCode.ok)
				{
					s= "OK\n";
				}
				else
				{
					s= "KO\n";
				}
				s += "#loop set: " + getI_num_tx_and_rx_loops() + ", executed: "+ total_txrx_times+ "\n";
				if (radio_on_between_txrx)
				{
					s += "radio ON between tx\n";
				}
				else
				{
					s += "radio OFF between tx\n";
				}
				s += "max time:" + l_max_time_txrx_ms + "; min time:" + l_min_time_txrx_ms +"; average time" + total_time_ms/total_txrx_times + "\n";
				int i;
				for (i = 0; i < num_elem_txrx_ms; i++)
				{
					s= s + "t<"+ (i+1)*base_time_txrx_ms + ":"+ array_txrx_times_100ms[i]+ "\n";
				}
				s = s + "very long time:" + very_long_txrx_times_100ms+"\n";

				m.obj = s;
				mHandler.sendMessage(m);

			}
    	}
		public int getI_num_tx_and_rx_loops() {
			return i_num_tx_and_rx_loops;
		}
		public void setI_num_tx_and_rx_loops(int i_num_tx_and_rx_loops) {
			this.i_num_tx_and_rx_loops = i_num_tx_and_rx_loops;
		}
    }

    public class Run_loop_send_receive_file_test implements Runnable {
    	private Context c;
    	private AsacRadioTransportLayer artl;
    	private boolean b_running;
    	private boolean b_stopping;
    	
		private class Thread_receive implements Runnable {
	    	private AsacRadioTransportLayer artl;
	    	private boolean b_running;
	    	private boolean b_completed;
	    	String s_file_to_receive,pc_filename;
	    	private EnumReceivefileReturnCode retcode;
			public Thread_receive(AsacRadioTransportLayer artl,String s_file_to_receive,String pc_filename){
				this.artl=artl;
				this.s_file_to_receive=s_file_to_receive;
				this.pc_filename=pc_filename;
				this.b_completed=false;
				setRetcode(EnumReceivefileReturnCode.ok);
			}
			public void reset_completed(){
				this.b_completed=false;
			}
			public boolean completed(){
				return this.b_completed;
			}
			public void run(){
				this.b_running=true;
				try{
					// receive the file from the PC
					setRetcode(artl.receiveFile(s_file_to_receive, getKind_of_receive(), pc_filename));
				}
				catch (RuntimeException e ){
					throw new RuntimeException("exception receiving file", e);
				}
				finally{
					this.b_running=false;
					this.b_completed=true;
				}
			}
	    	public boolean is_running(){
	    		return b_running;
	    	}
	    	public void stop(){
	    		if (is_running()){
	    			this.artl.bStopReceiveFile(8000);
	    			try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
					}
	    		}
	    	}
			public EnumReceivefileReturnCode getRetcode() {
				return retcode;
			}
			public void setRetcode(EnumReceivefileReturnCode retcode) {
				this.retcode = retcode;
			}
		}
		
		private class Thread_send implements Runnable {
	    	private AsacRadioTransportLayer artl;
	    	private boolean b_running;
	    	private boolean b_completed;
	    	String s_file_to_send,pc_filename;
	    	private EnumSendfileReturnCode retcode;
			public Thread_send(AsacRadioTransportLayer artl,String s_file_to_send,String pc_filename){
				this.artl=artl;
				this.s_file_to_send=s_file_to_send;
				this.pc_filename=pc_filename;
				this.b_completed=false;
				setRetcode(EnumSendfileReturnCode.ok);
			}
			public boolean completed(){
				return this.b_completed;
			}
			public void reset_completed(){
				this.b_completed=false;
			}
			public void run(){
				this.b_running=true;
				try{
					// receive the file from the PC
					setRetcode(artl.sendFile(s_file_to_send,  pc_filename));
				}
				catch (RuntimeException e ){
					throw new RuntimeException("exception sending file", e);
				}
				finally{
					this.b_running=false;
					this.b_completed=true;
				}
			}
	    	public boolean is_running(){
	    		return b_running;
	    	}
	    	public void stop(){
	    		if (is_running()){
	    			this.artl.stopSendfileThread(8000);
	    			try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
					}
	    		}
	    	}
			public EnumSendfileReturnCode getRetcode() {
				return retcode;
			}
			public void setRetcode(EnumSendfileReturnCode retcode) {
				this.retcode = retcode;
			}
		}
		
    	public boolean is_running(){
    		return b_running;
    	}
    	public void stop(){
    		if (is_running()){
    			b_stopping=true;
    			if (this.thread_receive!=null){
	    			this.thread_receive.stop();
    			}
    			if (this.thread_send!=null){
    				this.thread_send.stop();
    			}
    			try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
				}
    			if (is_running()){
					try {
						queue_receive_file_messages.put("error stopping send receive file loop");
					} catch (InterruptedException e1) {
					}			
    			}
    		}
    	}
		Thread_receive thread_receive;
		Thread_send thread_send;
		
		final int refresh_info_receiving=0;
		final int refresh_info_sending=1;
		final int refresh_info_general=2;
		private void refresh_info(int idx_file_loop, int i_num_loop_to_execute, int i_refresh_type){
			try {
                String mytext = "";                
				if (idx_file_loop>=i_num_loop_to_execute){
					mytext = String.format(Locale.US,"END OF FILE SEND/RECEIVE TEST, "+String.valueOf(idx_file_loop)+" LOOPS DONE, ALL OK **");                
				}
				else{
					mytext = String.format(Locale.US,"FILE SEND/RECEIVE loop "+String.valueOf(idx_file_loop+1)+" of "+String.valueOf(i_num_loop_to_execute));
				}
                mytext=mytext+"\n File size act:"+i_created_file_size;
				if (i_refresh_type==refresh_info_receiving){
	                mytext=mytext+"\n *** RECEIVING ***";
	                long lbytesrx=artl.stats.receive_file.last_file_bytes_RX;
	                int iperc=(int)((100*lbytesrx)/i_created_file_size);
	                iperc=Math.max(Math.min(iperc, 100), 0);
	                mytext=mytext+"\n Receiving "+iperc+" %";
	                mytext=mytext+"\n Total bytes rx:"+NumberFormat.getNumberInstance(Locale.US).format(lbytesrx);
	                String rate = String.format(Locale.US,"\n bytes/s %5.2f",artl.stats.receive_file.last_transfer_rate_byte_s);
	                mytext=mytext+rate;
	                mytext=mytext+"\n Num OK "+String.valueOf(artl.stats.receive_file.i_num_OK);
	                mytext=mytext+"\n Num ACK "+String.valueOf(artl.stats.receive_file.i_num_ack_sent);
	                mytext=mytext+"\n Num NACK "+String.valueOf(artl.stats.receive_file.i_num_nack_sent);
	                mytext=mytext+"\n Num ACK_RESYNC "+String.valueOf(artl.stats.receive_file.i_num_resync_sent);
				}
				else if (i_refresh_type==refresh_info_sending){
	                mytext=mytext+"\n *** SENDING ***";
	                mytext=mytext+"\n Sending "+artl.stats.getI_sendfile_percentage()+" %";
	                mytext=mytext+"\n "+NumberFormat.getNumberInstance(Locale.US).format(artl.stats.getL_sendfile_offset())+" bytes sent";
	                String rate = String.format(Locale.US,"\n bytes/s %5.2f",artl.stats.sendfileStats.getSpeedBytesPerSecond());
	                mytext=mytext+rate;
				}
                try {
					queue_receive_file_messages.put(mytext);
				} catch (InterruptedException e1) {
				}
			} catch(Exception e){
				
			}		
		}
        public void run() {
        	boolean b_use_ASCII_file=false;
        	b_running=true;
        	b_stopping=false;
			String TDA7_receive_fileName="receive_load.txt";
			String TDA7_path_receive_file_name = c.getFilesDir() + File.separator +  TDA7_receive_fileName;
			String TDA7_send_fileName="test.txt";
			String TDA7_path_send_file_name = c.getFilesDir() + File.separator +  TDA7_send_fileName;
			String PC_filename="send_receive_tda7.txt";
			EnumReceivefileReturnCode retcode=EnumReceivefileReturnCode.ok;
			// the number of send / receive loops
			final int MAX_NUM_LOOP=128;
			boolean b_loop_error=false;
			// create the thread for receiving
        	this.thread_receive=new Thread_receive(artl,TDA7_path_receive_file_name,PC_filename);	
			// create the thread for sending
        	this.thread_send=new Thread_send(artl,TDA7_path_send_file_name,PC_filename);	
			
			// enable some logs...
			artl.logEnable.disable_all();
			try {
				Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			//artl.logEnable.enable(EnumTransportLogEnable.receive_file);
			artl.logEnable.enable(EnumTransportLogEnable.send_file);
			artl.logEnable.enable(EnumTransportLogEnable.network_layer_post_messages);
			//artl.logEnable.enable(EnumTransportLogEnable.transmit_and_receive);
			try {
				queue_receive_file_messages.put("receive file in load mode begins...");
			} catch (InterruptedException e1) {
			}	
			try{
				while(!b_stopping&& !b_loop_error){
					if (!b_create_new_TDA7_test_file(TDA7_path_send_file_name,b_use_ASCII_file)){
						try {
							queue_receive_file_messages.put("unable to create test file !");
						} catch (InterruptedException e) {
						}
		                b_loop_error=true;							
					}
					// transfer max tot kBytes at every loop
					final int i_max_KBytes_to_transfer=512;
					final int i_max_Bytes_to_transfer=i_max_KBytes_to_transfer*1024;
					int i_clipped_num_loop=i_max_Bytes_to_transfer/i_created_file_size;
					if (i_clipped_num_loop<2){
						i_clipped_num_loop=2;
					}
					int i_num_loop_to_execute=MAX_NUM_LOOP;
					if (i_num_loop_to_execute>i_clipped_num_loop){
						i_num_loop_to_execute=i_clipped_num_loop;
					}
					for (int idx_file_loop=0;idx_file_loop<i_num_loop_to_execute+1;idx_file_loop++){
						if (b_loop_error || b_stopping){
							break;
						}
						try {
							Thread.sleep(10);
						} catch (InterruptedException e) {
						}				
						this.refresh_info(idx_file_loop, i_num_loop_to_execute,refresh_info_general);
						if (idx_file_loop>=i_num_loop_to_execute){
							break;
						}
						try{
							if (!b_loop_error &&!b_stopping){
								EnumSendfileReturnCode retcode_send;
								try{
						        	// start the thread
									thread_send.reset_completed();
									new Thread(thread_send).start();
									// wait until the thread completes
									while(!thread_send.completed()){
										try {
											Thread.sleep(200);
										} catch (InterruptedException e) {
										}
										this.refresh_info(idx_file_loop, i_num_loop_to_execute,refresh_info_sending);
									}
									// get the return code
									retcode_send=thread_send.getRetcode();
									
									// send the file to the PC
									if (retcode_send!=EnumSendfileReturnCode.ok){
						                String mytext = String.format(Locale.US,getKind_of_receive().toString()+" ERROR SENDING FILE: "+retcode_send.toString()+"(code "+String.valueOf(retcode_send.ordinal())+")");  
						                try {
											queue_receive_file_messages.put(mytext);
										} catch (InterruptedException e) {
										}
						                b_loop_error=true;							
									}
								}
								catch (RuntimeException e ){
									throw new RuntimeException("exception sending file", e);
								}
							}
							
							if (!b_loop_error &&!b_stopping){
								try{
						        	// start the thread
									thread_receive.reset_completed();
									new Thread(thread_receive).start();
									// wait until the thread completes
									while(!thread_receive.completed()){
										try {
											Thread.sleep(200);
										} catch (InterruptedException e) {
										}
										this.refresh_info(idx_file_loop, i_num_loop_to_execute,refresh_info_receiving);
									}
									// get the return code
									retcode=thread_receive.getRetcode();
									// check the return code
									if (retcode!=EnumReceivefileReturnCode.ok){
						                String mytext = String.format(Locale.US,getKind_of_receive().toString()+" ERROR RECEIVING FILE: "+retcode.toString()+"(code "+String.valueOf(retcode.ordinal())+")");  
						                try {
											queue_receive_file_messages.put(mytext);
										} catch (InterruptedException e) {
										}
						                b_loop_error=true;
									}
								}
								catch (RuntimeException e ){
									throw new RuntimeException("exception receiving file", e);
								}
							}
							// compare sent and received files
							if (!b_loop_error&&!b_stopping){
								RandomAccessFile file_received=null;
								RandomAccessFile file_sent=null;
								long l_received_file_size=-1;
								long l_sent_file_size=-1;
								try {
									file_received = new RandomAccessFile(TDA7_path_receive_file_name, "r");
								} catch (FileNotFoundException e1) {
									file_received=null;
								}
								try {
									file_sent = new RandomAccessFile(TDA7_path_send_file_name, "r");
								} catch (FileNotFoundException e1) {
									file_sent=null;
								}
								// check if files were opened OK
								if (file_sent==null || file_received==null){
									try {
										if (file_sent==null && file_received==null){
											queue_receive_file_messages.put("both sent and received files are null, cannot compare, error");
										}
										else if (file_sent==null){
											queue_receive_file_messages.put("sent file is null, cannot compare, error");
										}
										else if (file_received==null){
											queue_receive_file_messages.put("received file is null, cannot compare, error");
										}
									} catch (InterruptedException e) {
									}								
									b_loop_error=true;
								}
								// check if file's lengths are nonnegative
								if (!b_loop_error){
									try{
										l_received_file_size=file_received.length();
									}catch (IOException e){
										l_received_file_size=-1;
									}	
									if (l_received_file_size<0){
										try {
											queue_receive_file_messages.put("received file invalid size "+l_received_file_size);
										} catch (InterruptedException e) {
										}
										b_loop_error=true;
									}
								}
								if (!b_loop_error){
									try{
										l_sent_file_size=file_sent.length();
									}catch (IOException e){
										l_sent_file_size=-1;
									}	
									if (l_sent_file_size<0){
										try {
											queue_receive_file_messages.put("received file invalid size "+l_sent_file_size);
										} catch (InterruptedException e) {
										}
										b_loop_error=true;
									}
								}
								// check if file's lengths are the same
								if (!b_loop_error){
									if (l_sent_file_size!=l_received_file_size){
										try {
											queue_receive_file_messages.put("files have different size; received size is "+l_received_file_size+" while sent size is "+l_sent_file_size);
										} catch (InterruptedException e) {
										}
										b_loop_error=true;
									}
								}
								// check if files contents are the same
								long l_tot_bytes_read=0;
								final int byte_buffer_size=1024;
								byte[] buffer_received = new byte[byte_buffer_size];
								byte[] buffer_sent = new byte[byte_buffer_size];
								while(l_tot_bytes_read<l_sent_file_size && !b_loop_error &&!b_stopping){
									long n_bytes_read_received=-1;
									long n_bytes_read_sent=-1;
									if (!b_loop_error){
										try{
											n_bytes_read_received=file_received.read(buffer_received, 0, buffer_received.length);
										}catch (IOException e){
											n_bytes_read_received=-1;
										}
										if (n_bytes_read_received<0){
											try {
												queue_receive_file_messages.put("error reading from receiving file");
											} catch (InterruptedException e) {
											}
											b_loop_error=true;
										}
									}
									if (!b_loop_error){
										try{
											n_bytes_read_sent=file_sent.read(buffer_sent, 0, buffer_sent.length);
										}catch (IOException e){
											n_bytes_read_sent=-1;
										}
										if (n_bytes_read_sent<0){
											try {
												queue_receive_file_messages.put("error reading from sent file");
											} catch (InterruptedException e) {
											}
											b_loop_error=true;
										}
									}
									if (!b_loop_error){
										if (n_bytes_read_sent!=n_bytes_read_received){
											try {
												queue_receive_file_messages.put("different number of bytes read from files; from received "+n_bytes_read_received+" bytes read; from sent "+n_bytes_read_sent+" bytes sent");
											} catch (InterruptedException e) {
											}
											b_loop_error=true;
										}
									}
									if (!b_loop_error){
										for (int idx_buffer=0;idx_buffer<n_bytes_read_sent; idx_buffer++){
											if (buffer_sent[idx_buffer]!=buffer_received[idx_buffer]){
												try {
													queue_receive_file_messages.put("sent and received file are different at offset "+l_tot_bytes_read+idx_buffer);
												} catch (InterruptedException e) {
												}
												b_loop_error=true;
												break;
											}
										}
									}
									l_tot_bytes_read+=n_bytes_read_sent;
								}
							}
						}
						catch(RuntimeException e){
			                String mytext = String.format(Locale.US,getKind_of_receive().toString()+" RUNTIME EXCEPTION LOADING FILE: "+e);                
			                try {
								queue_receive_file_messages.put(mytext);
							} catch (InterruptedException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
			                b_loop_error=true;
						}
						if (b_stopping){
			                try {
								queue_receive_file_messages.put("file send/receive stopped");
							} catch (InterruptedException e1) {
							}
							break;
						}
					}// for
				}// while
			}
			finally{
				artl.logEnable.disable(EnumTransportLogEnable.receive_file);
				b_running=false;
			}
			
			
        }
        private int i_created_file_size=0;

        private boolean b_create_new_TDA7_test_file(String tDA7_path_send_file_name,boolean b_ASCII_file) {
			// create the file
			// the file can be ASCII or binary generic 
			// the file size can be between 1 and 1024 or between 100K and 101K
			final int i_min_small_file_size=1;
			final int i_max_small_file_size=1024;
			final int i_min_big_file_size=1024*100;
			final int i_max_big_file_size=1024*101;
			int i_file_size=1;
			boolean b_small_file_size=(Math.random()>=0.5);
			if (b_small_file_size){
				i_file_size=(int)(i_min_small_file_size+(i_max_small_file_size-i_min_small_file_size)*Math.random());
			}
			else{
				i_file_size=(int)(i_min_big_file_size+(i_max_big_file_size-i_min_big_file_size)*Math.random());
			}
			i_created_file_size=i_file_size;
			RandomAccessFile file_sent=null;
			boolean b_file_ok=true;
			try {
				file_sent = new RandomAccessFile(tDA7_path_send_file_name, "rw");		
			}catch(IOException e){
				b_file_ok=false;
			}
			if (b_file_ok){
				try {
					file_sent.setLength(0);
				} catch (IOException e) {
					b_file_ok=false;
				}
			}
			if (b_file_ok){
				byte [] file_buffer=new byte[i_file_size];
				if (b_ASCII_file){
					byte b=(byte)(' '+(byte)(Math.random()*(0x7f-' ')));
					for (int idx_c=0;idx_c<file_buffer.length;idx_c++){
						if ((b<' ') || (b>0x7f)){
							b=' ';
						}
						file_buffer[idx_c]=b;
						b++;
					}
					
				}
				else{
					// start from a random byte, then put all of the subsequent bytes
					byte b_start=(byte)(((int)(256*Math.random()))&0xff);
					for (int idx_c=0;idx_c<file_buffer.length;idx_c++){
						file_buffer[idx_c]=(byte)((b_start+idx_c)&0xff);
					}
				}
				try{
					file_sent.write(file_buffer);
				}
				catch(IOException e){
					b_file_ok=false;
				}
			}
			if (file_sent!=null){
				try{
					file_sent.close();
				}
				catch(IOException e){
					b_file_ok=false;
				}
			}
			return b_file_ok;
		}
        
        
		public Run_loop_send_receive_file_test(Context c, AsacRadioTransportLayer artl){
        	this.c=c;
        	this.artl=artl;
        }
        public EnumReceiveFileMode getKind_of_receive() {
			return kind_of_receive;
		}

		public void setKind_of_receive(EnumReceiveFileMode kind_of_receive) {
			this.kind_of_receive = kind_of_receive;
		}
		private EnumReceiveFileMode kind_of_receive=EnumReceiveFileMode.load;
        

    }
    
    
    public class Run_receive_file_test implements Runnable {
    	private Context c;
    	private AsacRadioTransportLayer artl;
    	private boolean b_running;
    	private boolean b_stopping;
    	public boolean is_running(){
    		return b_running;
    	}
    	public void stop(){
    		if (is_running()){
    			b_stopping=true;
    			this.artl.bStopReceiveFile(8000);
    			try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
				}
    			if (is_running()){
					try {
						queue_receive_file_messages.put("error stopping receive file loop");
					} catch (InterruptedException e1) {
					}			
    			}
    		}
    	}
        public void run() {
        	b_running=true;
        	b_stopping=false;
			String TDA7_fileName="receive_load.txt";
			String TDA7_path_file_name = c.getFilesDir() + File.separator +  TDA7_fileName;
			String PC_filename="send_tda7.txt";
			EnumReceivefileReturnCode retcode=EnumReceivefileReturnCode.ok;
			// the number of send / receive loops
			final int i_max_num_loop=64;
			boolean b_loop_error=false;
			// enable some logs...
			artl.logEnable.disable_all();
			artl.logEnable.enable(EnumTransportLogEnable.receive_file);
			//artl.log_enable.enable(enum_artl_log_enable.transmit_and_receive);
			try {
				queue_receive_file_messages.put("receive file in load mode begins...");
			} catch (InterruptedException e1) {
			}	
			try{
				for (int i=0;i<i_max_num_loop+1;i++){
					if (b_loop_error){
						break;
					}
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}				
					try{
						try {
			                String mytext = "";                
							if (i>=i_max_num_loop){
								mytext = String.format(Locale.US,getKind_of_receive().toString()+" END OF FILE RECEIVE TEST, "+String.valueOf(i)+" LOOPS DONE, ALL OK **");                
							}
							else{
								mytext = String.format(Locale.US,getKind_of_receive().toString()+" COMPLETE FILE RECEIVED OK, loop "+String.valueOf(i+1)+" of "+String.valueOf(i_max_num_loop));                
							}
			                mytext=mytext+"\n File size:"+String.valueOf(artl.stats.receive_file.last_file_bytes_RX);
			                mytext=mytext+"\n Total bytes rx:"+String.valueOf(artl.stats.receive_file.l_bytes_RX);
			                String rate = String.format(Locale.US,"\n bytes/s %5.2f",artl.stats.receive_file.last_transfer_rate_byte_s);
			                mytext=mytext+rate;
			                mytext=mytext+"\n Num OK "+String.valueOf(artl.stats.receive_file.i_num_OK);
			                mytext=mytext+"\n Num ACK "+String.valueOf(artl.stats.receive_file.i_num_ack_sent);
			                mytext=mytext+"\n Num NACK "+String.valueOf(artl.stats.receive_file.i_num_nack_sent);
			                mytext=mytext+"\n Num ACK_RESYNC "+String.valueOf(artl.stats.receive_file.i_num_resync_sent);
			                try {
								queue_receive_file_messages.put(mytext);
							} catch (InterruptedException e1) {
								// TODO Auto-generated catch block
								e1.printStackTrace();
							}
						} catch(Exception e){
							
						}		
						finally{
							if (i>=i_max_num_loop){
								break;
							}
						}
						
						// do the test using the receive mode set
						retcode=artl.receiveFile(TDA7_path_file_name, getKind_of_receive(), PC_filename);
						if (retcode!=EnumReceivefileReturnCode.ok){
			                String mytext = String.format(Locale.US,getKind_of_receive().toString()+" ERROR LOADING FILE: "+retcode.toString()+"(code "+String.valueOf(retcode.ordinal())+")");  
			                try {
								queue_receive_file_messages.put(mytext);
							} catch (InterruptedException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
			                b_loop_error=true;
						}
					}
					catch(RuntimeException e){
		                String mytext = String.format(Locale.US,getKind_of_receive().toString()+" RUNTIME EXCEPTION LOADING FILE");                
		                try {
							queue_receive_file_messages.put(mytext);
						} catch (InterruptedException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}
		                b_loop_error=true;
					}
					if (b_stopping){
		                try {
							queue_receive_file_messages.put("file receive stopped");
						} catch (InterruptedException e1) {
						}
						break;
					}
				}
			}
			finally{
				artl.logEnable.disable(EnumTransportLogEnable.receive_file);
				b_running=false;
			}
			
			
        }

        public Run_receive_file_test(Context c, AsacRadioTransportLayer artl){
        	this.c=c;
        	this.artl=artl;
        }
        public EnumReceiveFileMode getKind_of_receive() {
			return kind_of_receive;
		}

		public void setKind_of_receive(EnumReceiveFileMode kind_of_receive) {
			this.kind_of_receive = kind_of_receive;
		}
		private EnumReceiveFileMode kind_of_receive=EnumReceiveFileMode.load;
        

    }
    private Run_receive_file_test receive_file_test;
    private Run_tx_and_rx_message tx_and_rx_message;
    public Run_sendFile run_sendFile;
    public Run_loop_send_receive_file_test loop_send_receive_file_test;
    
    public int i_num_of_file_load_tried=0;

    @SuppressLint("DefaultLocale")
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        String text_input = v.getText().toString().toLowerCase();
		String msg_string=new String();
		boolean b_is_radio_module_command=false;
		boolean b_hide_onscreen_keyboard=false;
		// "typhoon" to program radio module in typhoon emulation mode, i.e. in the way we want actually to use the module to communicate with the remote PC
		if (text_input.contentEquals("typhoon")){
			b_hide_onscreen_keyboard=true;
			String radio_req="AT+ASACMOD=12,5,1,0";
			msg_to_transmit.message=radio_req.getBytes();
			msg_to_transmit.numBytesInMessage=radio_req.length();
		    asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module, msg_to_transmit,msg_received);
		    
			radio_req="AT+ASACSAVE";
			msg_to_transmit.message=radio_req.getBytes();
			msg_to_transmit.numBytesInMessage=radio_req.length();
		    asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module, msg_to_transmit, msg_received);
		}    
		else if (text_input.startsWith("code")){
			if (text_input.length()>4){
			int iCodeValue=Integer.valueOf(text_input.substring(4, text_input.length()));
			if (iCodeValue>0){
				b_hide_onscreen_keyboard=true;
				String radio_req="AT+ASACMOD=12,"+iCodeValue+",1,0";
				msg_to_transmit.message=radio_req.getBytes();
				msg_to_transmit.numBytesInMessage=radio_req.length();
			    asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module,msg_to_transmit,msg_received);
			    
				radio_req="AT+ASACSAVE";
				msg_to_transmit.message=radio_req.getBytes();
				msg_to_transmit.numBytesInMessage=radio_req.length();
			    asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module,msg_to_transmit,msg_received);
			}
			}
		}
		// rrp = read radio protect code
		else if (text_input.startsWith("rrp")){

			b_hide_onscreen_keyboard = true;

			msg_string=new String();

			// builds up the command to query the radio protect code:
			msg_string=asac_radio_module_commands.build_get_module_protect_code();
			msg_to_transmit.message=msg_string.getBytes();
			msg_to_transmit.numBytesInMessage=msg_string.length();
			EnumTransmitAndReceiveReturnCode retcode;
			retcode=asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module,msg_to_transmit,msg_received);
			if (retcode==EnumTransmitAndReceiveReturnCode.ok)
			{
				String mytext = new String(msg_received.message, 0, msg_received.numBytesInMessage);
				StringBuilder reply=new StringBuilder();
				enum_module_reply_type module_reply;
				module_reply=asac_radio_module_commands.check_responses(mytext,reply);
				if (module_reply!=enum_module_reply_type.enum_radio_module_reply_none)
				{
					String mytext_reply=reply.toString();
					if (module_reply==enum_module_reply_type.enum_radio_module_reply_get_protect_code)
					{
//                       asac_radio_module_commands.parameters.i_crypt_code contiene codice crypt
						Message m = Message.obtain(mHandler, MESSAGE_LOG);
						m.obj = "Crypt code is: " + this.asac_radio_module_commands.read_radio_crypt();
						mHandler.sendMessage(m);
					}
				}
			}
		}
		// wrp<code> = write radio protect code
		// if the command is executed successfully, we save the new setting in the radio module EEPROM and the radio module will reset
		else if (text_input.startsWith("wrp")){

			if (text_input.length()>3)
			{
				int iProtectValue = Integer.parseInt(text_input.substring(3, text_input.length()));
				b_hide_onscreen_keyboard = true;
				String radio_req=asac_radio_module_commands.build_set_module_protect_code() + iProtectValue;
				msg_to_transmit.message=radio_req.getBytes();
				msg_to_transmit.numBytesInMessage=radio_req.length();
				EnumTransmitAndReceiveReturnCode return_code;
				return_code=asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module,msg_to_transmit,msg_received);
				if (return_code==EnumTransmitAndReceiveReturnCode.ok)
				{
					String mytext = new String(msg_received.message, 0, msg_received.numBytesInMessage);
					StringBuilder reply=new StringBuilder();
					enum_module_reply_type module_reply;
					module_reply=asac_radio_module_commands.check_responses(mytext,reply);
					if (module_reply!=enum_module_reply_type.enum_radio_module_reply_none)
					{
						if (module_reply==enum_module_reply_type.enum_radio_module_reply_set_protect_code)
						{
							Message m = Message.obtain(mHandler, MESSAGE_LOG);
							m.obj = "Crypt code just written is: " + this.asac_radio_module_commands.read_radio_crypt()+ "; resetting the radio module...";
							mHandler.sendMessage(m);

							radio_req = asac_radio_module_commands.build_save_module_parameters();
							msg_to_transmit.message = radio_req.getBytes();
							msg_to_transmit.numBytesInMessage = radio_req.length();
							asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module, msg_to_transmit, msg_received);
							// wait while the radio module resets
							try {
								Thread.sleep(2000);
							} catch (InterruptedException e)
							{
								e.printStackTrace();
							}
						}
					}
				}
				// execute at+asaci
				msg_string=asac_radio_module_commands.build_get_module_info();
				b_is_radio_module_command=true;
			}


		}
		// eed set all of the eeprom parameters to the default values
		// INTERNAL RADIO PARAMETERS: Coef=-26,Pwr=0,Ramp=0,SizePr=31,Squeelch=0,Dev=120,BW=0
		// channel =1
		// radio mode = 12 (TDA7)
		// protect code = 1
		// terminal number = 1
		// protocol = 0 (BAREBONE)
		else if (text_input.startsWith("eed")){

			b_hide_onscreen_keyboard = true;

			// set radio internal parameters to default
			String radio_req = asac_radio_module_commands.build_set_radio_internal_params_at_default();
			msg_to_transmit.message = radio_req.getBytes();
			msg_to_transmit.numBytesInMessage = radio_req.length();
			asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module, msg_to_transmit, msg_received);


			// set radio channel to default
			radio_req = asac_radio_module_commands.build_set_radio_channel_at_default();
			msg_to_transmit.message = radio_req.getBytes();
			msg_to_transmit.numBytesInMessage = radio_req.length();
			asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module, msg_to_transmit, msg_received);

			// reset radio mode at 12 (TDA7 / TYPHOON module), protect code to 1, terminal number to 1, protocol type to 0=barebone
			radio_req = "AT+ASACMOD=12,1,1,0";
			msg_to_transmit.message = radio_req.getBytes();
			msg_to_transmit.numBytesInMessage = radio_req.length();
			asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module, msg_to_transmit, msg_received);

			radio_req = "AT+ASACSAVE";
			msg_to_transmit.message = radio_req.getBytes();
			msg_to_transmit.numBytesInMessage = radio_req.length();
			asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module, msg_to_transmit, msg_received);
			// wait while the NOR resets
			try {
				Thread.sleep(1500);
			} catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			msg_string=asac_radio_module_commands.build_get_module_info();
			b_is_radio_module_command=true;

		}
		else if (text_input.contentEquals("tf")){
//AT+ASACMOD=2,1
// AT+ASACCHANNEL=40,0
			b_hide_onscreen_keyboard=true;
			String radio_req="AT+ASACMOD=2,1";
			msg_to_transmit.message=radio_req.getBytes();
			msg_to_transmit.numBytesInMessage=radio_req.length();
			asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module,msg_to_transmit,msg_received);
			radio_req="AT+ASACCHANNEL=40,0";
			msg_to_transmit.message=radio_req.getBytes();
			msg_to_transmit.numBytesInMessage=radio_req.length();
			asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module,msg_to_transmit,msg_received);
			// execute at+asaci
			msg_string=asac_radio_module_commands.build_get_module_info();
			b_is_radio_module_command=true;

		}
		else if (text_input.startsWith("tc"))
		{
			if (text_input.length()>2)
			{
				int iChannelValue = Integer.parseInt(text_input.substring(2, text_input.length()));
				b_hide_onscreen_keyboard = true;
				//    AT+ASACRADIO=<numero>,0,0,31,0,120,0
				String radio_req="AT+ASACRADIO="+iChannelValue+",0,0,31,0,120,0";
				msg_to_transmit.message=radio_req.getBytes();
				msg_to_transmit.numBytesInMessage=radio_req.length();
				asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module,msg_to_transmit,msg_received);
				// execute at+asaci
				msg_string=asac_radio_module_commands.build_get_module_info();
				b_is_radio_module_command=true;
			}

		}
		else if (text_input.startsWith("nav0"))
		{
			boolean is_ok = true;
			if (false)
			{
				RadioRF radioRF = (RadioRF)getSystemService(SerialChat.RADIORF_SERVICE);
				try
				{
					radioRF.NavBarOff();

				}
				catch(RemoteException e)
				{
					is_ok= false;
				}
			}
			else
			{
				if (false) {
					boolean my_err = false;
					Process p;
					try {
						// Preform su to get root privileges
						p = Runtime.getRuntime().exec("/system/bin/su");

						// Attempt to write a file to a root-only
						DataOutputStream os = new DataOutputStream(p.getOutputStream());
						os.writeBytes("echo test >/data/local/temporary.txt\n");

						// Close the terminal
						os.writeBytes("exit\n");
						os.flush();
						try {
							p.waitFor();
							if (p.exitValue() != 255) {
								// TODO Code to run on success
								my_err = true;
								//toastMessage("root");
							} else {
								// TODO Code to run on unsuccessful
								//toastMessage("not root");
								my_err = true;
							}
						} catch (InterruptedException e) {
							// TODO Code to run in interrupted exception
							//toastMessage("not root");
							my_err = true;
						}
					} catch (IOException e) {
						// TODO Code to run in input/output exception
						//toastMessage("not root");
						my_err = true;
					}
				}
				// Hide the nav bar
				try
				{
					String command;
					//command = "LD_LIBRARY_PATH=/vendor/lib:/system/lib service call activity 42 s16 com.android.systemui";
					command = "service call activity 42 s16 com.android.systemui";

					ArrayList<String> envlist = new ArrayList<String>();
					Map<String, String> env = System.getenv();
					for (String envName : env.keySet()) {
						envlist.add(envName + "=" + env.get(envName));
					}
					String[] envp = (String[]) envlist.toArray(new String[0]);
					Process proc = Runtime.getRuntime().exec(new String[] { "/system/bin/su", "-c", command }, envp);
					proc.waitFor();
				}
				catch(Exception ex)
				{

				}
			}
		}
		else if (text_input.startsWith("nav1"))
		{
			boolean is_ok = true;
			if (false)
			{
				RadioRF radioRF = (RadioRF)getSystemService(SerialChat.RADIORF_SERVICE);
				try {
					radioRF.NavBarOn();

				}
				catch(RemoteException e)
				{
					is_ok = false;
				}
			}
			else {
				// Hide the nav bar
				try {
					String command;
					//command = "LD_LIBRARY_PATH=/vendor/lib:/system/lib am startservice -n com.android.systemui/.SystemUIService";
					command = "am startservice -n com.android.systemui/.SystemUIService";

					ArrayList<String> envlist = new ArrayList<String>();
					Map<String, String> env = System.getenv();
					for (String envName : env.keySet()) {
						envlist.add(envName + "=" + env.get(envName));
					}
					String[] envp = (String[]) envlist.toArray(new String[0]);
					Process proc = Runtime.getRuntime().exec(new String[]{"/system/bin/su", "-c", command}, envp);
					proc.waitFor();
				} catch (Exception ex) {

				}
			}
		}
		else if (text_input.contentEquals("ts"))
		{
			String radio_req = "AT+ASACMOD=12,1,1,0";
			msg_to_transmit.message = radio_req.getBytes();
			msg_to_transmit.numBytesInMessage = radio_req.length();
			asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module, msg_to_transmit, msg_received);
			// reset radio mode at 12 (TDA7 / TYPHOON module), protect code to 1, terminal number to 1, protocol type to 0=barebone
			radio_req = "AT+ASACSAVE";
			msg_to_transmit.message = radio_req.getBytes();
			msg_to_transmit.numBytesInMessage = radio_req.length();
			asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module, msg_to_transmit, msg_received);
			// wait while the NOR resets
			try {
				Thread.sleep(1500);
			} catch (InterruptedException e)
			{
				e.printStackTrace();
			}
			msg_string=asac_radio_module_commands.build_get_module_info();
			b_is_radio_module_command=true;

		}
		// "f" sends a test file
		else if (text_input.contentEquals("f")){
			if (run_sendFile.is_running()){
				run_sendFile.stop();
			}
			else{
				try{
					new Thread(run_sendFile).start();
				}catch (IllegalThreadStateException	e){
		        	Log.e(TAG, "the send file thread can' t be started");
		        }
			}
			//asac_radio_transport_layer.sendFile("/data/user/gg.png", "gg.png", 2000);
		}
		// use "load" keyword to load a remote file
		// the remote file has name "send_tda7.txt"
		else if (text_input.contentEquals("load")){
			b_hide_onscreen_keyboard=true;
			// set the type of receive to load
			receive_file_test.setKind_of_receive(EnumReceiveFileMode.load);
			// thread start
			try{
				// starts the receiving thread run() which waits for the incoming messages
		        new Thread(receive_file_test).start();
			}catch (IllegalThreadStateException	e){
	        	Log.e(TAG, "the receiving file thread can' t be started");
	        }
		}
		// use "update" keyword to update a remote file
		// the remote file has name "send_tda7.txt"
		else if (text_input.contentEquals("update")){
			b_hide_onscreen_keyboard=true;
			// set the type of receive to update
			receive_file_test.setKind_of_receive(EnumReceiveFileMode.update);
			// thread start
			try{
				// starts the receiving thread run() which waits for the incoming messages
		        new Thread(receive_file_test).start();
			}catch (IllegalThreadStateException	e){
	        	Log.e(TAG, "the receiving file thread can' t be started");
	        }
		}
		// use "loopfile" to have a transmit and receive file loop with check of file contents at every loop
		else if (text_input.contentEquals("loopfile")){
			b_hide_onscreen_keyboard=true;
			if (loop_send_receive_file_test.is_running()){
				loop_send_receive_file_test.stop();
			}
			else{
				// thread start
				try{
					// starts the receiving thread run() which waits for the incoming messages
			        new Thread(loop_send_receive_file_test).start();
				}catch (IllegalThreadStateException	e){
		        	Log.e(TAG, "the tx_and_rx file thread can' t be started");
		        }	
			}
		}
		else if (text_input.startsWith("termnr"))
		{
			InputMethodManager inputManager = (InputMethodManager)
					getSystemService(SerialChat.INPUT_METHOD_SERVICE);

			inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
					InputMethodManager.HIDE_NOT_ALWAYS);
			int termnr = Integer.parseInt(text_input.substring(6, text_input.length()));
			this.asac_radio_transport_layer.set_terminal_number((byte)termnr);
			int read_termnr = this.asac_radio_transport_layer.get_terminal_number();
			Message m = Message.obtain(mHandler, MESSAGE_LOG);
			String mytext = "terminal number set as " + read_termnr;
			m.obj = mytext;
			mHandler.sendMessage(m);

		}

		// use "u" to have a transmit and receive communication with a diagnostic message
		// UF100 executes 100 loops switching off the radio between the transmissions
		// UN35 executes 35 loops leaving ON the radio between the transmissions
		else if (text_input.startsWith("u")){
			InputMethodManager inputManager = (InputMethodManager)
					getSystemService(SerialChat.INPUT_METHOD_SERVICE);

			inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
					InputMethodManager.HIDE_NOT_ALWAYS);
			if (tx_and_rx_message.is_running()){
				tx_and_rx_message.stop();
			}
			else{
				int numloops = 100;
				boolean radio_on= true;
				if (text_input.length()>2) {
					String a = text_input.substring(1, 2);
					if (a.equalsIgnoreCase("f")){
						radio_on = false;
					}
				}
				tx_and_rx_message.set_radio_off_between_txrx(radio_on);
				if (text_input.length()>2) {
					numloops = Integer.parseInt(text_input.substring(2, text_input.length()));
				}
				// thread start
				try{
					tx_and_rx_message.setI_num_tx_and_rx_loops(numloops);
					// starts the receiving thread run() which waits for the incoming messages
			        new Thread(tx_and_rx_message).start();
				}catch (IllegalThreadStateException	e){
		        	Log.e(TAG, "the tx_and_rx message thread can' t be started");
		        }	
			}
		}
		else if (text_input.contentEquals("alrt"))
		{
			Context context = this;
			final AlertDialog.Builder inputAlert = new AlertDialog.Builder(context);
			inputAlert.setTitle("Title of the Input Box");
			inputAlert.setMessage("We need your name to proceed");
			final EditText userInput = new EditText(context);
			inputAlert.setView(userInput);
			inputAlert.setPositiveButton("Submit", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					String userInputValue = userInput.getText().toString();
				}
			});
			inputAlert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					dialog.dismiss();
				}
			});
			AlertDialog alertDialog = inputAlert.create();
			alertDialog.show();
		}
		// use "h" to have a transmit and receive communication with a diagnostic message x 100 times
		else if (text_input.contentEquals("h")){
			if (tx_and_rx_message.is_running()){
				tx_and_rx_message.stop();
			}
			else{
				// thread start
				try{
					tx_and_rx_message.setI_num_tx_and_rx_loops(100);
					// starts the receiving thread run() which waits for the incoming messages
			        new Thread(tx_and_rx_message).start();
				}catch (IllegalThreadStateException	e){
		        	Log.e(TAG, "the tx_and_rx message thread can' t be started");
		        }	
			}
		}
		// "d" toggles diagnostic mode
		else if (text_input.contentEquals("d")){
			b_hide_onscreen_keyboard=true;
			if (this.diagnostic_thread.isrunning()){
				// stop the thread
				this.diagnostic_thread.kill(2000);
				// send a message to the log screen
                String mytext = String.format(Locale.US,"DIAGNOSTIC THREAD STOPPED\n");
        		Message m = Message.obtain(mHandler, MESSAGE_LOG);
        		m.obj = mytext;
                mHandler.sendMessage(m);
			}
			else{
				this.asac_radio_transport_layer.logEnable.enable(EnumTransportLogEnable.data_link_layer_receiving_thread);
				// use short message to check whether the radio goes to search mode due to the message length
				// it do not depends upon the message length; still unknown cause
				//asac_radio_transport_layer.diagnostic_thread.set_message_length(40);
				// the minimum sleep is 20ms = 10ms tx time+10ms rx time
				this.diagnostic_thread.start();
				// send a message to the screen
                String mytext = String.format(Locale.US,"DIAGNOSTIC THREAD STARTS\n");
        		Message m = Message.obtain(mHandler, MESSAGE_LOG);
        		m.obj = mytext;
                mHandler.sendMessage(m);
			}
			
		}
		// "ver" --> NOR module firmware version
		else if (text_input.contentEquals("ver")){
			msg_string=asac_radio_module_commands.build_get_radio_firmware_version();
			b_is_radio_module_command=true;
		}
		// "i" --> info
		else if (text_input.contentEquals("i")){
			msg_string=asac_radio_module_commands.build_get_module_info();
			b_is_radio_module_command=true;
		}
		// "a" --> get radio status
		else if (text_input.contentEquals("a")){
			msg_string=asac_radio_module_commands.build_get_radio_status(false);
			b_is_radio_module_command=true;
		}
		// "t" --> test
		else if (text_input.contentEquals("t")){
			msg_string=asac_radio_module_commands.build_module_test();
			b_is_radio_module_command=true;
		}
		// "s" --> save radio parameters
		else if (text_input.contentEquals("s")){
			msg_string=asac_radio_module_commands.build_save_module_parameters();
			b_is_radio_module_command=true;
		}
		// g= get radio parameters--> not yet implemented on radio module
		//else if (text.matches("g")){
		//	msg_string=asac_radio_module_commands.build_get_module_parameters();
		//	b_is_radio_module_command=true;
		//}
		// "r"= reset radio module
		else if (text_input.matches("r")){
			msg_string=asac_radio_module_commands.build_reset_module();
			b_is_radio_module_command=true;
		}
		if (b_is_radio_module_command){
			b_hide_onscreen_keyboard=true;
		}
		if (b_hide_onscreen_keyboard){
			InputMethodManager inputManager = (InputMethodManager)
	                getSystemService(SerialChat.INPUT_METHOD_SERVICE); 
	
			inputManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
	                   InputMethodManager.HIDE_NOT_ALWAYS);
		}
		if (b_is_radio_module_command){
			msg_to_transmit.message=msg_string.getBytes();
			msg_to_transmit.numBytesInMessage=msg_string.length();
			EnumTransmitAndReceiveReturnCode retcode;
			retcode=asac_radio_transport_layer.transmitAndReceive(enum_rnl_message_destination_type.message_destination_radio_module,msg_to_transmit,msg_received);
			if (retcode==EnumTransmitAndReceiveReturnCode.ok){
				Message m = Message.obtain(mHandler, MESSAGE_LOG);
                String mytext = new String(msg_received.message, 0, msg_received.numBytesInMessage);
                StringBuilder reply=new StringBuilder();
                enum_module_reply_type module_reply;
                module_reply=asac_radio_module_commands.check_responses(mytext,reply);
                // if there is a reply from the module, printout the reply from the module check
        		if (module_reply!=enum_module_reply_type.enum_radio_module_reply_none){
        			mytext=reply.toString();
        			if (module_reply==enum_module_reply_type.enum_radio_module_reply_radio_firmware_version){
        				mytext+="\n\nFirmware version: "+asac_radio_module_commands.getFirmwareVersion();
        				mytext+="\nFirmware date: "+asac_radio_module_commands.getFirmwareDate();
        				mytext+="\nFirmware time: "+asac_radio_module_commands.getFirmwareTime()+"\n";
        			}
        		}
        		// else printout the message received
        		else{
        			mytext="T:"+msg_received.i_from_terminal_number+" rx:"+mytext;
        		}
        		// refresh statistics
        		asac_radio_transport_layer.refreshTxRxStatistics();
        		// read statistics
                rx_stats=asac_radio_transport_layer.receiveStatistics;
                tx_stats=asac_radio_transport_layer.transmitStatistics;
                mytext=mytext+"\n"+"tx ok:"+tx_stats.i_num_msg_tx_retcodes[EnumRnlReturnCode.ok.ordinal()]+", tx too long body:"+tx_stats.i_num_msg_tx_retcodes[EnumRnlReturnCode.too_long_body.ordinal()];
                mytext=mytext+"\n"+"rx ok:"+rx_stats.i_num_msg_rx_retcodes[enum_rnl_rx_return_code.ok.ordinal()]+", rx crc err:"+rx_stats.i_num_msg_rx_retcodes[enum_rnl_rx_return_code.bad_crc.ordinal()];
                mytext=mytext+"\n"+"rx too long body:"+rx_stats.i_num_msg_rx_retcodes[enum_rnl_rx_return_code.too_long_body.ordinal()]+", rx no etx:"+rx_stats.i_num_msg_rx_retcodes[enum_rnl_rx_return_code.missing_etx_at_message_end.ordinal()];
                Log.d(TAG, "chat: " + mytext);
                m.obj = mytext;
                mHandler.sendMessage(m);				
			}
			else{
				Message m = Message.obtain(mHandler, MESSAGE_LOG);
                String mytext = "error doing command "+msg_string;
                m.obj = mytext;
                mHandler.sendMessage(m);				
				
			}
			//public enum_rtl_txrx_return_code transmitAndReceive(enum_rnl_message_destination_type destination, Asac_radio_network_layer_message msg_transmit, Asac_radio_network_layer_message msg_received){
		    
		}
        return true;
    }

    public void run() {
        Log.d(TAG, "run");
        int ret = 0;
        
        while (ret >= 0) {
        	
        	try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        	if (this.diagnostic_thread.new_diagnostic_stats_available() || this.diagnostic_thread.b_new_radio_status_avail()){
        		Message m = Message.obtain(mHandler, MESSAGE_LOG);
                String mytext = String.format(Locale.US,"DIAGNOSTIC THREAD STATISTICS\nMSGS/s act: %5.2f; avg: %5.2f; max %5.2f\nduration: %s seconds\nsent: %d, received: %d\nmessage length: %d"
            			,this.diagnostic_thread.get_act_diagnostic_speed_msgs_per_s()
            			,this.diagnostic_thread.get_avg_diagnostic_speed_msgs_per_s()
            			,this.diagnostic_thread.get_max_diagnostic_speed_msgs_per_s()
            			,this.diagnostic_thread.get_diagnostic_thread_duration_second()
            			,(int)this.diagnostic_thread.getL_num_msg_tx()
            			,(int)this.diagnostic_thread.getL_num_msg_rx()
            			,(int)this.diagnostic_thread.get_message_length()
                );
                mytext=mytext+String.format(Locale.US,"\nRadio mode: %s",this.diagnostic_thread.getWork_mode());
                mytext=mytext+String.format(Locale.US,"\nRadio strength: %s",this.diagnostic_thread.getRadio_strength());
                mytext=mytext+String.format(Locale.US,"\nNum times search mode: %s",this.diagnostic_thread.getNum_times_radio_search_mode());
                mytext=mytext+String.format(Locale.US,"\nNOR queue OK: %d; NOR queue KO: %d",this.diagnostic_thread.getNum_radio_module_queued_messages_OK(),this.diagnostic_thread.getNum_radio_module_queued_messages_KO());
                mytext=mytext+String.format(Locale.US,"\nNOR queue FULL times: %d",this.diagnostic_thread.getNum_times_NOR_queue_full());
        		m.obj = mytext;
                mHandler.sendMessage(m);
        	}
        	if (!queue_receive_file_messages.isEmpty()){
        		Message m = Message.obtain(mHandler, MESSAGE_LOG);
                String mytext;
				try {
					mytext = queue_receive_file_messages.take();
	        		m.obj = mytext;
	                mHandler.sendMessage(m);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
        		
        	}

        }
        Log.d(TAG, "thread out");
    }

   @SuppressLint("HandlerLeak")
   Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_LOG:
                	if (i_receive_messages_from_radio){
                		mLog.setText((String)msg.obj);
                	}
                	else{
	                	if (mLog.getText().length()+((String)msg.obj).length()<512){
	                		mLog.setText(mLog.getText() + (String)msg.obj);
	                	}
	                	else{
	                		mLog.setText((String)msg.obj);
	                	}
                	}
                    break;
             }
            //super.handleMessage(msg);
        }
    }
   ;
}


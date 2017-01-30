package asac_radio_data_link_layer;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

import android.os.SystemProperties;
import android.util.Log;

import android.content.Context;
import android.hardware.SerialManager;
import android.hardware.SerialPort;
// this class defines the structures exchanged between data link layer and network layer
import asac_radio_network_layer_message.Asac_radio_network_layer_message;


import java.util.Locale;
//import java.nio.ByteBuffer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.io.IOException;

import asac_chars_rx_from_serial.Asac_chars_rx_from_serial;

// implements a thread to receive bytes chunks from the serial port; the chunks are queued 
// 			  a method to transmit bytes to the serial port

/**
 * implements a thread to receive bytes chunks from the serial port; the chunks are queued for the upper layer
 * also implements a method to send bytes to the serial port
 * @author root
 *
 */
public class Asac_radio_data_link_layer implements Runnable{
    // the size of the input/output buffers
    private final int IOBUFFERS_SIZE=1024;
    // the serial port baud rate
    private final int SERIAL_PORT_BAUDRATE=115200;
    // a semaphore to handle writing on the serial port
    private final Lock write_semaphore = new ReentrantLock(true);
	// our class tag
	private static final String TAG = "A_rdll";
	// the serial manager handle
	private SerialManager mSerialManager;
	// the serial port handle
    private SerialPort mSerialPort;	
    // the input buffer
    private ByteBuffer mInputBuffer;
    // the output buffer
    private ByteBuffer mOutputBuffer; 
    // the queue where we'll put the bytes chunk we receive
	private BlockingQueue<Asac_chars_rx_from_serial> queue;
    // the context: needed for the getSystemService API
    private Context mContext;
	private Thread thread = null;

    /**
     * the data link layer constructor
     * @param m_the_Context which is needed to access the serial service
     * @param q he queue where to put the characters received from the serial port
     * @throws RuntimeException
     */
    public Asac_radio_data_link_layer(Context m_the_Context,BlockingQueue<Asac_chars_rx_from_serial> q) throws RuntimeException{
    	Log.i(TAG,"constructor begins");
    	// assign the queue
    	this.queue=q;
    	// assign the context
    	mContext = m_the_Context;
    	// set the serial manager
    	mSerialManager=null;
    	// set buffer pointers
    	mInputBuffer=null;
    	mOutputBuffer=null;
    	try{
    		mSerialManager = (SerialManager)mContext.getSystemService(Context.SERIAL_SERVICE);
    	}catch(NullPointerException e ){
        	Log.e(TAG, "error from get serial service... wrong permissions? no serial port?", e);
        	throw new RuntimeException("Unable to access serial system service;",e);
        }
    	if (mSerialManager==null){
    		Log.e(TAG, "serial manager has not been set, can't continue");
    		throw new RuntimeException("Unable to get serial system service");
    	}
        // allocate input and output buffers
    	try{
    		mInputBuffer = ByteBuffer.allocate(IOBUFFERS_SIZE);
    	}catch(IllegalArgumentException e){
        	Log.e(TAG, "error allocating input buffer:", e);
        	throw new RuntimeException("Unable allocation size of input buffer;",e);
    	}
        if (mInputBuffer==null){
        	Log.e(TAG, "cannot allocate input buffer");
        	throw new RuntimeException("Unable to allocate input buffer");
        }
        try{
        	mOutputBuffer = ByteBuffer.allocate(IOBUFFERS_SIZE);
        }catch(IllegalArgumentException e){
        	Log.e(TAG, "error allocating output buffer:", e);
        	throw new RuntimeException("Unable allocation size of output buffer;",e);
    	}
    	if (mOutputBuffer==null){
        	Log.e(TAG, "cannot allocate output buffer");
        	throw new RuntimeException("Unable to allocate output buffer");
        }
    	mSerialPort=null;

    	// read the COM port to use from System properties
    	// BTW you got to use the SystemProperties.get if you want to read a system property 
    	String radioRF_port=null;
    	// let's try open the serial port described in init.rc
    	radioRF_port=SystemProperties.get("ro.radioRF.COMport");    	
    	// if OK
    	if (radioRF_port!=null && radioRF_port.length()>0){
            try {
            	// fixed baud rate 115'200
            	// I get the first serial port, better to use the one set in the system properties
                mSerialPort = mSerialManager.openSerialPort(radioRF_port, SERIAL_PORT_BAUDRATE);
            } catch (IOException e) {
            	Log.e(TAG, "IO error while opening the serial port...", e);
            	throw new RuntimeException("Serial port open exception;",e);
            }
    	}
        // we all hope the serial port is allocated OK, but we can try with serial manager COM port list
        if (mSerialPort == null) {
        	Log.e(TAG, "serial port open through system property has failed");
        	Log.e(TAG, "now trying with serial manager list");
            // get serial port list
        	// this method won't throw exceptions; instead, it returns null when errors are detected
            String[] ports = mSerialManager.getSerialPorts();
            // by now, always opens the first serial port
            if (ports != null && ports.length > 0) {
                try {
                	// fixed baud rate 115'200
                    mSerialPort = mSerialManager.openSerialPort(ports[0], SERIAL_PORT_BAUDRATE);
                    // we all hope the serial port is allocated OK
                    if (mSerialPort == null) {
                    	Log.e(TAG, "serial port open has failed");
                    	throw new RuntimeException("Serial port open failed");
                    }
                } catch (IOException e) {
                	Log.e(TAG, "IO error while opening the serial port...", e);
                	throw new RuntimeException("Serial port open exception;",e);
                }
            }        
            else{
            	Log.e(TAG, "no available ports from getSerialPorts...");
            	throw new RuntimeException("No serial ports available from getSerialPorts");
            }
        }
        // here, mSerialPort should be set
        if (mSerialPort==null){
        	throw new RuntimeException("unable to open a serial port for radio RF");
        }
        try{
        	// starts the receiving thread
            thread = new Thread(this);
			thread.start();
        }catch (IllegalThreadStateException	e){
        	Log.e(TAG, "the receiving thread can' t be started");
        	throw new RuntimeException("Unable to start receiving thread");
        }
        log_enable= new Log_data_link_layer_enable();
        Log.i(TAG,"constructor ends OK");
    }	
    
    /**
     * waits until all of the bytes in the serial port buffer have been transmitted
     * from what I have seen so far, this is normally not necessary
     */
    public void sync(){
    	mSerialPort.sync();
    }

    /**
     * the routines/threads that can stream an information log
     * @author root
     *
     */
 	public enum enum_ardl_log_enable{
 		receiving_thread,
 		write_bytes_to_serial_port,
 	};
 	
 	/**
 	 * handles the logging for the data link layer class
 	 * @author root
 	 *
 	 */
    public class Log_data_link_layer_enable{
    	private boolean[] b_enable;
    	public Log_data_link_layer_enable(){
    		this.b_enable= new boolean[enum_ardl_log_enable.values().length];
    		this.disable_all();
    	}
    	public void disable_all(){
    		for (enum_ardl_log_enable e:enum_ardl_log_enable.values()){
    			this.enable_disable(e, false);
    		}
    	}
    	public boolean is_enabled(enum_ardl_log_enable e){
    		return this.b_enable[e.ordinal()];
    	}
    	public void enable_disable(enum_ardl_log_enable e, boolean value){
    		this.b_enable[e.ordinal()]=value;
    		switch(e){
	    		default:
	    		{
	    			break;
	    		}
    		}
    	}
    	public void enable(enum_ardl_log_enable e){
    		this.enable_disable(e,true);
    	}
    	public void disable(enum_ardl_log_enable e){
    		this.enable_disable(e,false);
    	}
    }
    /**
     * the object which stores and handles the logging
     */
    public Log_data_link_layer_enable log_enable;
    
    /**
     * write the input bytes to the serial port
     * @param the_bytes the byte array containing the bytes to write on the serial port
     * @param i_num_of_bytes the number of bytes to write
     * @throws RuntimeException
     */
    private void v_write_bytes_to_serial(byte[] the_bytes, int i_num_of_bytes) throws RuntimeException{
    	final String localTag=TAG+" write bytes";
    	// lock the serial port writing semaphore
	    write_semaphore.lock();
	    // just to be sure to always make the unlock
	    try{
	    	if (log_enable.is_enabled(enum_ardl_log_enable.write_bytes_to_serial_port)){
				Log.i(localTag,"about to write "+String.valueOf(i_num_of_bytes)+" bytes");
				if (i_num_of_bytes>0){
					final int i_max_char_per_col=16;
					final int i_max_rows=16;
					int i_num_row=i_num_of_bytes/i_max_char_per_col;
					if ((i_num_of_bytes%i_max_char_per_col)>0){
						i_num_row++;
					}
					if (i_num_row>i_max_rows){
						i_num_row=i_max_rows;
						Log.i(localTag,"only first "+String.valueOf(i_num_row)+" shown");
					}
					int i_num_bytes_read=0;
					for (int row=0;row<i_num_row;row++){
						String s="";
						for (int col=0;col<i_max_char_per_col;col++){
							if (i_num_bytes_read>=i_num_of_bytes){
								break;
							}
							s=s+String.format(Locale.US,"%2X ",the_bytes[i_num_bytes_read]);
							i_num_bytes_read++;
						}
						if (s.length()>0){
							Log.i(localTag,s);
						}
					}
				}
			}	    	
	        mOutputBuffer.clear();
	    	// put the characters on the buffer
	    	try{
		        mOutputBuffer.put(the_bytes);
	    	}
	    	catch (BufferOverflowException e) {
	            throw new RuntimeException("Buffer overflow",e);
	        }
	    	catch (ReadOnlyBufferException e) {
	            throw new RuntimeException("Buffer is read only",e);
	        }
	    	// output characters' buffer on the serial port
	    	try{
		        mSerialPort.write(mOutputBuffer, i_num_of_bytes);
	    	}
	    	catch (IOException e) {
	            throw new RuntimeException("IO write failed",e);
	        }
	    	catch (IllegalArgumentException e) {
	            throw new RuntimeException("Buffer is not valid",e);
		    }
	    }
    	finally {
	    	// at the end, always unlock the semaphore
	    	write_semaphore.unlock();
	    }
    }
    
    /**
     * write a message to the serial port
     * @param msg the message to write
     * @throws RuntimeException
     */
    public void put_message(Asac_radio_network_layer_message msg) throws RuntimeException{
    	// call the API which handles writing on the serial port
    	v_write_bytes_to_serial(msg.message, msg.numBytesInMessage);
    }
    
    /**
     * useful to close the serial port before destroying the object
     */
    public void stop() {
    	Log.i(TAG,"destroy calling"); 
        if (mSerialPort != null) {
        	Log.d(TAG,"trying to close the serial port"); 
            try {
                mSerialPort.close();
            } catch (IOException e) {
            	Log.e(TAG, "close failed", e);
            }
            mSerialPort = null;
        }
		if (this.thread != null)
		{
			this.thread.interrupt();
			this.thread = null;
		}

    	Log.i(TAG,"end of destroy calling"); 

    }
    
    /**
     * the enumerative containing the receiving thread statuses
     * @author root
     *
     */
    private enum enum_rx_thread_status{
    	init,
    	check_queue,  // the destination of the message is the server i.e. the pc
    	rx, //the destination of the message is the radio module
	};        

	/**
	 * the receiving characters thread
	 */
    public void run() {
		final String localTag=TAG+" receiving thread";
    	int ret=0;
    	// allocate the byte buffer we use as destination copy from mInputBuffer
        byte[] buffer = new byte[IOBUFFERS_SIZE];
        enum_rx_thread_status rx_thread_status;
        rx_thread_status=enum_rx_thread_status.init;
    	Log.i(TAG,"receiving thread begins");
        // a read with return code<0 means very bad problems happened 
    	while (ret >= 0) {
    		switch(rx_thread_status){
	    		case init:
	    		{	
	    			rx_thread_status=enum_rx_thread_status.check_queue;
	    			break;
	    		}
	    		case check_queue:
	    		{
	        		// if there is no room for other elements in the queue, loop waiting for free elements
	        		if (queue.remainingCapacity()<1){
	        			//Pause for some milliseconds
	                    try {
	    					Thread.sleep(100);
	    				} catch (InterruptedException e) {
	    				}
	        		}
	        		else{
	        			rx_thread_status=enum_rx_thread_status.rx;
	        		}
	    			break;
	    		}
	    		case rx:
	    		{
	    			if (queue.remainingCapacity()<1){
	        			if (log_enable.is_enabled(enum_ardl_log_enable.receiving_thread)){
							Log.i(localTag,"sleeping because queue is almost full");
						}
	    				rx_thread_status=enum_rx_thread_status.check_queue;
	    				break;
	    			}
	    			// clear the input buffer
	    			mInputBuffer.clear();
	        		try {
	        			// read some characters from input buffer
	        			ret = mSerialPort.read(mInputBuffer);
	        		}
	            	catch (IOException e) {
	                    throw new RuntimeException("IO read failed",e);
	                }
	            	catch (IllegalArgumentException e) {
	                    throw new RuntimeException("Buffer is not valid while reading from serial port",e);
	            	}
	        		// good characters received?
	        		if (ret>0){
	    	    		try{
	    	    			// copy the characters received into a byte array
	    	    			mInputBuffer.get(buffer, 0, ret);	     
	    	    		}
	    	    		catch (IndexOutOfBoundsException e) {
	    	    			Log.e(TAG, "index error while copying received bytes", e);
	    	    			throw new RuntimeException("Index error while copying received bytes", e);
	    	    		}
	    	    		catch (BufferUnderflowException e) {
	    	    			Log.e(TAG, "buffer underflow while copying received bytes", e);
	    	    			throw new RuntimeException("Buffer underflow while copying received bytes", e);
	    	    		}
	    	    		// queue the buffer read!
	    	    		try{
	    	    			// put a newly created object containing the number of bytes received (and with same length BTW)
	    	    			queue.put(new Asac_chars_rx_from_serial(ret,buffer));
		        			if (log_enable.is_enabled(enum_ardl_log_enable.receiving_thread)){
								Log.i(localTag,"queued "+String.valueOf(ret)+" chars rx from radio");
//	    	    				String s_buffer = new String(buffer, "UTF-8");  // example for one encoding type
//		    	    			int i_nc=ret;
//		    	    			final int i_max_char_to_show_up=32;
//		    	    			// it shows up the first N characters
//		    	    			if (i_nc>i_max_char_to_show_up){
//		    	    				i_nc=i_max_char_to_show_up;
//		    	    			}
//		    	    			// clip to max length
//		    	    			if (i_nc>s_buffer.length()){
//		    	    				i_nc=s_buffer.length();
//		    	    			}
//		    	    			// log the info about the first N characters
//		    	    			Log.i(localTag,"first " + String.valueOf(i_nc)+" chars:" +s_buffer.substring(0, i_nc));
	    	    			}
	    	    		}catch (InterruptedException e) {
	    	    			// some characters will be lost, maybe it is better to not generate an exception?
	    	    			Log.e(TAG, "queue put failed", e);
	    	            }catch (ClassCastException e) {
	    	    			Log.e(TAG, "wrong element type added to the queue", e);
	    	            }catch (NullPointerException e) {
	    	    			Log.e(TAG, "null pointer buffer added to the queue", e);
	    		        }catch (IllegalArgumentException e) {
	    					Log.e(TAG, "illegal buffer added to the queue", e);
	    		        }catch (Exception e) {
	    					Log.e(TAG, "unhandled exception detected!", e);
	    		        }
	        		}
	    			break;
	    		}// case rx
    		}//switch
    	}// thread loop
    	if (ret<0){
	    	Log.e(TAG,"receiving thread loop unexpected end");
	    	throw new RuntimeException("receiving thread unexpected end");
    	}
    	else{
    		Log.i(TAG,"receiving thread loop ends");
    	}
    }

}

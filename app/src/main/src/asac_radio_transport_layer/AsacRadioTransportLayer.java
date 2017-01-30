
package asac_radio_transport_layer;

//import java.io.FileNotFoundException;
import java.io.FileNotFoundException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.IllegalFormatException;
import java.util.LinkedList;
import java.util.Locale;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import asacIntents.AsacIntents;
import asacLEDs.Asac_LEDs;
import asacRadioRFPower.Asac_radioRF_power;
import messageWithIdQueue.IdQueue;

//import android.provider.MediaStore.Files;
import android.content.Intent;
import android.util.Log;


import android.content.Context;
import asac_radio_network_layer.AsacRadioNetworkLayer;
import asac_radio_network_layer.AsacRadioNetworkLayer.AsacRadioMessageFromTerminal;
import asac_radio_network_layer.AsacRadioNetworkLayer.enum_rnl_message_destination_type;
import asac_radio_network_layer.AsacRadioNetworkLayer.EnumRnlReturnCode;
import asac_radio_network_layer_message.Asac_radio_network_layer_message;
import asac_radio_transport_layer.LogTransportLayer.EnumTransportLogEnable;
import byteArrayUtils.*;
/**
 * The transport layer of the ASAC radio stack
 * @author root
 *
 */

public class AsacRadioTransportLayer {
	// our class tag
	private static final String TAG = "transport_layer";
	// enable a sync when sending files? this has been found as non necessary
	private final boolean ENABLE_SERIAL_PORT_SYNC_ON_SENDFILE=false;
	// the character used to differentiate between fields
	private final byte FIELD_SEPARATOR=1;
	// a short message can be easily and efficiently transmitted through radio network when its size is like that
    private final int MOST_EFFICIENT_MESSAGE_LENGTH=(110-6);  
 	// max number of messages received from terminal that can be put in queue
 	private final int QUEUE_MESSAGE_SENT_MAX_ELEMENTS=32;
 	// the minimum and maximum admitted terminal number
    private final byte MIN_TERMINAL_NUMBER=1;
    private final byte MAX_TERMINAL_NUMBER=(byte) 127;
	// the first message identifier we will use; we keep it printable to avoid character stuffing ect
    private final int START_ID=' '+1;
    // the maximum message identifier
    private final int MAX_ID=0x7F;
    // the number of message identifiers we can use
    private final int NUM_ID=MAX_ID-START_ID;
    private final String REPEAT_STRING_MESSAGE="REPET:";
	
	// the underlying network layer object
	public AsacRadioNetworkLayer arnl;
	// the currently received message
	private AsacRadioNetworkLayer.AsacRadioMessageFromTerminal radioMessageRx;
	// the queue where the incoming messages are saved
	private BlockingQueue<AsacRadioNetworkLayer.AsacRadioMessageFromTerminal> queue_msgs;
    private IdQueue idQueue;
	
	public TransportParams params;
	// the variable containing the statistics
	public TransportLayerStats stats=new TransportLayerStats();
	public ByteArrayUtils byteArrayUtils;
	
	
	// accumulator for trip time [ms]
	private long sumTripTimeMs=0;
	// accumulator for the number of sent messages
	private long sumTripNum=0;
	// a semaphore to handle access to the network layer
    private final Lock accessNetworkLayer = new ReentrantLock(true);
	
	// radio statistics
    public AsacRadioNetworkLayer.AsacRadioRxStats receiveStatistics;
    public AsacRadioNetworkLayer.Asac_radio_tx_stats transmitStatistics;
    public void refreshTxRxStatistics(){
    	receiveStatistics=arnl.get_rx_stats();
    	transmitStatistics=arnl.get_tx_stats();
    }
    
    // the terminal number
    private byte theTerminalNumber=1;
    
    public LogTransportLayer logEnable;
	private AckNackMessages msgAckNak;
	private Asac_radioRF_power asac_radioRF_power;
	private Context m_the_context;
	// class constructor
	public AsacRadioTransportLayer(Context m_the_context) throws RuntimeException{
		Log.i(TAG,"transport layer constructor");
		this.m_the_context = m_the_context;
	    idQueue=new IdQueue(NUM_ID, START_ID);
	    params=new TransportParams();
	    msgAckNak=new AckNackMessages();
	    byteArrayUtils= new ByteArrayUtils();

		// received messages queue creation
		setQueue_msgs(new ArrayBlockingQueue<AsacRadioNetworkLayer.AsacRadioMessageFromTerminal>(QUEUE_MESSAGE_SENT_MAX_ELEMENTS));
		try{
			asac_radioRF_power = new Asac_radioRF_power(m_the_context, true, Asac_radioRF_power.enum_radio_power_requests.on, 20000);
		}catch(RuntimeException e){
			throw new RuntimeException("cannot handle radio RF power",e);
		}

		// now it creates the underlying layer
		try{
			// creates the underlying network layer
			arnl=new AsacRadioNetworkLayer(m_the_context,getQueueMessages(),idQueue, asac_radioRF_power);
		}catch(RuntimeException e){
			throw new RuntimeException("cannot create network layer",e);
		}

		asac_radioRF_power.is_OK_radio_power_request(Asac_radioRF_power.enum_radio_power_requests.on, Asac_radioRF_power.MAX_DELAY_WAIT_REQUEST_RADIO_MS);
		asac_radioRF_power.is_OK_radio_power_request(Asac_radioRF_power.enum_radio_power_requests.release_on, Asac_radioRF_power.MAX_DELAY_WAIT_REQUEST_RADIO_MS);

        // creates log enable class
        logEnable=new LogTransportLayer(arnl);
		
		// initialize the terminal number of the network layer
		arnl.init_radio_network(getTerminalNumber());
		// initialize the radio message receive
		radioMessageRx= arnl.new AsacRadioMessageFromTerminal();
		// initialize statistics
        receiveStatistics =arnl.new AsacRadioRxStats();
        transmitStatistics =arnl.new Asac_radio_tx_stats();
		Log.i(TAG,"transport layer constructor ends OK");
		
	}	
	
    
    /**
     * lock the network layer; use carefully!
     */
    public void lock(){
    	accessNetworkLayer.lock();
    }
    /**
     * unlock the network layer
     */
    public void unlock(){
    	accessNetworkLayer.unlock();
    }
	
	public void purgeRxQueuesWithoutDelay(){
		final int maxLoop=100;
		int i=0;
		while (!getQueueMessages().isEmpty()){
			try {
				getQueueMessages().take();
			} catch (InterruptedException e) {
			}
			if (++i>maxLoop){
				break;
			}
		}
	}
	
	// this procedure returns true if no messages are coming in radio for l_silence_length_ms, wait for it l_max_wait_ms then return false (radio talks!)
	// IMPORTANT! THE INCOMING MESSAGES ARE PURGED, LOST, UNTIL NO MESSAGES ARRIVE FOR AT LEAST l_silence_length_ms
	// The entire procedure can't last more than l_max_wait_ms
	public boolean bWaitNoMessages(long silenceLengthMs, long maxWaitMs){
		long baseTimeMs=System.currentTimeMillis();
		long baseSilenceTimeMs=System.currentTimeMillis();
		boolean bContinueLoop=true;
		boolean bRadioSilent=false;
		boolean bQueueWasEmpty=false;
		while(bContinueLoop){
			// sleep some ms
			try {
				Thread.sleep(10);
			} catch (InterruptedException e1) {
			}
			if (getQueueMessages().isEmpty()){
				if (!bQueueWasEmpty){
					bQueueWasEmpty=true;
					baseSilenceTimeMs=System.currentTimeMillis();
				}
			}
			else{
				bQueueWasEmpty=false;
			}
			// if no messages coming in... return true if silence time elapsed
			if (bQueueWasEmpty){
				// get actual time
				long actTimeMs=System.currentTimeMillis();
				// if no messages incoming, returns true
				if (actTimeMs-baseSilenceTimeMs>=silenceLengthMs){
					bContinueLoop=false;
					bRadioSilent=true;
				}				
				// if the wait takes too long, return false
				if (actTimeMs-baseTimeMs>=maxWaitMs){
					bContinueLoop=false;
				}
			}
			else{
				// purge an element from the queue
				try {
					getQueueMessages().take();
				} catch (InterruptedException e) {
				}
				// get actual time
				long actTimeMs=System.currentTimeMillis();
				// if the wait takes too long, return false
				if (actTimeMs-baseTimeMs>=maxWaitMs){
					bContinueLoop=false;
				}
			}
		}
		return bRadioSilent;
	}
	
	
	// ********************** 
	// ********************** 
	// ********************** 
	// ********************** 
	// ********************** HERE THE TRANSMIT AND RECEIVE MESSAGE ROUTINES START
	// ********************** 
	// ********************** 
	// ********************** 
	// ********************** 
	
	// "transmit and receive" procedure return codes
	public enum EnumTransmitAndReceiveReturnCode{
		ok,
		too_long_body,
		no_silence_radio,
		error_transmitting,
		toolong_message_received,
		timeout_receiving,
	};	
	
	// "transmit and receive" procedure statuses
	private enum EnumTransmitAndReceiveLoopStatus{
		tx_message,
		tx_repeat,
		wait_message,
		wait_little_delay_then_tx_message,
	};
	// the status of tx/rx routine
	public enum EnumTransmitAndReceiveStatus{
		idle,
		running,
		ends,
	};		
	private EnumTransmitAndReceiveStatus statusTransmitAndReceive=EnumTransmitAndReceiveStatus.idle;
	// used to stop the tx/rx thread
	private boolean bStopTransmitAndReceive=false;
	
	// check if tx/rx thread is running
	/**
     * Check whether {@link #transmit_and_receive} is running or not
     * @return true if {@link #transmit_and_receive} is running, else false
     * 
     */    
	public boolean isRunningTransmitAndReceive(){
		if (statusTransmitAndReceive==EnumTransmitAndReceiveStatus.running){
			return true;
		}
		return false;
	}
	
	// used for stopping the diagnostic transmit and receive thread
    /**
     * Stops the transmit_and_receive routine 
     * @param  l_max_wait_ms  the routine timeout [ms]
     * @return true if transmit_and_receive was stopped successfully, else false
     *  
     */    	
	public boolean stopTransmitAndReceive(long l_max_wait_ms){
		// get base time
		long baseTimeMs=System.currentTimeMillis();
		// set the stop flag
		bStopTransmitAndReceive=true;
		try{
			while(isRunningTransmitAndReceive()){
				// get actual time
				long actTimeMs=System.currentTimeMillis();
				if (actTimeMs<baseTimeMs){
					baseTimeMs=actTimeMs;
				}
				else{
					if (actTimeMs-baseTimeMs>l_max_wait_ms){
						break;
					}
				}
			}
		}
		// always reset the stop flag
		finally{
			bStopTransmitAndReceive=false;
		}
		if (isRunningTransmitAndReceive()){
			return false;
		}
		return true;
	}
	
	// update the trip statistics
	private void updateTripStatistics(int last_trip_time_ms){
		stats.lastTripTimeMs=last_trip_time_ms;
		sumTripNum++;
		if (last_trip_time_ms>stats.maxTripTimeMs){
			stats.maxTripTimeMs=last_trip_time_ms;
		}
		if (last_trip_time_ms<stats.minTripTimeMs){
			stats.minTripTimeMs=last_trip_time_ms;
		}
		sumTripTimeMs+=last_trip_time_ms;
		// update if possible the mean trip time
		if (sumTripTimeMs<0 || sumTripNum<0){
			sumTripTimeMs=0;
			sumTripNum=0;
		}
		else{
			stats.averageTripTimeMs=(int) (sumTripTimeMs/sumTripNum);
		}
	}
	/**
     * Returns the last trip time for a {@link #transmit_and_receive} message
     * @return the last message trip time (from message sending to reply received), in milliseconds
     *  
     */	
	public int getLastTripTimeMs(){
		return stats.lastTripTimeMs;
	}
    
    // wait until a message is in queue received or timeout [ms]
    private void waitRxMessageOrTimeout(int i_timeout_ms){				
    	long baseTimeMs=System.currentTimeMillis();
    	
    	boolean bMessageRs=false;
    	boolean bTimeout=false;
    	// wait for the incoming message
    	while(!bMessageRs && !bTimeout){
    		if(!getQueueMessages().isEmpty()){
    			bMessageRs=true;
    		}
    		else{
    			long l_actual_time_ms=System.currentTimeMillis();
    			if (l_actual_time_ms<baseTimeMs){
    				baseTimeMs=l_actual_time_ms;
    			}
    			else if (l_actual_time_ms-baseTimeMs>i_timeout_ms){
    				bTimeout=true;
    			}
    			// sleep some ms
    			try {
    				Thread.sleep(10);
    			} catch (InterruptedException e) {
    			}
    		}
    	}
    }
	public boolean asks_radio_power()
	{
		return asac_radioRF_power.is_OK_radio_power_request(Asac_radioRF_power.enum_radio_power_requests.on, Asac_radioRF_power.MAX_DELAY_WAIT_REQUEST_RADIO_MS);
	}
	public boolean release_radio_power()
	{
		return asac_radioRF_power.is_OK_radio_power_request(Asac_radioRF_power.enum_radio_power_requests.release_on, Asac_radioRF_power.MAX_DELAY_WAIT_REQUEST_RADIO_MS);
	}
	public boolean OFF_radio_power()
	{
		return asac_radioRF_power.is_OK_radio_power_request(Asac_radioRF_power.enum_radio_power_requests.off, Asac_radioRF_power.MAX_DELAY_WAIT_REQUEST_RADIO_MS);
	}

	public void stop()
	{
		this.arnl.stop();
		this.asac_radioRF_power.stop();
	}

	/**
     * Transmits a message and waits for a reply 
     * @throws RuntimeException if unable to post messages
     * @param  destination  the destination of the message, normally the communication server (i.e. the PC); sometimes it can be the radio module
     * @param  msg_transmit the message to transmit
     * @param  msgReceived the message to receive
     * @param timeoutMs the routine timeout [ms]
     * @return      the operation result; if received, the message is copied into "msg_received"
     * 
     */    
	public EnumTransmitAndReceiveReturnCode transmitAndReceive(enum_rnl_message_destination_type destination, Asac_radio_network_layer_message msg_transmit, Asac_radio_network_layer_message msgReceived) throws RuntimeException{
		final String localTag=TAG+" txrx";
		EnumTransmitAndReceiveReturnCode ret;
		
		// return code is OK
		ret=EnumTransmitAndReceiveReturnCode.ok;

		// function begins here
		if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
			Log.i(localTag,"begin");
		}
		Log.i(localTag,"begin");

		// set the timeout
		int procedureTimeoutMs=params.transmitAndReceive.getTimeoutMs();

		
		// change routine status
		statusTransmitAndReceive=EnumTransmitAndReceiveStatus.running;
		
		// grant access to the network layer
		accessNetworkLayer.lock();
		Log.i(localTag, "after lock");
		// asks the radio ON
		asac_radioRF_power.is_OK_radio_power_request(Asac_radioRF_power.enum_radio_power_requests.on, Asac_radioRF_power.MAX_DELAY_WAIT_REQUEST_RADIO_MS);
		Log.i(localTag, "after ON");
		// now a big try/finally block to have a finally to always execute the unlock
		try{
			// purge spurious messages in the RX queues, but do not wait, just cleanup
			purgeRxQueuesWithoutDelay();
			
			long lBaseTimeMs=System.currentTimeMillis();
			boolean bContinueLoop=true;
			int repetitionsRemaining=params.transmitAndReceive.getMaxRepetitionsNumOf();
			int numRepetitionsSent=0;
			EnumTransmitAndReceiveLoopStatus txRxStatus=EnumTransmitAndReceiveLoopStatus.tx_message;
			
			// empty the message received
			msgReceived.b_message_valid=false;
			msgReceived.numBytesInMessage=0;

			if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
				Log.i(localTag,"send message loop begins");
			}
			
			while(   bContinueLoop
				  &&(ret==EnumTransmitAndReceiveReturnCode.ok)
				  &&!bStopTransmitAndReceive
				){
				switch(txRxStatus){
					// Let's go, post the message, then wait for the reply
					case tx_message:
					{
						EnumRnlReturnCode retcode;
						// information log
						if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
							Log.i(localTag,"post the message");
						}
						//
						// post the message ! (and catch exceptions)
						//
						stats.i_num_try_post_message++;
						try{
							retcode=arnl.postMessage(destination,msg_transmit);
						}catch(RuntimeException e ){
							throw new RuntimeException("cannot post message",e);
						}finally{
						}
						// what's happening?
						switch(retcode){
							case ok:
								// increase the number of TX messages, just for the statistics
								stats.numMsgTx++;
								txRxStatus=EnumTransmitAndReceiveLoopStatus.wait_message;
								if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
									Log.i(localTag,"post OK, waiting for the reply");
								}
								
								break;
							case too_long_body:
								// this should never happen boys!
								stats.numTooLongBodyTx++;
								bContinueLoop=false;
								ret=EnumTransmitAndReceiveReturnCode.too_long_body;
								if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
									Log.i(localTag,"body is too long");
								}
								break;
							case invalid_reply_header:
							case invalid_reply_length:
							case invalid_value:
								stats.numNorReplyError++;
								bContinueLoop=false;
								ret=EnumTransmitAndReceiveReturnCode.error_transmitting;
								if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
									Log.i(localTag,"invalid NOR reply");
								}
								break;
							case timeout_waiting_ack:
								stats.numNorQueueTimeoutError++;
								bContinueLoop=false;
								ret=EnumTransmitAndReceiveReturnCode.error_transmitting;
								if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
									Log.i(localTag,"timeout waiting ACK from NOR");
								}
								break;
							case unable_queue_NOR_msg:
								stats.numNorQueueStalledError++;
								bContinueLoop=false;
								ret=EnumTransmitAndReceiveReturnCode.error_transmitting;
								if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
									Log.i(localTag,"the NOR queue do not accept the message");
								}
								break;
							default:
								// unknown error
								stats.numUnknownErrorTx++;
								bContinueLoop=false;
								ret=EnumTransmitAndReceiveReturnCode.error_transmitting;
								if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
									Log.i(localTag,"unknown error during post");
								}
								break;
						}
						break;
					}
					// transmit the "REPET" token to repeat the message post
					case tx_repeat:
					{
						// a return code for the REPET message post
						EnumRnlReturnCode retcode;
						if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
							Log.i(localTag,"sending REPET token, remaining "+repetitionsRemaining+" tries");
						}
						if (repetitionsRemaining<1){
							repetitionsRemaining=1;
						}
						// build the infamous REPET string: REPET:<remaining repetitions+1>
						String repetitionRequest=REPEAT_STRING_MESSAGE+repetitionsRemaining;
						// update remaining repetitions
						if (repetitionsRemaining>1){
							repetitionsRemaining--;
						}
						else{
							repetitionsRemaining=1;
						}
						// create a new message
						Asac_radio_network_layer_message repetitionMessage=new Asac_radio_network_layer_message();
						// get the bytes
						repetitionMessage.message=repetitionRequest.getBytes();
						// set the length
						repetitionMessage.numBytesInMessage=repetitionRequest.length();	
						// set the return code to an invalid value
						retcode=EnumRnlReturnCode.invalid_value;
						// post the message, catch exceptions etc
						try{
							retcode=arnl.postMessage(destination,repetitionMessage);
						}catch(RuntimeException e ){
							throw new RuntimeException("cannot post repeat message",e);
						}finally{
						}
						// well check the return code
						switch(retcode){
							case ok:
								// increment statistics
								stats.numRepeatTx++;
								// wait a little bit, then post again the original message
								txRxStatus=EnumTransmitAndReceiveLoopStatus.wait_little_delay_then_tx_message;
								if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
									Log.i(localTag,"repetition token post OK, little delay now");
								}
								break;
							default:
								// unknown error
								stats.repeatPostErrors++;
								bContinueLoop=false;
								ret=EnumTransmitAndReceiveReturnCode.error_transmitting;
								if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
									Log.i(localTag,"error posting repetition token: "+retcode.toString());
								}
								break;
						}		
						break;
					}
					// after the REPET token, wait a little bit before sending again the message
					case wait_little_delay_then_tx_message:
					{
						if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
							Log.i(localTag,"sleeping a little bit");
						}
						// sleep
						try {
							Thread.sleep(params.transmitAndReceive.getPauseAfterRepetMs());
						} catch (InterruptedException e) {
						}
						if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
							Log.i(localTag,"back to send message again");
						}
						// transmit again the message
						txRxStatus=EnumTransmitAndReceiveLoopStatus.tx_message;
						break;
					}
					// we've posted the message, now wait for the incoming reply
					case wait_message:
					{	
						// no message successfully RX
						boolean bMessageRx=false;
						if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
							Log.i(localTag,"waiting for incoming messages");
						}
						// wait for incoming messages or timeout
						waitRxMessageOrTimeout(procedureTimeoutMs);
						// message received?
						if(!getQueueMessages().isEmpty()){
							// wait until a message is RX
							while(!bMessageRx){
								// try to take the message
								try {
									radioMessageRx=getQueueMessages().take();
									bMessageRx=true;
								} catch (InterruptedException e) {									
								}
								// if the queue is empty, WOW exit
								if(getQueueMessages().isEmpty()){
									break;
								}
							}
						}
						// if a message has been received and it has a non null length... OK!
						if (bMessageRx && radioMessageRx.msg.numBytesInMessage>0){
							if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
								Log.i(localTag,"checking received message length");
							}
							// well, we've received a message, update statistics
							stats.numMessagesRx++;
							// if there is no room for the copy... error
							if (msgReceived.message.length<radioMessageRx.msg.numBytesInMessage){
								if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
									Log.i(localTag,"the message received is too long: "+radioMessageRx.msg.numBytesInMessage+" while we can store max "+msgReceived.message.length+" bytes");
								}
								// update statistics
								stats.numMessagesTooLongRx++;
								// exit from the loop
								bContinueLoop=false;
								// set the code
								ret=EnumTransmitAndReceiveReturnCode.toolong_message_received;
							}
							else{
								if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
									Log.i(localTag,"message length OK, copying the message");
								}
								// copy the message body
								System.arraycopy(
										radioMessageRx.msg.message, 0,
							            msgReceived.message, 0,
							            radioMessageRx.msg.numBytesInMessage
							        );		
								// set the number of bytes in the message
								msgReceived.numBytesInMessage=radioMessageRx.msg.numBytesInMessage;
								// set the terminal number which sent the message
								msgReceived.i_from_terminal_number=radioMessageRx.from_terminal_number;
								// the message is valid too
								msgReceived.b_message_valid=true;
								if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
									Log.i(localTag,"valid message "+msgReceived.numBytesInMessage+" bytes long, from address "+msgReceived.i_from_terminal_number);
								}
								// well done
								ret=EnumTransmitAndReceiveReturnCode.ok;
								// the message has been received OK, end of loop
								bContinueLoop=false;
							}
						}
						else{
							if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
								Log.i(localTag,"timeout waiting for the reply");
							}
							// increase number of repetitions
							++numRepetitionsSent;
							// too many repetitions?
							if (numRepetitionsSent>params.transmitAndReceive.getMaxRepetitionsNumOf()){
								if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
									Log.i(localTag,"too many repetitions, abandon");
								}
								// clip to the maximum
								numRepetitionsSent=params.transmitAndReceive.getMaxRepetitionsNumOf();
								// exit form the loop
								bContinueLoop=false;
								ret=EnumTransmitAndReceiveReturnCode.timeout_receiving;
							}
							// switch to repeat status
							else{
								if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
									Log.i(localTag,"about to send repetition request number "+numRepetitionsSent);
								}
								txRxStatus=EnumTransmitAndReceiveLoopStatus.tx_repeat;
							}						
						}
						break;
					}//case wait_message:
				}//switch(txrx_status){
			}//while(   b_continue_loop
			
			{
				// calculates trip time
				long finalTimeMs=System.currentTimeMillis();
				long tripTimeMs=finalTimeMs-lBaseTimeMs;
				updateTripStatistics((int)tripTimeMs);
			}
			
			// if at least a repeat has been sent, purge the incoming queue
			if (numRepetitionsSent>0 && !bStopTransmitAndReceive){
				if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
					Log.i(localTag,"purging receiving queues");
				}
				bWaitNoMessages(params.transmitAndReceive.getPurgeSilenceDurationMs(),params.transmitAndReceive.getPurgeMaxDurationMs());
			}
			if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
				Log.i(localTag,"ends normally (no exceptions thrown)");
			}
		}
		finally{
			if (!receivefileRunning)
			{
				// we do not need no more the radio ON
				asac_radioRF_power.is_OK_radio_power_request(Asac_radioRF_power.enum_radio_power_requests.release_on, Asac_radioRF_power.MAX_DELAY_WAIT_REQUEST_RADIO_MS);
			}
			Log.i(localTag, "after release");
			// release access to the network layer
			accessNetworkLayer.unlock();
			Log.i(localTag, "after unlock");
			// set routine status to end
			statusTransmitAndReceive=EnumTransmitAndReceiveStatus.ends;

		}
		// set statistics about message received OK or not
		if (ret==EnumTransmitAndReceiveReturnCode.ok){
			if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
				Log.i(localTag,"ends OK");
			}
			stats.i_num_msg_ok++;
		}
		else{
			stats.i_num_msg_err++;
			if (logEnable.isEnabled(EnumTransportLogEnable.transmit_and_receive)){
				Log.i(localTag,"ends with error:"+ret.toString());
			}
			
		}
		
		return ret;
	}
	
	// ********************** 
	// ********************** 
	// ********************** 
	// ********************** 
	// ********************** HERE THE RECEIVE FILE ROUTINES START
	// ********************** 
	// ********************** 
	// ********************** 
	// ********************** 
	
	/**
	 * the return codes form the receive file routine
	 * @author root
	 *
	 */
	public enum EnumReceivefileReturnCode{
		ok,
		unable_to_format_kill_message_string,
		unable_to_build_kill_message_string,
		nullpointer_on_kill_message_string,
		unable_to_build_kill_message,
		unexpected_null_string_on_kill_message_string,
		too_many_retries_on_kill_message_send,
		unable_to_post_kill_message,
		unable_to_format_expected_reply_string,
		nullpointer_on_expected_reply_string,
		expected_reply_buffer_overflow_1,
		expected_reply_buffer_overflow_2,
		expected_reply_unsupported_encoding,
		expected_reply_unable_to_build_message,
		too_long_kill_message_to_send,
		unable_to_delete_file,
		unable_to_delete_directory,
		unable_to_open_destination_file,
		unable_to_close_received_file, 
		nullpointer_on_update_message_string, 
		unable_to_format_update_message_string, 
		unable_to_build_update_message_string, 
		unable_to_build_update_message, 
		too_long_update_message_to_send, 
		unable_to_get_update_message, 
		unable_to_get_server_file_status, 
		unknown_server_file_status,
    	unable_to_delete_file_to_align_with_server,
    	unable_to_delete_directory_to_align_with_server, 
    	unable_to_build_ack_first_packet, 
    	unable_to_post_ack_first_packet, 
    	unable_to_build_upd_name, 
    	unable_to_seek, 
    	error_writing_file, 
    	unable_to_send_flush_message, 
    	unable_to_build_flush_message, 
    	unable_to_receive_flushed, 
    	unable_to_receive_flushed_sep, 
    	unable_to_receive_flushed_msg, 
    	unable_to_receive_flushed_prologue, 
    	unable_to_send_nak_message, 
    	unable_to_build_nak_message, 
    	timeout_receving_updates, 
    	out_of_sequence_packet,
    	stopped,
    	not_initialized,

	}
	/**
	 * the different ways to load a file: full or update
	 * @author root
	 *
	 */
	public enum EnumReceiveFileMode{
		load,
		update,

	}
	
	public enum EnumFileServerFileStatus{
		unchanged,
		non_existent,
		changed,
		unknown
	};
	
	private void buildUpdateNackMessage(int i_packet_number, String string_update_prefix, Asac_radio_network_layer_message msg) throws RuntimeException{
		try{
			// build the NAK string
			String s_NAK_MESSAGE = new String(msgAckNak.getNackMessage(), "UTF-8");
			String s_ack_string=null;
			// build the ACK string: it is easier to do format using strings instead of byte arrays
			s_ack_string=String.format(Locale.US,"%s%d%c%s"
						,string_update_prefix
						,i_packet_number
						,(char)FIELD_SEPARATOR
						,s_NAK_MESSAGE
						);
			// get the raw bytes from the string
			byte [] byte_array=s_ack_string.getBytes("UTF-8");
			// copy the byte buffer to the message to server buffer
			System.arraycopy(byte_array, 0, msg.message, 0, byte_array.length);
			// set the number of characters in the buffer...
			msg.numBytesInMessage=byte_array.length;
			// OK, all fine!
			
		}
		// catch the exceptions
		catch (Exception n){
			throw new RuntimeException("unable to build update ack message",n);
		}

	}
	
	
	private Asac_radio_network_layer_message buildKillImageFileMessage(String string_kill_prefix, String filename) throws RuntimeException{
		Asac_radio_network_layer_message msg=new Asac_radio_network_layer_message ();
		// build the string to post
		try{
			String s_kill_message=null;
			s_kill_message=String.format(Locale.US,"%s%c%s"
						,string_kill_prefix
						,(char)FIELD_SEPARATOR
						,filename
						);
			byte [] byte_array=s_kill_message.getBytes("UTF-8");
			// copy the byte buffer to the message to server buffer
			System.arraycopy(byte_array, 0, msg.message, 0, byte_array.length);
			// set the number of characters in the buffer...
			msg.numBytesInMessage=byte_array.length;
			// OK, all fine!
		}
		// catch the exceptions
		catch (Exception n){
			throw new RuntimeException("unable to build kill image file message",n);
		}
		return msg;

	}
	private byte [] buildKillImageFileReply(String string_kill_prefix) throws RuntimeException{
		// build the string to post
		byte [] dst_byte_array=null;
		try{
			String s_ACK_MESSAGE = new String(msgAckNak.getAckMessage(), "UTF-8");
			String s_expected_reply=null;
			s_expected_reply=String.format(Locale.US,"%s%c%s"
					,string_kill_prefix
					,(char)FIELD_SEPARATOR
					,s_ACK_MESSAGE
					);
			dst_byte_array=s_expected_reply.getBytes("UTF-8");
			// OK, all fine!
		}
		// catch the exceptions
		catch (Exception n){
			throw new RuntimeException("unable to build kill image file reply message",n);
		}
		return dst_byte_array;

	}
	
	private Asac_radio_network_layer_message buildFirstUpdateFileMessage(String string_update_prefix, String host_filename) throws RuntimeException{
		 Asac_radio_network_layer_message msg= new Asac_radio_network_layer_message();
		
		try{
			// build the NAK string
			String s_update_message=null;
			// build the ACK string: it is easier to do format using strings instead of byte arrays
			s_update_message=String.format(Locale.US,"%s%d%c%s"
					,string_update_prefix
					,0
					,(char)FIELD_SEPARATOR
					,host_filename
					);
			// get the raw bytes from the string
			byte [] byte_array=s_update_message.getBytes("UTF-8");
			// copy the byte buffer to the message to server buffer
			System.arraycopy(byte_array, 0, msg.message, 0, byte_array.length);
			// set the number of characters in the buffer...
			msg.numBytesInMessage=byte_array.length;
			// OK, all fine!
			
		}
		// catch the exceptions
		catch (Exception n){
			throw new RuntimeException("unable to build first update message",n);
		}
		return msg;

	}
	
	
	
	private Asac_radio_network_layer_message buildUpdateAckMessage(int i_packet_number, String string_update_prefix) throws RuntimeException{
		Asac_radio_network_layer_message msg=new Asac_radio_network_layer_message ();
		
		try{
			// build the NAK string
			String s_ACK_MESSAGE = new String(msgAckNak.getAckMessage(), "UTF-8");
			String s_ack_string=null;
			// build the ACK string: it is easier to do format using strings instead of byte arrays
			s_ack_string=String.format(Locale.US,"%s%d%c%s"
						,string_update_prefix
						,i_packet_number
						,(char)FIELD_SEPARATOR
						,s_ACK_MESSAGE
						);
			// get the raw bytes from the string
			byte [] byte_array=s_ack_string.getBytes("UTF-8");
			// copy the byte buffer to the message to server buffer
			System.arraycopy(byte_array, 0, msg.message, 0, byte_array.length);
			// set the number of characters in the buffer...
			msg.numBytesInMessage=byte_array.length;
			// OK, all fine!
			
		}
		// catch the exceptions
		catch (Exception n){
			throw new RuntimeException("unable to build update ack message",n);
		}
		return msg;

	}
	
	private Asac_radio_network_layer_message buildUpdateFlushMessage(int i_packet_number, String string_update_prefix) throws RuntimeException{
		Asac_radio_network_layer_message msg=new Asac_radio_network_layer_message();
		try{
			// build the FLUSH string
			String s_FLUSH = "FLUSH";
			String s_msg_string=null;
			// build the message string: it is easier to do format using strings instead of byte arrays
			s_msg_string=String.format(Locale.US,"%s%d%c%s"
						,string_update_prefix
						,i_packet_number
						,(char)FIELD_SEPARATOR
						,s_FLUSH
						);
			// get the raw bytes from the string
			byte [] byte_array=s_msg_string.getBytes("UTF-8");
			// copy the byte buffer to the message to server buffer
			System.arraycopy(byte_array, 0, msg.message, 0, byte_array.length);
			// set the number of characters in the buffer...
			msg.numBytesInMessage=byte_array.length;
			// OK, all fine!
			
		}
		// catch the exceptions
		catch (Exception n){
			throw new RuntimeException("unable to build update flush message",n);
		}
		return msg;

	}
	
	/**
	 * wait until a message arrives, used by the routine
	 * if the b_stop_receive_file boolean is set to true, the routine exits in a short time
	 * @param timeoutMs the timeout waiting for the message
	 * @return the message received
	 */
	AsacRadioMessageFromTerminal receivefileWaitMessage(int timeoutMs){
		boolean messageTakenOK=false;
		AsacRadioMessageFromTerminal m=null;
		// wait for incoming messages or timeout
		// break the loop into pieces to stop fast if needed
		final int timeoutBaseTime=100;
		final int numLoop=(int) Math.ceil((double)timeoutMs/(double)timeoutBaseTime);
		for (int i=0;i<numLoop;i++){
			if (bStopReceivingFile){
				break;
			}
			waitRxMessageOrTimeout(timeoutBaseTime);
		}
		while(!messageTakenOK && !bStopReceivingFile && !getQueueMessages().isEmpty()){
			try {
				m=getQueueMessages().take();
				messageTakenOK=true;
			} catch (InterruptedException e) {
			}
		}
		if(messageTakenOK){
			return m;
		}
		return null;
	}
	public enum EnumStatusReceiveFileLoop{
		wait_msg,
		nack,
		ack,
		ok, ends, ko
	};
	
	private boolean bStopReceivingFile=false;
	private boolean receivefileRunning=false;
	/**
	 * stops the {@link #receiveFile(String, EnumReceiveFileMode, String)} thread
	 * @param wait_ms max ms to wait for the thread to stop
	 * @return true if thread was stopped, else false
	 */
	public boolean bStopReceiveFile(long wait_ms){
		
    	boolean bStopped=true;
    	if (receivefileRunning){
    		bStopReceivingFile=true;
    		long elapsedTimeMs=0;
    		try{
	    		while (receivefileRunning){
	    			try {
	    				final int my_pause_ms=10;
						Thread.sleep(my_pause_ms);
						elapsedTimeMs+=my_pause_ms;
					} catch (InterruptedException e) {
					}
	    			if (elapsedTimeMs>=wait_ms){
	    				break;
	    			}
	    		}
    		}
    		finally{
	    		if (!receivefileRunning){
	    			bStopped=true;
	    		}
	    		else{
	    			bStopped=false;
	    		}
    		}
    	}
    	return bStopped;
}
	/**
	 * receive a file
	 * @param fileDstPath the file pathname where the received data should be stored
	 * @param receive_mode states whether the file should be fully loaded or only updated
	 * @param fileSrcHostPath the server file pathname
	 * @return the return code as enumerative type
	 * @throws RuntimeException
	 */
	public EnumReceivefileReturnCode receiveFile(String fileDstPath,EnumReceiveFileMode receive_mode,String fileSrcHostPath) throws RuntimeException{
		EnumReceivefileReturnCode retcode=EnumReceivefileReturnCode.not_initialized;
		// set the running flag
		receivefileRunning=true;
		// asks the radio ON
		asac_radioRF_power.is_OK_radio_power_request(Asac_radioRF_power.enum_radio_power_requests.on, Asac_radioRF_power.MAX_DELAY_WAIT_REQUEST_RADIO_MS);

		try{
			// launch the private primitive
			retcode=receiveFilePrivate(fileDstPath,receive_mode,fileSrcHostPath);
		}
		catch (RuntimeException e){
			
		}
		// always reset the running flag
		finally{
			receivefileRunning=false;
			// asks the radio ON
			asac_radioRF_power.is_OK_radio_power_request(Asac_radioRF_power.enum_radio_power_requests.release_on, Asac_radioRF_power.MAX_DELAY_WAIT_REQUEST_RADIO_MS);
		}
		return retcode;
	}
	private void update_receive_file_stats_broadcast()
	{
		Intent i = new Intent();
		i.setAction("ASAC.action.SC_RX_FILE_STATS_CHANGED");
		i.putExtra(AsacIntents.RX_FILE_STATS_CHANGED_NUM_OF_BYTES_RX, stats.receive_file.last_file_bytes_RX);
		i.putExtra(AsacIntents.RX_FILE_STATS_CHANGED_HOST_FILE_SIZE, stats.receive_file.l_file_size);
		this.m_the_context.sendBroadcast(i);
	}

	public void set_terminal_number( byte tn)
	{
		if ((tn<=MAX_TERMINAL_NUMBER)&&(tn>=MIN_TERMINAL_NUMBER)){
			this.theTerminalNumber = tn;
			arnl.init_radio_network(theTerminalNumber);
		}
	}
	public byte get_terminal_number()
	{
		return this.theTerminalNumber;
	}


	/**
	 * 
	 * @param fileDstPath the file pathname where the received data should be stored
	 * @param receiveMode states whether the file should be fully loaded or only updated, see {@link #enum_receivefile_mode}
	 * @param fileSrcHostPath the server file pathname
	 * @return {@link #enum_receivefile_return_code} type
	 * @throws RuntimeException
	 */
	private EnumReceivefileReturnCode receiveFilePrivate(String fileDstPath,EnumReceiveFileMode receiveMode,String fileSrcHostPath) throws RuntimeException{
		EnumReceivefileReturnCode retcode;
		final String localTag=TAG+" receive file";
		
		
		// the kill file token string
		final String string_X_kill_file="X.KIF";
		
		bStopReceivingFile=false;
		
		int numConsecutiveResync=0;
		
		// build a new message 
		Asac_radio_network_layer_message messageToFileServer=new Asac_radio_network_layer_message();
		// function begins here
		if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
			Log.i(localTag,"begin receiving "+fileDstPath+"mode: "+receiveMode.toString()+", host: "+fileSrcHostPath);
		}
		
		
		// initialize return code to OK
		retcode=EnumReceivefileReturnCode.ok;
		// reset the number of bytes received last time we called this routine
		stats.receive_file.start_time=System.currentTimeMillis();
		stats.receive_file.last_file_bytes_RX=0;
		stats.receive_file.l_file_size = 0;
		update_receive_file_stats_broadcast();

		// if a full file load is requested, first send a KILL IMAGE message
		
		if (receiveMode == EnumReceiveFileMode.load){
			// function begins here
			if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
				Log.i(localTag,"load mode");
			}
			
			
			byte [] expectedReply=null;
			
			try{
				messageToFileServer=buildKillImageFileMessage(string_X_kill_file, fileSrcHostPath) ;
			}
			catch (RuntimeException e){
				throw new RuntimeException("unable to build kill message", e);
			}
			try{
				expectedReply=buildKillImageFileReply(string_X_kill_file);
			}
			catch(RuntimeException e ){
				throw new RuntimeException("unable to build expected reply to kif",e);
			} 
			
			
			boolean b_kill_message_sent_ok=false;
			boolean b_kill_message_error=false;
			int i_num_retry=0;
			
			// function begins here
			if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
				Log.i(localTag,"loop sending kill");
			}
			
			// send the kill message and wait for a reply
			while (!b_kill_message_sent_ok && !b_kill_message_error&&!bStopReceivingFile){
				// grant access to the network layer
				accessNetworkLayer.lock();
				
				try{
					EnumRnlReturnCode retcodePostMessage;
					// f
					if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
						Log.i(localTag,"post kill message");
					}
					
					try{
						retcodePostMessage=arnl.postMessage(enum_rnl_message_destination_type.message_destination_server,messageToFileServer);
					}catch(Exception e ){
						throw new RuntimeException("cannot post kill message",e);
					}
					switch(retcodePostMessage){
						case ok:
							stats.i_num_msg_ok++;
							// 
							if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
								Log.i(localTag,"kill posted OK");
							}
							
							radioMessageRx=receivefileWaitMessage(params.receivefile.getTimeoutRxFilePacket());
							if (radioMessageRx!=null){
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
									Log.i(localTag,"reply received");
								}
								
								if (byteArrayUtils.byteArrayBeginsWith(radioMessageRx.msg.message, radioMessageRx.msg.numBytesInMessage,expectedReply)){
									b_kill_message_sent_ok=true;
									// 
									if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
										Log.i(localTag,"reply OK");
									}
									
								}
								// if the message has not sent OK, try to send again the message
								else{
									// 
									if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
										Log.i(localTag,"invalid reply");
									}
									
									// too many retries? error!
									if (++i_num_retry>=params.receivefile.getMaxKillfileRepetitions()){
										// 
										if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
											Log.i(localTag,"too many retries, aborting");
										}
										retcode=EnumReceivefileReturnCode.too_many_retries_on_kill_message_send;
										b_kill_message_error=true;
									}
		
								}
							}
							break;
						case too_long_body:
						default:
							// 
							if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
								Log.i(localTag,"too long body, aborting");
							}
							stats.i_num_msg_err++;
							retcode=EnumReceivefileReturnCode.unable_to_post_kill_message;
							b_kill_message_error=true;
							break;
					}
				}finally{
					// release access to the network layer
					accessNetworkLayer.unlock();
				}
			}
			// if no error, before the full load, delete the actual file from the file system
			if (retcode==EnumReceivefileReturnCode.ok &&!bStopReceivingFile){
				File file = new File(fileDstPath);
			    if (file.exists()){
			    	if (!file.isDirectory()) {
			    		boolean bDeleted=false;
			    		try{
			    			bDeleted=file.delete();
			    		}
			    		catch (Exception e){
			    		}
				        if (!bDeleted){
							// 
							if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
								Log.i(localTag,"unable to delete destination file");
							}
				        	retcode=EnumReceivefileReturnCode.unable_to_delete_file;
				        }
				    }
			    	else{
						// 
						if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
							Log.i(localTag,"unable to delete directory");
						}
			    		retcode=EnumReceivefileReturnCode.unable_to_delete_directory;
			    	}
			    }
			}
		}
		
		
		if (retcode==EnumReceivefileReturnCode.ok &&!bStopReceivingFile){
		 // build the function message U.0<separator><FileName>
		    int ipacketAck=0;
		    Asac_radio_network_layer_message messageFromFileServer=new Asac_radio_network_layer_message();
		    
			// the kill file token string
			final String string_update_prefix="U.";
			
			boolean bFilesizeInfoGot=false;
			int numLoopFilesizeInfo;
				
			EnumFileServerFileStatus rxFileServerFileStatus;

			rxFileServerFileStatus=EnumFileServerFileStatus.unknown;
			
			// the length of the host file
			long lHostFilesize=0;
			
			EnumReceivefileReturnCode retcode_get_filesize=EnumReceivefileReturnCode.ok;
			// 
			if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
				Log.i(localTag,"get the file size");
			}
			
			// try to get the server file size/status info
			for(numLoopFilesizeInfo=0;!bFilesizeInfoGot && !bStopReceivingFile && numLoopFilesizeInfo<params.receivefile.getMaxLoopFileSizeInfo();numLoopFilesizeInfo++){
				try{
					messageToFileServer=buildFirstUpdateFileMessage(string_update_prefix, fileSrcHostPath) ;
				}
				catch (RuntimeException e){
					throw new RuntimeException("unable to build update message",e);
				}
				// OK maybe here we've built the update message...
				{
					EnumTransmitAndReceiveReturnCode rtl_txrx_return_code;
					rtl_txrx_return_code=EnumTransmitAndReceiveReturnCode.ok;
					try{
						// 
						if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
							Log.i(localTag,"querying file size");
						}
						
						rtl_txrx_return_code=transmitAndReceive(enum_rnl_message_destination_type.message_destination_server,	messageToFileServer, messageFromFileServer);
						if (rtl_txrx_return_code!=EnumTransmitAndReceiveReturnCode.ok){
							// 
							if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
								Log.i(localTag,"no good message received");
							}
							
							retcode_get_filesize=EnumReceivefileReturnCode.unable_to_get_update_message;
						}
					}catch (RuntimeException e) {
						retcode_get_filesize=EnumReceivefileReturnCode.unable_to_get_update_message;
					}
				}
				// unable to have the correct reply? try again!
				if (retcode_get_filesize!=EnumReceivefileReturnCode.ok){
					continue;
				}
				// Risposta:  U.0<sep><FileSize>
				{
					// 
					if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
						Log.i(localTag,"checking message");
					}
					int index_sep_b4_filesize=0;
					index_sep_b4_filesize=byteArrayUtils.lookByteInBuffer(radioMessageRx.msg.message,FIELD_SEPARATOR,0);

					// look for the first separator
					if (index_sep_b4_filesize>=0){
						// 
						if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
							Log.i(localTag,"first separator OK");
						}
						
						boolean bValidMessageRx=true;
						final int numCharToCheck=index_sep_b4_filesize+1;
						final int idx_first_char_body_reply=numCharToCheck;
						// check if the length is correct; at least one body character received?
						if (messageFromFileServer.numBytesInMessage<numCharToCheck+1){
							// 
							if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
								Log.i(localTag,"too short message");
							}
							bValidMessageRx=false;
						}
						// check if the first bytes are the same we've sent, up to the separator
						if (bValidMessageRx){
							for (int i=0;bValidMessageRx && i<numCharToCheck;i++){
								if (messageFromFileServer.message[i]!=messageToFileServer.message[i]){
									// 
									if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
										Log.i(localTag,"invalid message body");
									}
									bValidMessageRx=false;
								}
							}
						}
						// OK  if we've received a good message, let's check what the server tell us
						if (bValidMessageRx){
							// 
							if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
								Log.i(localTag,"the received message was OK");
							}
							// OK, we've the file size, we can exit from the loop
							bFilesizeInfoGot=true;
							// read the length embedded in the body
							long lengthReceived=byteArrayUtils.getLongFromByteBuffer(messageFromFileServer.message,idx_first_char_body_reply,messageFromFileServer.numBytesInMessage);
							// if the reply is 0 or -1, delete the destination file and exit, nothing to receive
							if ((lengthReceived==0)||(lengthReceived==-1)){
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
									Log.i(localTag,"the file we need is non existent");
								}
								rxFileServerFileStatus=EnumFileServerFileStatus.non_existent;
							}
							// if the reply is -2, the file is not changed, nothing to receive
							else if (lengthReceived==-2){
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
									Log.i(localTag,"the file we need is unchanged");
								}
								rxFileServerFileStatus=EnumFileServerFileStatus.unchanged;
							}
							// else if length >0, loop receiving the file
							else if (lengthReceived>0){
								// save the expected host file size
								lHostFilesize=lengthReceived;
								stats.receive_file.l_file_size = lHostFilesize;
								update_receive_file_stats_broadcast();

								rxFileServerFileStatus=EnumFileServerFileStatus.changed;
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
									Log.i(localTag,"the file we need has changed");
								}
							}
							// other values less than zero, what to do?
							else{
								rxFileServerFileStatus=EnumFileServerFileStatus.unknown;
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
									Log.i(localTag,"unknown status of the file we need");
								}
							}
						}
					}
				}
				
			}
			// now check if we have the server file status
			if (!bFilesizeInfoGot && !bStopReceivingFile){
				// 
				if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
					Log.i(localTag,"we got no filesize info");
				}
				retcode=EnumReceivefileReturnCode.unable_to_get_server_file_status;
			}
			// if status received OK, check what to do
			if (retcode==EnumReceivefileReturnCode.ok && !bStopReceivingFile){
				switch(rxFileServerFileStatus){
					case changed:
						// well proceed with file receiving
						break;
					case non_existent:
						// delete the file!
						File file = new File(fileDstPath);
					    if (file.exists()){
					    	if (!file.isDirectory()) {
					    		boolean b_deleted=false;
					    		try{
					    			b_deleted=file.delete();
					    		}
					    		catch (Exception e){
					    			
					    		}
						        if (!b_deleted){
									// 
									if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
										Log.i(localTag,"unable to delete the file");
									}
						        	retcode=EnumReceivefileReturnCode.unable_to_delete_file_to_align_with_server;
						        }
						    }
					    	else{
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
									Log.i(localTag,"the file we need is adirectory and we can't delete");
								}
					    		retcode=EnumReceivefileReturnCode.unable_to_delete_directory_to_align_with_server;
					    	}
					    }							
						break;
					case unchanged:
						// nothing to do
						break;
					case unknown:
					default:
						retcode=EnumReceivefileReturnCode.unknown_server_file_status;
						break;
				}
			}
			// if file status changed, load the changes...
			if ((retcode==EnumReceivefileReturnCode.ok)&&(rxFileServerFileStatus==EnumFileServerFileStatus.changed)&&!bStopReceivingFile){
				// build the first ACK message
				try {
					messageToFileServer=buildUpdateAckMessage(ipacketAck,string_update_prefix);
				}
				catch (RuntimeException e){
					throw new RuntimeException("unable to build first update ack message",e);
				}
				// the variable file we'll use to access the file
				RandomAccessFile fileReceiving=null;
				
				// build the update name byte array
				byte[] b_upd_message_name=null;
				try {
					b_upd_message_name = string_update_prefix.getBytes("UTF-8");
				} catch (UnsupportedEncodingException e) {
					retcode=EnumReceivefileReturnCode.unable_to_build_upd_name;
				}
				// build the "FLUSHED" byte array
				byte [] flushed_byte_array=null;
				try {
					flushed_byte_array="FLUSHED".getBytes("UTF-8");
				} catch (UnsupportedEncodingException e) {
					throw new RuntimeException("unable to encode flushed array", e);
				}
				
				// 
				if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
					Log.i(localTag,"the file receiving begins");
				}
				
				// grant access to the network layer
				accessNetworkLayer.lock();
				try{
					// send the ack message to the server, check the return code
					if (retcode==EnumReceivefileReturnCode.ok){
						try{
							EnumRnlReturnCode retcode_post_message;
							retcode_post_message=arnl.postMessage(enum_rnl_message_destination_type.message_destination_server,messageToFileServer);
							switch(retcode_post_message){
							case ok:
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
									Log.i(localTag,"update request sent OK");
								}
								stats.i_num_msg_ok++;
								break;
							case too_long_body:
							default:
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
									Log.i(localTag,"update request error");
								}
								stats.i_num_msg_err++;
								retcode=EnumReceivefileReturnCode.unable_to_post_ack_first_packet;
								break;
						}
						}catch(Exception e ){
							throw new RuntimeException("cannot post first update ack message",e);
						}
					}
					// OK the ack message has been posted, let's try to open the file
					if (retcode==EnumReceivefileReturnCode.ok){
						try {
							fileReceiving = new RandomAccessFile(fileDstPath, "rw");
						} catch (FileNotFoundException e) {
							// 
							if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
								Log.i(localTag,"cannot open the destination file");
							}
							retcode=EnumReceivefileReturnCode.unable_to_open_destination_file;
						}
					}
					// now try to update the file
					if (retcode==EnumReceivefileReturnCode.ok &&!bStopReceivingFile){
						// wait a message coming from the server
						EnumStatusReceiveFileLoop statusReceiveFile=EnumStatusReceiveFileLoop.wait_msg;
						int numConsecutiveTimeouts=0;
						// 
						if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
							Log.i(localTag,"loop of update requests");
						}
						
						while(statusReceiveFile!=EnumStatusReceiveFileLoop.ends && !bStopReceivingFile){
							switch(statusReceiveFile){
							case ack:
								boolean b_valid_body=false;
								boolean b_zero_block_size_found=false;
								boolean b_written_at_the_end_of_file=false;
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
									Log.i(localTag,"message check");
								}
								// a valid message received is in the form: U.<nPacketRx><sep><lBlockOffset><sep><nBlockSize><sep><dati>[<lBlockOffset><sep><nBlockSize><sep><dati>]
								// warning, there is no sep between data and blockoffset after the first data block!
								// get blockoffset and blocksize form the received message
								int index_sep_b4_offset=0;
								index_sep_b4_offset=byteArrayUtils.lookByteInBuffer(radioMessageRx.msg.message,FIELD_SEPARATOR,0);
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
									Log.i(localTag,"first sep OK");
								}
								// look for the offset separator
								if (index_sep_b4_offset>=0){
									int index_offset=index_sep_b4_offset+1;
									// I can have a lot of blocks inside the data part
									// loop until no error and we've data to write down
									while ((index_offset <radioMessageRx.msg.numBytesInMessage && !bStopReceivingFile)){
										// 
										if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
											Log.i(localTag,"checking blocks");
										}
										// get the block offset
										long l_block_offset=byteArrayUtils.getLongFromByteBuffer(radioMessageRx.msg.message,index_offset,radioMessageRx.msg.numBytesInMessage);
										// update the start search index
										int idx_start_search_sep_b4_size=index_sep_b4_offset+1;
										// init the next index we need to find
										int index_sep_b4_size=0;
										index_sep_b4_size=byteArrayUtils.lookByteInBuffer(radioMessageRx.msg.message,FIELD_SEPARATOR,idx_start_search_sep_b4_size);
										// look for the block size
										if (index_sep_b4_size<0){
											// 
											if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
												Log.i(localTag,"block size not found");
											}
											b_valid_body=false;
											break;
										}
										int index_block_size=index_sep_b4_size+1;
										if (index_block_size>=radioMessageRx.msg.numBytesInMessage){
											// 
											if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
												Log.i(localTag,"invalid block length");
											}
											b_valid_body=false;
											break;
										}
										// get the block size
										long l_block_size=byteArrayUtils.getLongFromByteBuffer(radioMessageRx.msg.message,index_block_size,radioMessageRx.msg.numBytesInMessage);
										// if block size is zero, end of procedure
										if (l_block_size==0){
											// 
											if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
												Log.i(localTag,"0 sized block, end of transfer");
											}
											b_zero_block_size_found=true;
											break;
										}
										if (l_block_size+l_block_offset>=lHostFilesize){
											b_written_at_the_end_of_file=true;
										}
										int idx_start_search_sep_b4_data=index_sep_b4_size+1;
										int index_sep_b4_data=0;
										index_sep_b4_data=byteArrayUtils.lookByteInBuffer(radioMessageRx.msg.message,FIELD_SEPARATOR,idx_start_search_sep_b4_data);
										// look for the separator of the data
										if (index_sep_b4_data<0){
											// 
											if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
												Log.i(localTag,"no data separator found");
											}
											b_valid_body=false;
											break;
										}
										// the index of the data part
										final int idx_body_data=index_sep_b4_data+1; 
										// check if we're inside the byte array with all of the data part
										if (radioMessageRx.msg.numBytesInMessage<l_block_size+idx_body_data){
											// 
											if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
												Log.i(localTag,"invalid data length");
											}
											b_valid_body=false;
											break;
										}
										// OK , we've found a valid body
										b_valid_body=true;
										// seek at the specified offset
										try {
											fileReceiving.seek(l_block_offset);
											try {
												// 
												if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
													Log.i(localTag,"writing data block of "+l_block_size+" bytes");
												}
												fileReceiving.write(radioMessageRx.msg.message, idx_body_data, (int) l_block_size);
												stats.receive_file.l_bytes_RX+=l_block_size;
												stats.receive_file.last_file_bytes_RX+=l_block_size;
												update_receive_file_stats_broadcast();
												// as previously stated, the next block offset hasn't the separator before it...
												index_offset=idx_body_data+(int)l_block_size;
											} catch (IOException e) {
												throw new RuntimeException("unable to write on the destination file",e);
											}
										} catch (IOException e) {
											throw new RuntimeException("unable to seek on the destination file",e);
										}
									}
								}
								// if we were unable to process the data, switch to nack status
								if (!b_valid_body){
									// 
									if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
										Log.i(localTag,"uno valid body, sending nack");
									}
									statusReceiveFile=EnumStatusReceiveFileLoop.nack;
								}
								else{
									{
										double dbl_duration_ms;
										dbl_duration_ms=System.currentTimeMillis()-stats.receive_file.start_time;
										if (dbl_duration_ms>0 && stats.receive_file.last_file_bytes_RX>0){
											stats.receive_file.last_transfer_rate_byte_s=stats.receive_file.last_file_bytes_RX/dbl_duration_ms*1000.0;
											
										}
									}
									
									// switch to the next packet!
									ipacketAck++;
									// 
									if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
										Log.i(localTag,"posting ack message "+ipacketAck);
									}
									
									// build the ACK message
									try {
										messageToFileServer=buildUpdateAckMessage(ipacketAck,string_update_prefix);
									}
									catch (RuntimeException e){
										throw new RuntimeException("unable to build an update ack message",e);
									}
									
									try{
										EnumRnlReturnCode retcode_post_message;
										stats.receive_file.i_num_ack_sent++;
										
										retcode_post_message=arnl.postMessage(enum_rnl_message_destination_type.message_destination_server,messageToFileServer);
										switch(retcode_post_message){
											case ok:
												// 
												if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
													Log.i(localTag,"post OK");
												}
												stats.i_num_msg_ok++;
												break;
											case too_long_body:
											default:
												// 
												if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
													Log.i(localTag,"post error");
												}
												stats.i_num_msg_err++;
												retcode=EnumReceivefileReturnCode.unable_to_post_ack_first_packet;
												break;
										}
									}catch(Exception e ){
										throw new RuntimeException("cannot post update ack message",e);
									}
									// if we've received a zero size block or we've written until the last byte of the file, the procedure completes OK
									if(b_zero_block_size_found||b_written_at_the_end_of_file){
										// 
										if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
											Log.i(localTag,"end of transfer OK");
										}
										statusReceiveFile=EnumStatusReceiveFileLoop.ok;
									}
									else{
										// 
										if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
											Log.i(localTag,"waiting next data block");
										}
										statusReceiveFile=EnumStatusReceiveFileLoop.wait_msg;
									}
								}
								break;
							case ok:
								stats.receive_file.i_num_OK++;
								stats.receive_file.end_time=System.currentTimeMillis();
								{
									double dbl_duration_ms;
									dbl_duration_ms=stats.receive_file.end_time-stats.receive_file.start_time;
									if (dbl_duration_ms>0 && stats.receive_file.last_file_bytes_RX>0){
										stats.receive_file.last_transfer_rate_byte_s=stats.receive_file.last_file_bytes_RX/dbl_duration_ms*1000.0;
										
									}
								}
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
									Log.i(localTag,"file received OK");
								}
								
								retcode=EnumReceivefileReturnCode.ok;
								statusReceiveFile=EnumStatusReceiveFileLoop.ends;
								break;
							case ko:
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
									Log.i(localTag,"file receive ends with error");
								}
								stats.receive_file.i_num_err++;
								statusReceiveFile=EnumStatusReceiveFileLoop.ends;
								break;
							case nack:
								EnumReceivefileReturnCode nackRetcode;
								boolean b_nack_sent_OK=false;
								stats.receive_file.i_num_nack_sent++;
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
									Log.i(localTag,"nack send loop");
								}
								
								for (int loopTrySendNack=0;loopTrySendNack<params.receivefile.getNumTrySendNack() && ! b_nack_sent_OK && !bStopReceivingFile;loopTrySendNack++){
									nackRetcode=EnumReceivefileReturnCode.ok;
									// wait until silence
									bWaitNoMessages(params.receivefile.getPurgeSilenceDurationMs(), params.receivefile.getPurgeMaxDurationMs());		
									// send the FLUSH  message
									//  sprintf(bufMsgTx,"U.%lu\x001%s",(nPacketAck+1),"FLUSH");   
									// 
									if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
										Log.i(localTag,"sending a flush "+Integer.valueOf(ipacketAck+1));
									}
									try{
										messageToFileServer=buildUpdateFlushMessage(ipacketAck+1, string_update_prefix);
									}
									catch (RuntimeException e){
										throw new RuntimeException("unable to build flush message",e);
									}
									{
										// OK send the flush message
										EnumTransmitAndReceiveReturnCode rtl_txrx_return_code;
										rtl_txrx_return_code=EnumTransmitAndReceiveReturnCode.ok;
										try{
											rtl_txrx_return_code=transmitAndReceive(enum_rnl_message_destination_type.message_destination_server,	messageToFileServer, messageFromFileServer);
											if (rtl_txrx_return_code!=EnumTransmitAndReceiveReturnCode.ok){
												// 
												if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
													Log.i(localTag,"flush send error");
												}
												nackRetcode=EnumReceivefileReturnCode.unable_to_send_flush_message;
												continue;
											}
										}catch (RuntimeException e) {
											nackRetcode=EnumReceivefileReturnCode.unable_to_build_flush_message;
											continue;
										}
									}
									{
										// check that we've received 'U.<(nPacketAck+1)><sep>FLUSHED'
										
										// 
										if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
											Log.i(localTag,"checking received flush reply");
										}
										// check if begins with U.
										if (!byteArrayUtils.byteArrayBeginsWith(messageFromFileServer.message,messageFromFileServer.numBytesInMessage,b_upd_message_name)){
											// 
											if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
												Log.i(localTag,"invalid flush prologue");
											}
											nackRetcode=EnumReceivefileReturnCode.unable_to_receive_flushed_prologue;
											continue;
										}
										int index_sep_flushed=0;
										index_sep_flushed=byteArrayUtils.lookByteInBuffer(messageFromFileServer.message,FIELD_SEPARATOR,0);
										// look for the offset separator
										if (index_sep_flushed<0){
											// 
											if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
												Log.i(localTag,"no sep found after prologue");
											}
											nackRetcode=EnumReceivefileReturnCode.unable_to_receive_flushed_sep;
											continue;
										}
										int idx_flushed_string=index_sep_flushed+1;
										// look for the "flushed" string
										if (messageFromFileServer.numBytesInMessage<idx_flushed_string+flushed_byte_array.length){
											// 
											if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
												Log.i(localTag,"invalid length of flushed message");
											}
											nackRetcode=EnumReceivefileReturnCode.unable_to_receive_flushed_msg;
											continue;
										}
										
										// check if begins with FLUSHED
										if (!byteArrayUtils.byteArrayHasSequenceAtIndex(messageFromFileServer.message, messageFromFileServer.numBytesInMessage, idx_flushed_string,flushed_byte_array)){
											// 
											if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
												Log.i(localTag,"the message is not a flushed one");
											}
											nackRetcode=EnumReceivefileReturnCode.unable_to_receive_flushed_msg;
											continue;
										}
										// 
										if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
											Log.i(localTag,"flushed received OK, now post nack");
										}
										// post the NAK message
										try{
											buildUpdateNackMessage(ipacketAck+1, string_update_prefix, messageToFileServer);
										}
										catch (RuntimeException e){
											throw new RuntimeException("unable to build nak message",e);
										}
										{
											// OK send the NAK message
											try{
												EnumRnlReturnCode retcode_post_message;
												retcode_post_message=arnl.postMessage(enum_rnl_message_destination_type.message_destination_server,messageToFileServer);
												switch(retcode_post_message){
												case ok:
													// 
													if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
														Log.i(localTag,"nack post OK");
													}
													stats.i_num_msg_ok++;
													break;
												case too_long_body:
												default:
													// 
													if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
														Log.i(localTag,"nack post error");
													}
													stats.i_num_msg_err++;
													nackRetcode=EnumReceivefileReturnCode.unable_to_post_ack_first_packet;
													continue;
											}
											}catch(Exception e ){
												throw new RuntimeException("cannot post update nack message",e);
											}
											
										}
										// if return code is still OK, exit from the send NACK loop
										if (nackRetcode==EnumReceivefileReturnCode.ok){
											b_nack_sent_OK=true;
										}
										else{
											try {
												Thread.sleep(params.receivefile.getPauseBetweenNackMs());
											} catch (InterruptedException e) {

											}
										}
									}
									if (nackRetcode==EnumReceivefileReturnCode.ok){
										// 
										if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
											Log.i(localTag,"waiting next message");
										}
										// back to status wait message
										statusReceiveFile=EnumStatusReceiveFileLoop.wait_msg;
									}
									else{
										// 
										if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
											Log.i(localTag,"unable to send flush or nack, aborting");
										}
										retcode=nackRetcode;
										statusReceiveFile=EnumStatusReceiveFileLoop.ko;
									}
								}
								break;
							case wait_msg:
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
									Log.i(localTag,"now I wait for a data block");
								}
								// new message received?
								radioMessageRx=receivefileWaitMessage(params.receivefile.getTimeoutRxFilePacket());
								if (radioMessageRx!=null){
									numConsecutiveTimeouts=0;
									// 
									if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
										Log.i(localTag,"data block received, checking");
									}
									
									// a valid message received is in the form: U.<nPacketRx><sep><lBlockOffset><sep><nBlockSize><sep><dati>
									// if valid message received, now 
									if (byteArrayUtils.byteArrayBeginsWith(radioMessageRx.msg.message,radioMessageRx.msg.numBytesInMessage,b_upd_message_name)){
										// 
										if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
											Log.i(localTag,"the prologue is OK");
										}
										long numPacketAcknowledged=byteArrayUtils.getLongFromByteBuffer(radioMessageRx.msg.message,b_upd_message_name.length,radioMessageRx.msg.numBytesInMessage);
										// test for a sequence error
										if (numPacketAcknowledged==-1){
											// 
											if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
												Log.i(localTag,"sequence error");
											}
											if (++numConsecutiveResync>params.receivefile.getMaxConsecutiveTryResync()){
												if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
													Log.i(localTag,"too many consecutive sequence error("+numConsecutiveResync+", aborting");
												}
												numConsecutiveResync=params.receivefile.getMaxConsecutiveTryResync();
												statusReceiveFile=EnumStatusReceiveFileLoop.ko;
												retcode=EnumReceivefileReturnCode.out_of_sequence_packet;
											}
											else{
												// increase the number of sequence synchronization sent 
												stats.receive_file.i_num_resync_sent++;
												
												if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
													Log.i(localTag,"sending again ACK to packet # "+ipacketAck);
												}
												// build the ACK message
												try {
													messageToFileServer=buildUpdateAckMessage(ipacketAck,string_update_prefix);
												}
												catch (RuntimeException e){
													throw new RuntimeException("unable to build an update ack message",e);
												}
												
												try{
													EnumRnlReturnCode retcode_post_message;
													stats.receive_file.i_num_ack_sent++;
													
													retcode_post_message=arnl.postMessage(enum_rnl_message_destination_type.message_destination_server,messageToFileServer);
													switch(retcode_post_message){
														case ok:
															// 
															if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
																Log.i(localTag,"post OK");
															}
															stats.i_num_msg_ok++;
															break;
														case too_long_body:
														default:
															// 
															if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
																Log.i(localTag,"post error");
															}
															stats.i_num_msg_err++;
															retcode=EnumReceivefileReturnCode.unable_to_post_ack_first_packet;
															break;
													}
												}catch(Exception e ){
													throw new RuntimeException("cannot post update ack message",e);
												}
												
											}
										}
										else{
											// reset number of sequence resync
											numConsecutiveResync=0;
											// if the packet number is the same of the last acknowledged, all OK
											if (numPacketAcknowledged==ipacketAck){
												// 
												if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
													Log.i(localTag,"already received packet "+numPacketAcknowledged);
												}
												
											}
											// if the packet number is not the next we're waiting, switch to nack status
											else if (numPacketAcknowledged!=ipacketAck+1){
												// 
												if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
													Log.i(localTag,"incorrect packet number "+numPacketAcknowledged);
												}
												statusReceiveFile=EnumStatusReceiveFileLoop.nack;
											}
											// the correct packet number is arrived, let's get the data!
											else{
												// 
												if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
													Log.i(localTag,"correct packet number received "+numPacketAcknowledged);
												}
												statusReceiveFile=EnumStatusReceiveFileLoop.ack;
											}
										}
									}
									
								}
								// if 
								else if (++numConsecutiveTimeouts>=params.receivefile.getMaxConsecutiveTimeouts()){
									// 
									if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
										Log.i(localTag,"too many retries waiting for the message, aborting");
									}
									retcode=EnumReceivefileReturnCode.timeout_receving_updates;
									statusReceiveFile=EnumStatusReceiveFileLoop.ko;
								}
								// if 
								else{
									// 
									if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
										Log.i(localTag,"sending nack");
									}
									statusReceiveFile=EnumStatusReceiveFileLoop.nack;
								}
								break;
							default:
								break;
							}
						}
					}
				}
				finally{
					accessNetworkLayer.unlock();
					// 
					if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
						Log.i(localTag,"closing the destination file");
					}
					try {
						if (fileReceiving!=null){
							fileReceiving.close();
						}
					} catch (IOException e) {
						if (retcode==EnumReceivefileReturnCode.ok){
							// 
							if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
								Log.i(localTag,"error closing the destination file");
							}
							retcode=EnumReceivefileReturnCode.unable_to_close_received_file;
						}
					}
				}
			}
				
		}
		// if processing was stopped..
		if (bStopReceivingFile){
			retcode=EnumReceivefileReturnCode.stopped;
		}
		// 
		if (logEnable.isEnabled(EnumTransportLogEnable.receive_file)){
			Log.i(localTag,"ends normally");
			if (retcode==EnumReceivefileReturnCode.ok){
				Log.i(localTag,"ends OK");
			}
			else{
				Log.i(localTag,"ends with error: "+retcode.toString());
			}
		}
		
		// 
		return retcode;
	}
	
	// ********************** 
	// ********************** 
	// ********************** 
	// ********************** 
	// ********************** HERE THE SEND FILE ROUTINES START
	// ********************** 
	// ********************** 
	// ********************** 
	// ********************** 
	
	public enum EnumSendfileReturnCode{
		ok,
		unable_to_open_input_file,
		unable_to_get_file_length,
		unable_to_init_file_list,
		unable_to_read_from_file,
		unable_to_rewind,
		toolong_body_message,	
		toomany_retries,
		unknown_packet_nak,
		post_message_error,
		unable_to_format_message_string,
	};	
	private enum EnumSendfileStatus{
		open_input_file,
		send_message,
		check_next_packet_number,
		prepare_packet,
		timeout_reply,
		wait_reply,
		check_reply,
		end,
		exit_from_loop,
	};	
	
	
	
	public enum EnumSendfileThreadStatus{
		idle,
		running,
		ends,
	};		
	private EnumSendfileThreadStatus senfileThreadStatus=EnumSendfileThreadStatus.idle;
	private boolean bStopSendfileThread=false;
	// 
	public boolean isRunningSendfileThread(){
		if (senfileThreadStatus==EnumSendfileThreadStatus.running){
			return true;
		}
		return false;
	}
	public boolean stopSendfileThread(long l_max_wait_ms){
		// get base time
		long baseTimeMs=System.currentTimeMillis();
		bStopSendfileThread=true;
		while(isRunningSendfileThread()){
			// get actual time
			long actTimeMs=System.currentTimeMillis();
			if (actTimeMs<baseTimeMs){
				baseTimeMs=actTimeMs;
			}
			else{
				if (actTimeMs-baseTimeMs>l_max_wait_ms){
					break;
				}
			}
		}
		bStopSendfileThread=false;
		if (isRunningSendfileThread()){
			return false;
		}
		return true;
	}	
	
	private long sendfileNumBytesTx;
	private void update_send_file_stats_broadcast()
	{
		Intent i = new Intent();
		i.setAction("ASAC.action.SC_TX_FILE_STATS_CHANGED");
		i.putExtra(AsacIntents.TX_FILE_STATS_CHANGED_NUM_OF_BYTES_TX, stats.transmit_file.last_file_bytes_TX);
		i.putExtra(AsacIntents.TX_FILE_STATS_CHANGED_LOCAL_FILE_SIZE, stats.transmit_file.l_file_size);
		this.m_the_context.sendBroadcast(i);
	}

	/**
	 * send a file to the remote PC
	 * @param fileSrcPath the source file path on the terminal, be careful you have the rights to read the file
	 * @param hostfileDstPath the destination file name on the remote PC
	 * @return the return code as enumerative type
	 * @throws RuntimeException
	 */
	
	public EnumSendfileReturnCode sendFile(String fileSrcPath, String hostfileDstPath) throws RuntimeException {
		final String localTag=TAG+" send_file";
		long packetTimeoutMs=params.sendfile.getTimeoutMs();
		class VerifyLastPacket{
			long num_packet;
			boolean has_been_sent;
			public VerifyLastPacket(){
				num_packet=0;
				has_been_sent=false;
			}
		}
		VerifyLastPacket verifyLastPacket= new VerifyLastPacket();
		// ACK found?
		EnumSendfileReturnCode retcode;
		retcode=EnumSendfileReturnCode.ok;
		EnumSendfileStatus sendfileStatus;
		sendfileStatus=EnumSendfileStatus.open_input_file;
		LinkedList<SendFileInfo> sentPacketList=new LinkedList<SendFileInfo> ();
		SendFileInfo actualFileInfo= new SendFileInfo();
		SendFileInfo nextPacketToSend= new SendFileInfo();
		Asac_radio_network_layer_message msgSendfile=new Asac_radio_network_layer_message();
		FileByteBuffer fileByteBuffer=new FileByteBuffer(fileSrcPath);
		long maxPacketWaiting;
		int numConsecutiveRepetitions=0;
		// at the very beginning, max 1 packet running
		maxPacketWaiting=1;
		senfileThreadStatus= EnumSendfileThreadStatus.running;
		
		setSendfileNumBytesTx(0);
		stats.clear_sendfile_stats();

		stats.transmit_file.last_file_bytes_TX = 0;
		stats.transmit_file.l_file_size = fileByteBuffer.lfile_size;
		update_send_file_stats_broadcast();

		
		// 
		if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
			Log.i(localTag,"begin send TDA7 file: "+fileSrcPath+" to host file: "+hostfileDstPath);
		}
		
		// grant access to the network layer
		accessNetworkLayer.lock();
		// asks the radio ON
		asac_radioRF_power.is_OK_radio_power_request(Asac_radioRF_power.enum_radio_power_requests.on, Asac_radioRF_power.MAX_DELAY_WAIT_REQUEST_RADIO_MS);

		try{
			
			// loop for file sending
			while(sendfileStatus!=EnumSendfileStatus.exit_from_loop){
				if (bStopSendfileThread){
					sendfileStatus=EnumSendfileStatus.end;
				}
				switch(sendfileStatus){
					case open_input_file:
						// 
						if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
							Log.i(localTag,"opening input file");
						}
						// clear the queues
						final int maxMessageToClear=10;
						int numMessageCleared;
						numMessageCleared=0;
						while(!getQueueMessages().isEmpty() && (numMessageCleared<maxMessageToClear)){
							try {
								radioMessageRx=getQueueMessages().take();
								numMessageCleared++;
							} catch (InterruptedException e) {
							}
						}
						
						// check for errors
						if (fileByteBuffer==null){
							retcode=EnumSendfileReturnCode.unable_to_get_file_length;
							sendfileStatus=EnumSendfileStatus.end;
							break;
						}
						// empty the list
						sentPacketList.clear();
						//initialize the packet number
						actualFileInfo.numPacket=0;
						nextPacketToSend.numPacket=actualFileInfo.numPacket;
						nextPacketToSend.offset=actualFileInfo.offset;					
						sendfileStatus=EnumSendfileStatus.prepare_packet;
						break;
	
					case prepare_packet:
						// create the message string
						String s;
						// the very first packet has a different message structure
						if (actualFileInfo.numPacket==0){
							// 
							if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
								Log.i(localTag,"preparing the first packet");
							}
							
							String sFileName;
							if ((hostfileDstPath==null)||hostfileDstPath.isEmpty()){
								sFileName=fileSrcPath;
							}
							else{
								sFileName=hostfileDstPath;
							}
							try{
								s=String.format(Locale.US,"T.%d%c%s%c%d%c%d%c"
											,actualFileInfo.numPacket
											,(char)FIELD_SEPARATOR
											,sFileName
											,(char)FIELD_SEPARATOR
											,actualFileInfo.offset
											,(char)FIELD_SEPARATOR
											,fileByteBuffer.length()
											,(char)FIELD_SEPARATOR
											);
							}
							catch (NullPointerException n){
								retcode=EnumSendfileReturnCode.unable_to_format_message_string;
								sendfileStatus=EnumSendfileStatus.end;
								break;							
							}
							catch (IllegalFormatException i){
								retcode=EnumSendfileReturnCode.unable_to_format_message_string;
								sendfileStatus=EnumSendfileStatus.end;
								break;							
								
							}
						}
						else{
							// 
							if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
								Log.i(localTag,"preparing a packet");
							}
							
							try{
								s=String.format(Locale.US,"T.%d%c",actualFileInfo.numPacket,(char)FIELD_SEPARATOR);
							}
							catch (NullPointerException n){
								retcode=EnumSendfileReturnCode.unable_to_format_message_string;
								sendfileStatus=EnumSendfileStatus.end;
								break;							
							}
							catch (IllegalFormatException i){
								retcode=EnumSendfileReturnCode.unable_to_format_message_string;
								sendfileStatus=EnumSendfileStatus.end;
								break;							
								
							}						
						}
					    actualFileInfo.offset=fileByteBuffer.actualFileOffset();
					    stats.setSendfilePercentage(fileByteBuffer.actualReadPercentage());
					    stats.setSendfileOffset(actualFileInfo.offset);
						stats.transmit_file.last_file_bytes_TX = actualFileInfo.offset;
						update_send_file_stats_broadcast();

					    // add in list informations about the packet
					    {				
					    	SendFileInfo add_file_info= new SendFileInfo();
					    	add_file_info.numPacket=actualFileInfo.numPacket;
					    	add_file_info.offset=actualFileInfo.offset;
					    	sentPacketList.add(add_file_info);
					    }
					    
						// refill the buffer up to the best characters amount
						fileByteBuffer.refillUpTo(MOST_EFFICIENT_MESSAGE_LENGTH);
						final int maxNumBytesNeeded=MOST_EFFICIENT_MESSAGE_LENGTH-s.length()-1;
						// the no-stuffed array
						byte [] byteArrayUnstuffed=new byte[maxNumBytesNeeded];
						// the stuffed array
						byte [] byteArrayStuffed=new byte[maxNumBytesNeeded];
						// prepare the stuff parameters
						AsacRadioNetworkLayer.StuffParams stuffParams=arnl.new StuffParams(
								 fileByteBuffer.takeSomeBytes(byteArrayUnstuffed)
								,maxNumBytesNeeded
								);
						// prepare the stuffed array
						arnl.binaryBufferStuff( 
								byteArrayUnstuffed, 
								byteArrayStuffed, 
								stuffParams
								);
						// increase the number of bytes we send
						setSendfileNumBytesTx(getSendfile_num_bytes_tx()+stuffParams.i_num_bytes_read);
					    stats.sentBytes(stuffParams.i_num_bytes_read);
						// set the number of bytes used from the buffer
						fileByteBuffer.setNumOfCharsUsed(stuffParams.i_num_bytes_read);
						if (fileByteBuffer.finished()){
							verifyLastPacket.num_packet=actualFileInfo.numPacket;
							verifyLastPacket.has_been_sent=true;
						}
						// put in the message the very first part
						try {
							byte [] bb=s.getBytes("UTF-8");
							System.arraycopy(bb,0, msgSendfile.message,0,bb.length);
						} catch (UnsupportedEncodingException e1) {
						}
						msgSendfile.numBytesInMessage=s.length();
						// now append the stuffed data part
						System.arraycopy(byteArrayStuffed,0,msgSendfile.message,msgSendfile.numBytesInMessage,stuffParams.i_num_bytes_written);
						// increase message length
						msgSendfile.numBytesInMessage+=stuffParams.i_num_bytes_written;
						if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
							Log.i(localTag, "prepared message "+msgSendfile.numBytesInMessage+" bytes long, stuffed in "+stuffParams.i_num_bytes_written+" bytes");
						}
						// switch to the send message status
					    sendfileStatus=EnumSendfileStatus.send_message;	
						break;
						
					case send_message:
						// 
						if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
							
							String decoded = "";
							try {
								decoded = new String(msgSendfile.message, "UTF-8");
							} catch (UnsupportedEncodingException e) {
							}				
							int iLength=decoded.length();
							if (iLength>16){
								iLength=16;
							}
							// I am always afraid about the substring function
							try{
								Log.i(localTag, "sending message "+msgSendfile.numBytesInMessage+" bytes long: "+decoded.substring(0, iLength));
							}
							catch (Exception e){
								e.printStackTrace();
							}
						}
	
						stats.numMsgTx++;	
						
						EnumRnlReturnCode retcode_post_message;
						try{
							//retcode_post_message=arnl.postMessageNoStuff(enum_rnl_message_destination_type.message_destination_server,msgSendfile);
							retcode_post_message=arnl.postMessageNoStuffNoWait(enum_rnl_message_destination_type.message_destination_server,msgSendfile);
						}catch (RuntimeException e){
							throw new RuntimeException("cannot post part of a file",e);
						}
						switch(retcode_post_message){
							case ok:
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
									Log.i(localTag,"message posted OK ");
								}
								stats.i_num_msg_ok++;
								sendfileStatus=EnumSendfileStatus.check_next_packet_number;
								// just to check if the nor serial port overruns!
								// wait until all of the bytes have been sent!!!
								if (ENABLE_SERIAL_PORT_SYNC_ON_SENDFILE){
									arnl.sync();
									// 
									if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
										Log.i(localTag,"sync message "+System.currentTimeMillis());
									}
								}
								break;
							case too_long_body:
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
									Log.i(localTag,"message is too long and can't be posted");
								}
								stats.i_num_msg_err++;
								retcode=EnumSendfileReturnCode.toolong_body_message;
								sendfileStatus=EnumSendfileStatus.end;
								break;
							default:
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
									Log.i(localTag,"unknown error");
								}
								stats.i_num_msg_err++;
								retcode=EnumSendfileReturnCode.post_message_error;
								sendfileStatus=EnumSendfileStatus.end;
								break;
						}
						break;
					case check_next_packet_number:
						// give an initialization to the next status
						sendfileStatus=EnumSendfileStatus.wait_reply;
						// if no more bytes to send, or too many packets in queue waiting, switch to receive status
						if (fileByteBuffer.finished() || sentPacketList.size()>=maxPacketWaiting){
							sendfileStatus=EnumSendfileStatus.wait_reply;
						}
						else{
							// if queue isn't empty, goto wait_reply
							if(!getQueueMessages().isEmpty()){
								sendfileStatus=EnumSendfileStatus.wait_reply;
								break;
							}
							else{
								// 
								if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
									Log.i(localTag,"pause between bursts");
								}
								// sleep a little bit
								try {
									Thread.sleep(params.sendfile.getPauseBetweenBurstMs());
								} catch (InterruptedException e) {
								}					
								// increase packet number
								actualFileInfo.numPacket++;
								// prepare the packet
								sendfileStatus=EnumSendfileStatus.prepare_packet;
							}
						}
						break;
					case wait_reply:
					{
						final int i_sleep_time_ms=10;
						boolean queueHasMessages=!getQueueMessages().isEmpty();
						// 
						if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
							Log.i(localTag,"waiting replies");
						}
						// determine the number of loops to execute
						int maxNumLoopWaitReply=(int) Math.ceil((double)packetTimeoutMs/(double)i_sleep_time_ms);
						if (maxNumLoopWaitReply<=0){
							maxNumLoopWaitReply=1;
						}
						// loop waiting for incoming messages
						while(!queueHasMessages&& maxNumLoopWaitReply-->0){
							queueHasMessages=!getQueueMessages().isEmpty();
							// if I get a reply, check it!
							if(queueHasMessages){
								break;
							}
							// sleep...
							try {
								Thread.sleep(i_sleep_time_ms);
							} catch (InterruptedException e) {
							}
						}
						// if the queue has messages
						if(queueHasMessages){
							sendfileStatus=EnumSendfileStatus.check_reply;
							break;
						}
						else{
							// timeout waiting for packet!
							sendfileStatus=EnumSendfileStatus.timeout_reply;
						}
						break;
					}//wait_reply
					// check the reply...
					case check_reply:
					{
						if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
							Log.i(localTag,"checking replies");
						}
						
						if (getQueueMessages().isEmpty()){
							sendfileStatus=EnumSendfileStatus.wait_reply;
							break;
						}
						if (radioMessageRx!=null){
							radioMessageRx.clear();
						}
						// loop to take the message
						while(true){
							if (getQueueMessages().isEmpty()){
								sendfileStatus=EnumSendfileStatus.wait_reply;
								break;
							}
							try {
								radioMessageRx=getQueueMessages().take();
								break;
							} catch (InterruptedException e) {
							}
						}
								
						stats.numMessagesRx++;
						switch(msgAckNak.check(radioMessageRx.msg.message)){
							default:
								if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
									Log.i(localTag,"unknown message rx");
								}
								stats.i_num_sendfile_unk_replies++;
								sendfileStatus=EnumSendfileStatus.wait_reply;
								break;
							case nak_received:
							{		
								if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
									String decoded = "";
									try {
										decoded = new String(radioMessageRx.msg.message, "UTF-8");
									} catch (UnsupportedEncodingException e) {
									}
									try{
										if (decoded.length()>0){
											Log.i(localTag,"NAK msg rx: "+System.currentTimeMillis()+": "+decoded.substring(0, Math.min(decoded.length(),16)));
										}
									}
									catch (Exception e){
										
									}
								}
								// repetition needed
								stats.numRepeatTx++;
								
								if (++numConsecutiveRepetitions>=params.sendfile.getMaxRepetitionsNumOf()){
									if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
										Log.i(localTag,"too many REPET token sent, aborting");
									}
									retcode=EnumSendfileReturnCode.toomany_retries;
									sendfileStatus=EnumSendfileStatus.end;
									break;
								}								
								if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
									Log.i(localTag,"sending a REPET token");
								}
								// the queue comes back to the minimum length
								maxPacketWaiting=1;
								// empty the list!
								sentPacketList.clear();
								// send the packet following the last acknowledged one
								actualFileInfo.numPacket=nextPacketToSend.numPacket;
								actualFileInfo.offset=nextPacketToSend.offset;
								// rewind the file buffer to the specified offset
								if (!fileByteBuffer.rewind(actualFileInfo.offset)){
									if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
										Log.i(localTag,"unable to rewind the file buffer");
									}
									retcode=EnumSendfileReturnCode.unable_to_rewind;
									sendfileStatus=EnumSendfileStatus.end;
									break;											
								}
								// clear the queue...
								bWaitNoMessages(params.sendfile.getPurgeSilenceDurationMs(),params.sendfile.getPurgeMaxDurationMs());
								// prepare next packet
								sendfileStatus=EnumSendfileStatus.prepare_packet;
								break;
							}
							case ack_received:
							{
								if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
									String decoded = "";
									try {
										decoded = new String(radioMessageRx.msg.message, "UTF-8");
									} catch (UnsupportedEncodingException e) {
									}		
									try{
										if (decoded.length()>0){
											Log.i(localTag, "ack msg rx "+System.currentTimeMillis()+": "+decoded.substring(0, Math.min(decoded.length(), 16)));
										}
									}
									catch (Exception e){
									}
								}
								LinkedList<Integer> indexesToRemove=new LinkedList<Integer> ();			
								indexesToRemove.clear();
								// reset the error counter
								numConsecutiveRepetitions=0;
								// get the acknowledged packet number
								long numPacketAcknowledged=byteArrayUtils.getLongFromByteBuffer(radioMessageRx.msg.message,4,radioMessageRx.msg.numBytesInMessage);
								// if the last packet has been sent and this packet acknowledges it, end of the procedure
								if (verifyLastPacket.has_been_sent){
									if (numPacketAcknowledged>=verifyLastPacket.num_packet){
										if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
											Log.i(localTag,"last packet received, OK");
										}										
										retcode=EnumSendfileReturnCode.ok;
										sendfileStatus=EnumSendfileStatus.end;
										break;									
									}
								}								
								// if the last packet has been acknowledged, clear the queue and increase queue size to the maximum
								if (numPacketAcknowledged>=actualFileInfo.numPacket){
									if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
										Log.i(localTag,"clearing wait queue, ack: "+numPacketAcknowledged+ " last sent: "+actualFileInfo.numPacket);
									}									
									maxPacketWaiting=params.sendfile.getMaxPacketWaiting();
									// empty the waiting queue
									sentPacketList.clear();
									// next packet to send is in effect the last sent+1...
									nextPacketToSend.numPacket=actualFileInfo.numPacket;
									nextPacketToSend.offset=actualFileInfo.offset;
								}
								else{
									// remove all of the entries which are confirmed by the acknowledgment
									for(SendFileInfo sf:sentPacketList) {
									    if(sf.numPacket<=numPacketAcknowledged) { 
									    	indexesToRemove.add(sentPacketList.indexOf(sf));
											if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
												Log.i(localTag,"removing packet: "+sf.numPacket);
											}												// switch queue to the maximum length
											maxPacketWaiting=params.sendfile.getMaxPacketWaiting();
									    }		
									    else if (sf.numPacket==numPacketAcknowledged+1){
											// update next packet number, if bigger than the actual one
											if (sf.numPacket>nextPacketToSend.numPacket){
												nextPacketToSend.numPacket=sf.numPacket;
												nextPacketToSend.offset=sf.offset;
											}
											// switch queue to the maximum length
											maxPacketWaiting=params.sendfile.getMaxPacketWaiting();
										}
									}
								}
								// remove all of the objects in list...
								for(Integer index_to_remove:indexesToRemove) {
									sentPacketList.remove((int)index_to_remove);
								}
								indexesToRemove.clear();
								// if the sent packet queue is empty, switch to send next packet!
								if (sentPacketList.isEmpty()){
									if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
										Log.i(localTag,"empty packet list");
									}												// switch queue to the maximum length
									// prepare next packet
									sendfileStatus=EnumSendfileStatus.check_next_packet_number;
									break;
								}
								else if (getQueueMessages().isEmpty()){
									// sleep...
									try {
										Thread.sleep(1);
									} catch (InterruptedException e) {
									}								
								}
							}//case ack_received:
						}//switch(check_acknak_msg(radio_message_rx.msg.message))
						break;
					}//check_reply
					case timeout_reply:
						// packet timeout???
						{
							if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
								Log.i(localTag,"reply timeout");
							}												// switch queue to the maximum length
							stats.numRepeatTx++;
							if (++numConsecutiveRepetitions>=params.sendfile.getMaxRepetitionsNumOf()){
								if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
									Log.i(localTag,"too many retries, aborting");
								}												// switch queue to the maximum length
								retcode=EnumSendfileReturnCode.toomany_retries;
								sendfileStatus=EnumSendfileStatus.end;
								break;
							}
							// the queue comes back to the minimum length
							maxPacketWaiting=1;
							// back to the very first packet not acknowledged
							actualFileInfo.numPacket=sentPacketList.getFirst().numPacket;
							actualFileInfo.offset=sentPacketList.getFirst().offset;
							// clear the pending queue
							sentPacketList.clear();
							// rewind the file buffer to the specified offset
							if (!fileByteBuffer.rewind(actualFileInfo.offset)){
								if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
									Log.i(localTag,"unable to rewind file buffer, aborting");
								}												// switch queue to the maximum length
								retcode=EnumSendfileReturnCode.unable_to_rewind;
								sendfileStatus=EnumSendfileStatus.end;
								break;											
							}
							// prepare next packet
							sendfileStatus=EnumSendfileStatus.prepare_packet;	
							break;
						}
					case end:
						// update statistics
						if (retcode==EnumSendfileReturnCode.ok){
						    stats.setSendfilePercentage(100);
						    stats.setSendfileOffset(fileByteBuffer.lfile_size);
							stats.transmit_file.last_file_bytes_TX = fileByteBuffer.lfile_size;
							update_send_file_stats_broadcast();
						}
						
						fileByteBuffer.close();
						sentPacketList.clear();
						sendfileStatus=EnumSendfileStatus.exit_from_loop;
						break;
					case exit_from_loop:
						break;
					default:
						break;
				}
			}
		}
		finally{
			// asks the radio ON
			asac_radioRF_power.is_OK_radio_power_request(Asac_radioRF_power.enum_radio_power_requests.release_on, Asac_radioRF_power.MAX_DELAY_WAIT_REQUEST_RADIO_MS);
			// release access to the network layer
			accessNetworkLayer.unlock();
		}
		
		senfileThreadStatus= EnumSendfileThreadStatus.ends;
		if (logEnable.isEnabled(EnumTransportLogEnable.send_file)){
			Log.i(localTag,"end of procedure");
		}
		
		return retcode;
		
	}

	
	
	/**
	 * take a look at {@link #setM_byte_terminal_number}
	 * @return the actual terminal number
	 */
	public byte getTerminalNumber() {
		return theTerminalNumber;
	}

	/**
	 * @param terminalNumber the needed terminal number
	 */
	public void setTerminalNumber(byte terminalNumber) {
		if ((terminalNumber<=MAX_TERMINAL_NUMBER)&&(terminalNumber>=MIN_TERMINAL_NUMBER)){
			this.theTerminalNumber = terminalNumber;
		}
	}


	

	public BlockingQueue<AsacRadioNetworkLayer.AsacRadioMessageFromTerminal> getQueueMessages() {
		return queue_msgs;
	}

	public void setQueue_msgs(BlockingQueue<AsacRadioNetworkLayer.AsacRadioMessageFromTerminal> queue_msgs) {
		this.queue_msgs = queue_msgs;
	}
	public long getSendfile_num_bytes_tx() {
		return sendfileNumBytesTx;
	}
	public void setSendfileNumBytesTx(long sendfile_num_bytes_tx) {
		this.sendfileNumBytesTx = sendfile_num_bytes_tx;
	}
    
}

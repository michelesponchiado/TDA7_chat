package diagnostic_thread;
import android.content.Context;

import java.io.UnsupportedEncodingException;
import java.util.Locale;

import asacRadioRFPower.Asac_radioRF_power;
import round_trip_statistics.RoundTripMessagesPerSecond;

import Profile.LogProfile;
import asac_radio_module_commands.Asac_radio_module_commands;
import asac_radio_module_commands.Asac_radio_module_commands.enum_module_reply_type;
import asac_radio_module_commands.Asac_radio_module_commands.enum_radio_strength;
import asac_radio_module_commands.Asac_radio_module_commands.enum_radio_txqueue_status;
import asac_radio_module_commands.Asac_radio_module_commands.enum_radio_work_mode;
import asac_radio_network_layer.AsacRadioNetworkLayer;
import asac_radio_network_layer.AsacRadioNetworkLayer.enum_rnl_message_destination_type;
import asac_radio_network_layer.AsacRadioNetworkLayer.EnumRnlReturnCode;
import asac_radio_network_layer_message.Asac_radio_network_layer_message;
import asac_radio_transport_layer.AsacRadioTransportLayer;


/**
 * implements a diagnostic tool to evaluate the performance of the radio network
 * @author root
 *
 */
public class Diagnostic_thread implements Runnable {
//	final int i_default_message_length=HIGHCOM_OPT_PACKET_SIZE;
	final int i_default_message_length=100;
	final int i_max_message_length=120;
	final int i_min_message_length=20;
	final int default_pause_between_post_ms=20;
	// the header of a valid diagnostic message received
	final String s_valid_msg_rx_header="TX&RX [";
	// a diagnostic message with the placeholder for the message index
	final String s_diagnostic_message_tx_format="F_TESTR\001#TX&RX [%d]* TEST * hello from TDA7!";
	
	private volatile Thread thread_diagnostic;
	// the minimum time between diagnostic messages send
	private int pause_between_post_ms;
	private enum_radio_work_mode work_mode;
	private enum_radio_txqueue_status txqueue_status;
	private enum_radio_strength radio_strength;
	private int rssi;
	private int i_message_length;
	private enum enum_diagnostic_thread_status{
		idle,
		running,
		ends,
	};
	enum_diagnostic_thread_status thread_status;
	private long l_diagnostic_thread_begin_time;
	private long l_num_msg_tx;
	private long l_num_msg_rx;
	private long l_num_msg_rx_bad_format;
	private Asac_radio_module_commands armc;
	private long l_num_radio_status_w;
	private long l_num_radio_status_r;
	private long l_pause_every_N_ms;
	private long l_pause_every_N_ms_base;
	
	private int i_cur_send_index;
	private int i_cur_receive_index;
	final int i_max_index=9;
	final int i_min_index=0;
	final int i_max_distance_tx_rx_index=4;
	private int i_status_wait_packet;
	private long l_base_timeout_packet_wait;
	final long final_max_wait_packets_ms=1000;
	private long l_max_wait_packets_ms;
	
	private RoundTripMessagesPerSecond rtmps;
	
	/**
	 * @return the actual diagnostic throughput speed in messages per second
	 */
	public double get_act_diagnostic_speed_msgs_per_s(){
		return rtmps.get_act_speed();
	}
	/**
	 * @return the average diagnostic throughput speed in messages per second
	 */
	public double get_avg_diagnostic_speed_msgs_per_s(){
		return rtmps.get_average_speed();
	}
	/**
	 * @return the max diagnostic throughput speed in messages per second
	 */
	public double get_max_diagnostic_speed_msgs_per_s(){
		return rtmps.get_max_speed();
	}
	/**
	 * @return true if new statistics are available
	 */
	public boolean new_diagnostic_stats_available(){
		return rtmps.new_value_avail();
	}
	/**
	 * @return the total number of messages received
	 */
	public double get_diagnostic_stats_total_rx(){
		return rtmps.get_num_msg_rx_total();
	}
	
	
	
	private void init_waiting_packets_indexes(){
		setI_cur_send_index(0);
		setI_cur_receive_index(0);
	}
	private AsacRadioTransportLayer artl;
	private int num_radio_module_queued_messages_KO;
	private int num_radio_module_queued_messages_OK;
	private boolean NOR_queue_is_full;
	private int num_times_NOR_queue_full;
	private int num_times_radio_search_mode;
	
	LogProfile profile=null;
	
	private void v_init_radio_status(){
    	setWork_mode(enum_radio_work_mode.unknown);
    	setTxqueue_status(enum_radio_txqueue_status.unknown);
    	setRadio_strength(enum_radio_strength.off);
    	setRssi(0);
	}
	/**
	 * the class constructor
	 */
	public Diagnostic_thread(AsacRadioTransportLayer artl, Context m_the_context){
		this.artl=artl;
		// build a radio module commands layer to handle messages from the radio module
		armc=new Asac_radio_module_commands(m_the_context);
		rtmps=new RoundTripMessagesPerSecond();
    	setL_num_radio_status_w(0);
    	setL_num_radio_status_r(0);
    	setL_pause_every_N_ms(0);
    	setNOR_queue_is_full(false);
    	
    	v_init_radio_status();
    	profile=new LogProfile();
		
		setPause_between_post_ms(default_pause_between_post_ms);
		default_message_length();
    	setL_max_wait_packets_ms(final_max_wait_packets_ms);
    	
    	thread_status=enum_diagnostic_thread_status.idle;
    	v_init_wait_packetstatus();
		
	}
	
	/**
	 * 
	 * @return the diagnostic thread duration time in seconds; if the thread isn't running, returns 0
	 */
	public long get_diagnostic_thread_duration_second(){
		if (!this.isrunning()){
			return 0;
		}
		long l_time_elapsed_ms=System.currentTimeMillis()-this.getL_diagnostic_thread_begin_time();
		if (l_time_elapsed_ms<0){
			return 0;
		}
		return (long)(l_time_elapsed_ms/1000.0);
	}
	
	/**
	 * sets the length of the message to use in the {@link #run()} of the test
	 * @param the_length of the message to use, typically around 120 bytes
	 * @return the length of the message
	 */
	public int set_message_length(int the_length){
		if ((the_length>i_max_message_length)||(the_length<i_min_message_length)){
			i_message_length=i_default_message_length;
		}
		return i_message_length;
	}
	/**
	 * reset the message length to the default
	 * @return the message length set, the default value
	 */
	public int default_message_length(){
		i_message_length=i_default_message_length;
		return i_message_length;
	}
	/**
	 * 
	 * @return the actual diagnostic message length
	 */
	public int get_message_length(){
		return i_message_length;
	}
	public boolean b_new_radio_status_avail(){
		if (getL_num_radio_status_w()!=getL_num_radio_status_r()){
			setL_num_radio_status_r(getL_num_radio_status_w());
			return true;
		}
		return false;
	}
	
	/**
	 * initialize the pause structures for the diagnostic thread
	 */
	private void init_pause(){
		l_pause_every_N_ms_base=System.currentTimeMillis();
	}
	private enum enum_diagnostic_loop_status{
		init,
		post,
		rx_messages,
	}
	/**
	 * check whether  pause if needed, and if it is, waits the specified amount of time
	 * @return true if a pause has been done, else returns false
	 */
	private boolean b_check_pause(){
		boolean retcode=false;
    	// is a pause enabled every N ms?
    	if (l_pause_every_N_ms>0){
    		long l_act_time_pause=System.currentTimeMillis();
    		if ((l_act_time_pause>=l_pause_every_N_ms_base+l_pause_every_N_ms)||(l_act_time_pause<l_pause_every_N_ms_base)){
    			l_pause_every_N_ms_base=l_act_time_pause;
        		try {
					Thread.sleep(800);
				} catch (InterruptedException e) {
				}
        		retcode=true;
    		}
    	}
    	return retcode;
	}
	
	private void build_req_radio_status(Asac_radio_module_commands armc, Asac_radio_network_layer_message msg_req_radio_status, boolean b_enable_short_status_reply){
		String s_msg_req_radio_status=null;
		s_msg_req_radio_status=armc.build_get_radio_status(b_enable_short_status_reply);
		msg_req_radio_status.message=s_msg_req_radio_status.getBytes();
		msg_req_radio_status.numBytesInMessage=s_msg_req_radio_status.length();
	}
	private void build_req_short_radio_status(Asac_radio_module_commands armc, Asac_radio_network_layer_message msg_req_radio_status){
		String s_msg_req_radio_status=null;
		s_msg_req_radio_status=armc.build_get_short_radio_status();
		msg_req_radio_status.message=s_msg_req_radio_status.getBytes();
		msg_req_radio_status.numBytesInMessage=s_msg_req_radio_status.length();
	}
	
	private void the_thread(){
		AsacRadioNetworkLayer.AsacRadioMessageFromTerminal radio_message_rx; 
		setL_diagnostic_thread_begin_time(System.currentTimeMillis());
    	Thread thisThread = Thread.currentThread();
    	// set the start time
    	long l_last_time_post_ms=System.currentTimeMillis();
		String s_0_to_9_repeated="";
		Asac_radio_network_layer_message msg_req_radio_status=null;
		Asac_radio_network_layer_message msg_transmit=null;
		
		
		
		// initialize the packet wait status
		v_init_wait_packetstatus();
		// init the thread status
		enum_diagnostic_loop_status diagnostic_loop_status=enum_diagnostic_loop_status.init;

        while (thread_diagnostic == thisThread) {
        	// check if we are waiting more than maximum messages
        	v_update_wait_packetstatus();
        	
//here some examples of pattern strings	            	
//	        	String radio_req="F_TESTR\001#TX&RX [1]* TEST * hello from TDA7!";
//	        	String radio_req="F_TESTR\001#TX&RX [1]* TEST * hello from TDA70123456789!";
//	        	String radio_req="F_TESTR\001#TX&RX [1]* TEST * hello from TDA701234567890123456789012345678901234567890123456789!";
//	        	String radio_req="F_TESTR\001#TX&RX [1]* TEST * hello from TDA701234567890123456789012345678901234567890123456789012345678901234567890123456789!";
			// this is the default prologue; we can append 01234567890123...
			// the character \001 is the separator 0x01
        	
        	// handle diagnostic thread loop statuses
        	switch(diagnostic_loop_status){
	        	case init:
            	default:
	        	{
	        		// start with queue status to full to wait an update from the NOR
	        		setNOR_queue_is_full(true);
	        		setNum_times_NOR_queue_full(0);
	            	msg_transmit=new Asac_radio_network_layer_message();
	        		radio_message_rx= artl.arnl.new AsacRadioMessageFromTerminal(); 
	        		msg_req_radio_status=new Asac_radio_network_layer_message();
	        		setL_diagnostic_thread_begin_time(System.currentTimeMillis());
	        		setL_num_msg_tx(0);
	        		setL_num_msg_rx(0);
	        		setL_num_msg_rx_bad_format(0);
	        		setL_num_radio_status_w(0);
	            	// build the radio status query message--> enable the automatic reply
	        		//build_req_radio_status(armc, msg_req_radio_status,true);
	            	// build the radio status query message--> disable the automatic reply
	        		build_req_radio_status(armc, msg_req_radio_status,false);
	        		//build_req_short_radio_status(armc, msg_req_radio_status);
	        		// post the message enabling the query status
		        	try {
		        		artl.arnl.postMessage(enum_rnl_message_destination_type.message_destination_radio_module,msg_req_radio_status);
		        	}catch(RuntimeException e){
		        		throw new RuntimeException("unable to query radio status",e);
		        	}
	        		
	        		// builds up the string filled with 012..9012..9012... sequence
	        		for (int i=0;i<i_max_message_length;i++){
	        			s_0_to_9_repeated+=(char)((i%10)+'0');
	        		}
	        		
	        		
	        		// clear statistics
	        		rtmps.clear();
	        		// begin acquiring new statistics
	        		rtmps.start();
	        		// initialize the pause timers
	        		init_pause();
	        		// initialize last time we posted a message
	        		l_last_time_post_ms=System.currentTimeMillis();
	        		
	        		// change status
	        		diagnostic_loop_status=enum_diagnostic_loop_status.post;
	        		break;
	        		
	        	}
        		// post a new message
            	case post:
            	{
	        		profile.log("enter post status");
            		
            		// if we are waiting for incoming packets because our waiting queue is too long, wait
            		if (isB_waiting_packets()){
            			// send a status request to keep info about radio alive
    	            	// build the short radio status query message
    	        		build_req_short_radio_status(armc, msg_req_radio_status);
    	        		// post the message enabling the query status
    		        	try {
    		        		int i_act_radio_status=artl.arnl.handleNORpackets.getRadio_status_update_rx_numof();
    		        		EnumRnlReturnCode post_retcode = artl.arnl.postMessage(enum_rnl_message_destination_type.message_destination_radio_module,msg_req_radio_status);
    		        		if (post_retcode!=EnumRnlReturnCode.ok){
                				setNum_radio_module_queued_messages_KO(getNum_radio_module_queued_messages_KO() + 1);
                			}
                			else{
                				setNum_radio_module_queued_messages_OK(getNum_radio_module_queued_messages_OK() + 1);
                			}
    		        		if (i_act_radio_status!=artl.arnl.handleNORpackets.getRadio_status_update_rx_numof()){
		            			// set the work mode
		            			setWork_mode(artl.arnl.handleNORpackets.getRadio_work_mode());
		            			// set the radio signal strength
		            			setRadio_strength(artl.arnl.handleNORpackets.getRadio_strength());
		            			// set nor queue status
		            			setNOR_queue_is_full(artl.arnl.handleNORpackets.isB_NOR_message_queue_full());
		            			// increment the number of radio status rx
		            			setL_num_radio_status_w(getL_num_radio_status_w() + 1);
    		        		}
    		        	}catch(RuntimeException e){
    		        		throw new RuntimeException("unable to query radio status",e);
    		        	}
    		        	// wait for incoming messages
            			diagnostic_loop_status=enum_diagnostic_loop_status.rx_messages;
            			break;
            		}
	            	// if we make a pause, restart the statistics
	            	if (b_check_pause()){
	            		profile.log("pause done");
	            		rtmps.clear();
	            		rtmps.start();
	            		break;
	            	}
	            	// build the message updating the message index
	        		try{
	        			profile.log("building message");
	        			// prepare the diagnostic message
			        	String radio_req=String.format(Locale.US,s_diagnostic_message_tx_format,get_a_new_send_index());	
			        	// how many char do I need? (can be less than zero)
		        		final int i_num_char_difference=i_message_length-radio_req.length();
			        	// if needed string is shorter then the default built, clip
			        	if (i_num_char_difference<0){
			        		try{
			        			radio_req=radio_req.substring(0, i_message_length);
			        		}
			        		catch (IndexOutOfBoundsException e){
			        		}
			        	}
			        	// if needed string is longer, add ASCII '0', '1', ..., '9' until completion
			        	else if (i_num_char_difference>0){
			        		try{
			        			radio_req+=s_0_to_9_repeated.substring(0, Math.min(i_num_char_difference,s_0_to_9_repeated.length()));
			        		}
			        		catch(IndexOutOfBoundsException	e){
			        		}
			        	}
			        	// copy the message to transmit
			        	msg_transmit.message=radio_req.getBytes();
			        	msg_transmit.numBytesInMessage=radio_req.length();
	    		
	        		}
	        		catch(Exception e ){
	        			throw new RuntimeException("unable to build diagnostic pattern string",e);
	        		}
	        		// post the message
		        	try{
		        		profile.log("post message");
		        		int i_act_radio_status=artl.arnl.handleNORpackets.getRadio_status_update_rx_numof();
		        		EnumRnlReturnCode post_retcode;
		        		post_retcode=artl.arnl.postMessage(enum_rnl_message_destination_type.message_destination_server,msg_transmit);
		        		if (post_retcode!=EnumRnlReturnCode.ok){
            				setNum_radio_module_queued_messages_KO(getNum_radio_module_queued_messages_KO() + 1);
            			}
            			else{
            				setNum_radio_module_queued_messages_OK(getNum_radio_module_queued_messages_OK() + 1);
            			}
		        		if (i_act_radio_status!=artl.arnl.handleNORpackets.getRadio_status_update_rx_numof()){
	            			// set the work mode
	            			setWork_mode(artl.arnl.handleNORpackets.getRadio_work_mode());
	            			// set the radio signal strength
	            			setRadio_strength(artl.arnl.handleNORpackets.getRadio_strength());
	            			// set nor queue status
	            			setNOR_queue_is_full(artl.arnl.handleNORpackets.isB_NOR_message_queue_full());
	            			// increment the number of radio status rx
	            			setL_num_radio_status_w(getL_num_radio_status_w() + 1);
		        		}
			        	// increment the number of posted messages
			        	setL_num_msg_tx(getL_num_msg_tx() + 1);
					}catch(RuntimeException e ){
						throw new RuntimeException("cannot post diagnostic message",e);
					}
		        	profile.log("goto rx");
		        	// switch to next status
		        	diagnostic_loop_status=enum_diagnostic_loop_status.rx_messages;
		        	break;
	            	
            	}
            	// check for incoming messages
            	case rx_messages:
            	{
	        		profile.log("enter rx status");
		        	// reset the number of messages received
		        	int i_nrx=0;
					// get all of the messages you can
					if(artl.getQueueMessages().isEmpty()){
						try {
							Thread.sleep(0);
						} catch (InterruptedException e) {
						}
					}
					else if(!artl.getQueueMessages().isEmpty()){
						try {
							// read the message from the queue
							radio_message_rx=artl.getQueueMessages().take();
		    				profile.log("message rx");
							// message from the module???
			    			if (radio_message_rx.from_terminal_number==artl.arnl.m_byte_radio_module_terminal_number){
			    				profile.log("message from module");
			    				// force interpretation of a module message
				        		//armc.force_expect_radio_status();
				        		armc.force_expect_short_radio_status();
			    				enum_module_reply_type module_reply;
			    				StringBuilder reply=new StringBuilder();
			    				String mytext = new String(radio_message_rx.msg.message, 0, radio_message_rx.msg.numBytesInMessage);
			    				// check the reply if valid and parse fields
			                    module_reply=armc.check_responses(mytext,reply);
			                    // if there is a reply from the module, printout the reply from the module check
			            		if (module_reply==enum_module_reply_type.enum_radio_module_reply_radio_status){
			            			// set the work mode
			            			setWork_mode(armc.getWork_mode());
			            			// set the queue status
			            			setTxqueue_status(armc.getTxqueue_status());
			            			// set the radio signal strength
			            			setRadio_strength(armc.getRadio_strength());
			            			// set the rssi value
			            			setRssi(armc.getRssi());
			            			// increment the number of radio status rx
			            			setL_num_radio_status_w(getL_num_radio_status_w() + 1);
			            		}
			            		else if (module_reply==enum_module_reply_type.enum_radio_module_reply_short_radio_status){
				    				profile.log("short radio status");
			            			if (!armc.isLast_message_queued_OK()){
			            				setNum_radio_module_queued_messages_KO(getNum_radio_module_queued_messages_KO() + 1);
			            			}
			            			else{
			            				setNum_radio_module_queued_messages_OK(getNum_radio_module_queued_messages_OK() + 1);
			            			}
			            			// set the work mode
			            			setWork_mode(armc.getWork_mode());
			            			// set the radio signal strength
			            			setRadio_strength(armc.getRadio_strength());
			            			// increment the number of radio status rx
			            			setL_num_radio_status_w(getL_num_radio_status_w() + 1);
			            			// set nor queue status
			            			setNOR_queue_is_full(armc.isB_message_queue_NOR_is_full());
			            		}
			    			}
			    			// else the message comes from the PC, check if the format is correct
			    			else{
			    				String s_rx="";
								try {
									s_rx = new String(radio_message_rx.msg.message, "UTF-8");
									s_rx=s_rx.substring(0, radio_message_rx.msg.numBytesInMessage);
								} catch (UnsupportedEncodingException e) {
								}
			    				int idx_packet_rx=s_rx.indexOf(s_valid_msg_rx_header);
			    				int idx_packet_num_in_string=idx_packet_rx+s_valid_msg_rx_header.length();
			    				if (idx_packet_rx>=0 && idx_packet_num_in_string<radio_message_rx.msg.numBytesInMessage){
			    					int i_rx_index=radio_message_rx.msg.message[idx_packet_num_in_string]-'0';
				            		setI_cur_receive_index(i_rx_index);

									// a valid message has been received, for me it is OK
									artl.stats.numMessagesRx++;
									setL_num_msg_rx(getL_num_msg_rx() + 1);
									// increase the number of messages actually received
									i_nrx++;
			    				}
			    				else{
			    					setL_num_msg_rx_bad_format(getL_num_msg_rx_bad_format() + 1);
			    				}
			    				
			    			}
							
						} catch (InterruptedException e) {
						}											
					}
					// update the statistics
					rtmps.update(i_nrx);
					// if the minimum pause time between message posts is elapsed, post another message
					// if max time between posts has elapsed, exit from the loop
		        	long l_act_time=System.currentTimeMillis();
		        	if ((l_act_time-l_last_time_post_ms>=getPause_between_post_ms()) || (l_act_time<l_last_time_post_ms)){
		        		// reset the start time
		        		l_last_time_post_ms=l_act_time;
		        		// restart form status 0
		        		diagnostic_loop_status=enum_diagnostic_loop_status.post;
		        		profile.log("end timeout rx status");
						break;
		        	}
            	}
        	}
    	}
		
	}
	
	/**
	 * THE THREAD RUNNING THE DIAGNOSTIC TEST
	 */
    public void run() throws RuntimeException{
    	// grant access to the network layer
		artl.lock();
		try{
			// asks the radio ON
			this.artl.asks_radio_power();


			// change thread status to running
			thread_status=enum_diagnostic_thread_status.running;
			// run the thread loop
			this.the_thread();
		}
		finally{
			// switch to ends state
			thread_status=enum_diagnostic_thread_status.ends;

			// release the radio ON
			this.artl.release_radio_power();

            // release access to the network layer
    		artl.unlock(); 
		}

    }

    /**
     * starts the diagnostic thread
     */
    public void start() {
    	thread_diagnostic = new Thread(this);
    	if (thread_diagnostic!=null){
    		thread_diagnostic.start();
    	}
    }
    /**
     * kills the diagnostic thread
     * @param wait_ms max time to wait before thread stops
     * @return true if thread was stopped within wait_ms, else false
     */
    public boolean kill(int wait_ms){
    	boolean b_stopped=true;
    	if (thread_diagnostic!=null){
    		thread_diagnostic=null;
    		long l_wait_ms=0;
    		while (thread_status!=enum_diagnostic_thread_status.ends){
    			try {
    				final int my_pause_ms=10;
					Thread.sleep(my_pause_ms);
					l_wait_ms+=my_pause_ms;
				} catch (InterruptedException e) {
				}
    			if (l_wait_ms>=wait_ms){
    				break;
    			}
    		}
    		if (thread_status==enum_diagnostic_thread_status.ends){
    			b_stopped=true;
    		}
    		else{
    			b_stopped=false;
    		}
    	}
    	return b_stopped;
    }

    /**
     * 
     * @return true if diagnostic thread is running, else false
     */
    public boolean isrunning(){
    	return (thread_diagnostic!=null)&&(thread_diagnostic.isAlive());
    }

	public enum_radio_work_mode getWork_mode() {
		return work_mode;
	}

	private void setWork_mode(enum_radio_work_mode work_mode) {
		this.work_mode = work_mode;
		if (this.work_mode==enum_radio_work_mode.search){
			setNum_times_radio_search_mode(getNum_times_radio_search_mode() + 1);
		}
	}

	public enum_radio_txqueue_status getTxqueue_status() {
		return txqueue_status;
	}

	private void setTxqueue_status(enum_radio_txqueue_status txqueue_status) {
		this.txqueue_status = txqueue_status;
	}

	public enum_radio_strength getRadio_strength() {
		return radio_strength;
	}

	private void setRadio_strength(enum_radio_strength radio_strength) {
		this.radio_strength = radio_strength;
	}

	public int getRssi() {
		return rssi;
	}

	private void setRssi(int rssi) {
		this.rssi = rssi;
	}

	public long getL_pause_every_N_ms() {
		return l_pause_every_N_ms;
	}

	public void setL_pause_every_N_ms(long l_pause_every_N_ms) {
		this.l_pause_every_N_ms = l_pause_every_N_ms;
	}

	public long getL_diagnostic_thread_begin_time() {
		return l_diagnostic_thread_begin_time;
	}

	public void setL_diagnostic_thread_begin_time(long l_diagnostic_thread_begin_time) {
		this.l_diagnostic_thread_begin_time = l_diagnostic_thread_begin_time;
	}

	public int getPause_between_post_ms() {
		return pause_between_post_ms;
	}

	public void setPause_between_post_ms(int pause_between_post_ms) {
		this.pause_between_post_ms = pause_between_post_ms;
	}


	private int get_num_packet_waiting(){
    	int i_actual_distance_txrx;
    	i_actual_distance_txrx=getI_cur_send_index()-getI_cur_receive_index();
    	if (i_actual_distance_txrx<0){
    		i_actual_distance_txrx+=i_max_index;
    	}
    	return i_actual_distance_txrx;
	}

	private void v_init_wait_packetstatus(){
		i_status_wait_packet=0;
		l_base_timeout_packet_wait=System.currentTimeMillis();
		init_waiting_packets_indexes();			
	}
	private void v_update_wait_packetstatus(){
		switch(i_status_wait_packet){
		case 0:
		default:
		{
			if (get_num_packet_waiting()>i_max_distance_tx_rx_index){
				l_base_timeout_packet_wait=System.currentTimeMillis();
				i_status_wait_packet=1;
			}
			break;
		}
		case 1:
		{
			if (get_num_packet_waiting()<i_max_distance_tx_rx_index){
			//if (get_num_packet_waiting()<=1){
				i_status_wait_packet=0;
				break;
			}
			long l_act_time=System.currentTimeMillis();
			if ((l_act_time<l_base_timeout_packet_wait)||(l_act_time>l_base_timeout_packet_wait+getL_max_wait_packets_ms())){
				// if timeout waiting packets, reset packets indexes
				v_init_wait_packetstatus();
				break;
			}
			break;
			
		}
		}
	}
	public boolean isB_waiting_packets() {
		return i_status_wait_packet!=0;
	}
	private int get_a_new_send_index(){
		incrementI_cur_send_index();
		return getI_cur_send_index();
	}
	private int getI_cur_send_index() {
		return i_cur_send_index;
	}
	private void incrementI_cur_send_index() {
		int i_new_index;
		i_new_index=getI_cur_send_index()+ 1;
    	if (i_new_index>i_max_index){
    		i_new_index=i_min_index;
    	}
		setI_cur_send_index(i_new_index);
	}
	private int setI_cur_send_index(int index) {
    	if ((index<=i_max_index)&&(index>=i_min_index)){
    		this.i_cur_send_index = index;
    	}
    	
		return i_cur_send_index;
	}

	private int getI_cur_receive_index() {
		return i_cur_receive_index;
	}
	private void setI_cur_receive_index(int index) {
    	if ((index<=i_max_index)&&(index>=i_min_index)){
    		this.i_cur_receive_index = index;
    	}
	}
	public long getL_num_msg_tx() {
		return l_num_msg_tx;
	}
	public void setL_num_msg_tx(long l_num_msg_tx) {
		this.l_num_msg_tx = l_num_msg_tx;
	}
	public long getL_num_msg_rx() {
		return l_num_msg_rx;
	}
	public void setL_num_msg_rx(long l_num_msg_rx) {
		this.l_num_msg_rx = l_num_msg_rx;
	}
	public long getL_num_msg_rx_bad_format() {
		return l_num_msg_rx_bad_format;
	}
	public void setL_num_msg_rx_bad_format(long l_num_msg_rx_bad_format) {
		this.l_num_msg_rx_bad_format = l_num_msg_rx_bad_format;
	}
	public long getL_num_radio_status_w() {
		return l_num_radio_status_w;
	}
	public void setL_num_radio_status_w(long l_num_radio_status_w) {
		this.l_num_radio_status_w = l_num_radio_status_w;
	}
	public long getL_num_radio_status_r() {
		return l_num_radio_status_r;
	}
	public void setL_num_radio_status_r(long l_num_radio_status_r) {
		this.l_num_radio_status_r = l_num_radio_status_r;
	}
	public long getL_max_wait_packets_ms() {
		return l_max_wait_packets_ms;
	}
	public void setL_max_wait_packets_ms(long l_max_wait_packets_ms) {
		this.l_max_wait_packets_ms = l_max_wait_packets_ms;
	}
	public int getNum_radio_module_queued_messages_KO() {
		return num_radio_module_queued_messages_KO;
	}
	public void setNum_radio_module_queued_messages_KO(
			int num_radio_module_queued_messages_KO) {
		this.num_radio_module_queued_messages_KO = num_radio_module_queued_messages_KO;
	}
	public int getNum_radio_module_queued_messages_OK() {
		return num_radio_module_queued_messages_OK;
	}
	public void setNum_radio_module_queued_messages_OK(
			int num_radio_module_queued_messages_OK) {
		this.num_radio_module_queued_messages_OK = num_radio_module_queued_messages_OK;
	}
	public boolean isNOR_queue_is_full() {
		return NOR_queue_is_full;
	}
	public void setNOR_queue_is_full(boolean nOR_queue_is_full) {
		NOR_queue_is_full = nOR_queue_is_full;
		if (NOR_queue_is_full){
			setNum_times_NOR_queue_full(getNum_times_NOR_queue_full() + 1);
		}
	}
	public int getNum_times_NOR_queue_full() {
		return num_times_NOR_queue_full;
	}
	public void setNum_times_NOR_queue_full(int num_times_NOR_queue_full) {
		this.num_times_NOR_queue_full = num_times_NOR_queue_full;
	}
	public int getNum_times_radio_search_mode() {
		return num_times_radio_search_mode;
	}
	public void setNum_times_radio_search_mode(int num_times_radio_search_mode) {
		this.num_times_radio_search_mode = num_times_radio_search_mode;
	}

}


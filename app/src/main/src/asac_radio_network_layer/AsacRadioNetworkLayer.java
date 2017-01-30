package asac_radio_network_layer;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import asacIntents.AsacIntents;
import asacLEDs.Asac_LEDs;
import asacRadioRFPower.Asac_radioRF_power;
import messageWithIdQueue.IdQueue;

import Profile.LogProfile;

import android.content.Intent;
import android.util.Log;

import android.content.Context;
import asacradio_crc.Asacradio_crc;
import asac_radio_module_commands.Asac_radio_module_commands;
import asac_radio_module_commands.Asac_radio_module_commands.enum_radio_strength;
import asac_radio_module_commands.Asac_radio_module_commands.enum_radio_work_mode;
import asac_radio_module_commands.Asac_radio_module_commands.enum_short_radio_strength;
import asac_radio_module_commands.Asac_radio_module_commands.enum_short_radio_work_mode;
import asac_radio_network_layer_message.Asac_radio_network_layer_message;
import asac_radio_data_link_layer.Asac_radio_data_link_layer;
import asac_radio_data_link_layer.Asac_radio_data_link_layer.enum_ardl_log_enable;
import asac_chars_rx_from_serial.Asac_chars_rx_from_serial;
import asac_synchro_obj_produced.Synchro_Obj_Produced;

/**
 * this class handles the network layer, basically it addes / remove the byte stuffing codes
 * @author root
 *
 */
public class AsacRadioNetworkLayer implements Runnable{
	// our class tag
	private static final String TAG = "network_layer";
	// max elements in serial queue
	private final int i_queue_rx_from_serial_max_elements=32;
	//Creating BlockingQueue of size i_r_from_serial_max_elements
    private BlockingQueue<Asac_chars_rx_from_serial> queue;
    private Asac_radio_data_link_layer asac_radio_data_link_layer;
 // the queue where we'll put the bytes chunk we receive
 	private BlockingQueue<AsacRadioNetworkLayer.AsacRadioMessageFromTerminal> queue_msgs; 
	// the object used to synchronize received messages
	public Synchro_Obj_Produced synchro_msg_received=new Synchro_Obj_Produced();
	private byte m_byte_destination_terminal_number=(byte) 0x1; // the destination (pc)
	public final byte m_byte_radio_module_terminal_number=(byte)0xff;// the destination address, when it's radio module
	public final int i_byte_radio_module_terminal_number=-1;// the destination address, when it's radio module
	private final Lock access_post = new ReentrantLock(true);
	private final Lock access_build_message = new ReentrantLock(true);
	private final byte byte_SERIAL_EOM =13; // 0x0D     Serial Eom
	private final byte byte_SERIAL_DLE =29; // 0x1D     Serial Dle 
	private final byte byte_SERIAL_STX =30; // 0x1E     Serial Stx 
	private final byte byte_SERIAL_ETX =31; // 0x1F     Serial Etx 
	private final byte byte_RADIO_DLE  =26; // 0x1A     Radio  Dle 
	private final byte byte_RADIO_NULL = 0; // 0x00     Radio  NULL 
	
	LogProfile profile=null;
	private Asac_radio_module_commands armc;
	private IdQueue idQueue;
	public HandleNORpackets handleNORpackets;
	/**
	 * who should interpret the message I am sending?
	 * @author root
	 *
	 */
	public enum enum_rnl_message_destination_type{
		message_destination_server,  // the destination of the message is the server i.e. the PC
		message_destination_radio_module, //the destination of the message is the radio module
	};
	
	/**
	 * the post / build message routine can typically return one of these values
	 * @author root
	 *
	 */
	public enum EnumRnlReturnCode{
		ok,
		too_long_body,
		invalid_value,
		timeout_waiting_ack,
		invalid_reply_length, invalid_reply_header, unable_queue_NOR_msg,
	};
	
	/**
	 * an array containing some statistics of the radio communication
	 * @author root
	 *
	 */
	public class Asac_radio_tx_stats{
		public int[]  i_num_msg_tx_retcodes= new int[EnumRnlReturnCode.values().length];
	};
	public Asac_radio_tx_stats tx_stats =new Asac_radio_tx_stats();
	
	/**
	 * a method to get the radio transmission statistics
	 * @return
	 */
	public Asac_radio_tx_stats get_tx_stats(){
		return tx_stats;
	}
	
	/**
	 * the check codes for transmission and receive messages
	 */
	private Asacradio_crc msg_crc = new Asacradio_crc();
	private Asacradio_crc msg_crc_receiving = new Asacradio_crc();
	/**
	 * the list of the routines that can be logged
	 * @author root
	 *
	 */
 	public enum enum_arnl_log_enable{
 		receiving_thread,
 		post_messages,
 		ardl_receiving_thread,
 		ardl_write_bytes_to_serial_port,
 	};
 	
 	/**
 	 * the class which handles the logging
 	 * @author root
 	 *
 	 */
	Thread thread = null;
    public class Log_radio_network_layer_enable{
    	private boolean[] b_enable= new boolean[enum_arnl_log_enable.values().length];
    	Asac_radio_data_link_layer ardl;
    	public Log_radio_network_layer_enable(Asac_radio_data_link_layer ardl){
    		this.ardl=ardl;
    		disable_all();
    	}
    	public void disable_all(){
    		for (enum_arnl_log_enable e:enum_arnl_log_enable.values()){
    			enable_disable(e, false);
    		}
    	}
    	public boolean is_enabled(enum_arnl_log_enable e){
    		return b_enable[e.ordinal()];
    	}
    	public void enable_disable(enum_arnl_log_enable e, boolean value){
    		b_enable[e.ordinal()]=value;
    		switch(e){
	    		case ardl_receiving_thread:
	    		{
	    			this.ardl.log_enable.enable_disable(enum_ardl_log_enable.receiving_thread, value);
	    			break;
	    		}
	    		case ardl_write_bytes_to_serial_port:
	    		{
	    			this.ardl.log_enable.enable_disable(enum_ardl_log_enable.write_bytes_to_serial_port, value);
	    			break;
	    		}
	    		default:
	    		{
	    			break;
	    		}
    		}
    	}
    	public void enable(enum_arnl_log_enable e){
    		enable_disable(e,true);
    	}
    	public void disable(enum_arnl_log_enable e){
    		enable_disable(e,false);
    	}
    }
    public Log_radio_network_layer_enable log_enable;
	
	
	/**
	 * receive network layer status of the receiving message check
	 * @author root
	 *
	 */
	private enum enum_rnl_status{
		wait_stx,
		wait_terminal_number,
		wait_terminal_number_after_dle,
		read_body,
		read_body_after_dle,
		read_crc_hi,
		read_crc_hi_after_dle,		
		read_crc_lo,
		read_crc_lo_after_dle,		
		check_eom,
	};
	
	/**
	 * the receive return code
	 * @author root
	 *
	 */
	public enum enum_rnl_rx_return_code{
		ok,
		too_long_body,
		bad_crc,
		missing_etx_at_message_end,
	};
    
 /**
  * the receive characters thread...
  */
    public void run() {
    	while(true){
    		try{
    			Asac_chars_rx_from_serial asac_chars_rx_from_serial=queue.take();
        		// update the message I am receiving
        		v_update_msg_receiving(asac_chars_rx_from_serial.the_bytes, asac_chars_rx_from_serial.num_bytes_in_buffer);
    		}catch(InterruptedException e) {
        		//e.printStackTrace();
        		continue;
        	}
    	}
    }
	private Asac_LEDs asacLEDs;
	public int delay_between_radio_status_updates_ms = 300;
	public int delay_after_radio_busy_before_status_request_ms = 1000;
	private Asac_radioRF_power asac_radioRF_power;
    /** 
     * constructor
     * @param m_the_context the context, which is passed to the data link layer to open the serial port
     * @param q the queue where to put the received messages
     * @throws RuntimeException
     */
	public AsacRadioNetworkLayer(Context m_the_context,BlockingQueue<AsacRadioNetworkLayer.AsacRadioMessageFromTerminal> q, IdQueue idQueue, Asac_radioRF_power asac_radioRF_power) throws RuntimeException{
		Log.i(TAG,"creating network layer");
		asacLEDs = new Asac_LEDs(m_the_context);
		// a meaningless comment
		asacLEDs.enableAutoBluedLED(true);
		this.asac_radioRF_power = asac_radioRF_power;

		profile=new LogProfile();
		this.armc=new Asac_radio_module_commands(m_the_context);
		this.idQueue=idQueue;
		// creates the NOR packets handler
		this.handleNORpackets=new HandleNORpackets(this.armc, m_the_context);
		// start the NOR packets thread
		this.handleNORpackets.thread = new Thread(this.handleNORpackets);
		this.handleNORpackets.thread.start();
		 //Creating BlockingQueue of size i_r_from_serial_max_elements
		try{
			queue = new ArrayBlockingQueue<Asac_chars_rx_from_serial>(i_queue_rx_from_serial_max_elements);
		}catch(IllegalArgumentException e){
			throw new RuntimeException("Unable to create exchange queue", e);
		}
	    try{
			// create data link layer 
			asac_radio_data_link_layer=new Asac_radio_data_link_layer(m_the_context,queue);
	    }catch(RuntimeException e){
	    	throw new RuntimeException("Unable to create data link layer", e);
	    }
	    // create the log enable/disable object
		log_enable=new Log_radio_network_layer_enable(asac_radio_data_link_layer);
		
		// save the queue of the upper level
		this.queue_msgs=q;
		
		// thread start
		try{
			// starts the receiving thread run() which waits for the incoming messages
	        thread = new Thread(this);
			thread.start();
		}catch (IllegalThreadStateException	e){
        	Log.e(TAG, "the receiving thread can' t be started");
        	throw new RuntimeException("Unable to start receiving thread");
        }
		Log.i(TAG,"network layer created OK");
	}	
	
	
	
	/**
	 * the receive statistics
	 * @author root
	 *
	 */
	public class AsacRadioRxStats{
		public int[] i_num_msg_rx_retcodes= new int[enum_rnl_rx_return_code.values().length];
	};
	/**
	 * a class to handle received messages
	 * @author root
	 *
	 */
	public class AsacRadioMessageFromTerminal {
		public Asac_radio_network_layer_message msg;
		public int from_terminal_number;
		/**
		 * clear the message
		 */
		public void clear(){
			this.msg.clear();
			this.from_terminal_number=0;
		}
		/**
		 * the constructor
		 */
		public AsacRadioMessageFromTerminal(){
			this.msg=new Asac_radio_network_layer_message();
			this.clear();
		}
		/**
		 *  creates a copy of the source object:
		 *   only the min(ASAC_RADIO_NETWORK_LAYER_MESSAGE_SIZE, source.num_bytes_in_messsage) will be copied from source into message 
		 * @param source the source objetc to copy
		 * @return the newly copied message
		 */
		public AsacRadioMessageFromTerminal Copy(AsacRadioMessageFromTerminal source){
			AsacRadioMessageFromTerminal n=new AsacRadioMessageFromTerminal();
			// copy the termina number
			n.from_terminal_number=source.from_terminal_number;
			// now, copy the message
			n.msg=n.msg.Copy(source.msg);
			return n;
		}		
		public AsacRadioMessageFromTerminal Copy(Asac_radio_network_layer_message msg){
			AsacRadioMessageFromTerminal n=new AsacRadioMessageFromTerminal();
			n.msg.numBytesInMessage=msg_received.numBytesInMessage;
			System.arraycopy(msg_received.message,0,n.msg.message,0,msg_received.numBytesInMessage);
			n.msg.b_message_valid=msg_received.b_message_valid;
			return n;
		}

	}
	public AsacRadioRxStats rx_stats =new AsacRadioRxStats();
	/**
	 * returns the actual receiving statistics
	 * @return
	 */
	public AsacRadioRxStats get_rx_stats(){
		return rx_stats;
		
	}
	private Asac_radio_network_layer_message msg_receiving=new Asac_radio_network_layer_message();
	private int i_terminal_number_msg_receiving=0;

	
	private Asac_radio_network_layer_message msg_received=new Asac_radio_network_layer_message();
	private int i_terminal_number_msg_received=0;
	//private boolean b_new_valid_msg_received=false;
	private enum_rnl_status status_msg_receiving=enum_rnl_status.wait_stx;
	private enum_rnl_rx_return_code receive_return_code;
	
	/**
	 * resets the message I am currently receiving
	 */
	private void v_reset_msg_receiving(){
		this.msg_receiving.numBytesInMessage=0;
		this.i_terminal_number_msg_receiving=0;
		this.receive_return_code=enum_rnl_rx_return_code.ok;
		this.status_msg_receiving=enum_rnl_status.wait_stx;		
	}
	
	/**
	 * initializes the radio network with the destination terminal number 
	 * @param byte_destination_terminal_number
	 */
	public void init_radio_network(byte byte_destination_terminal_number){
		this.m_byte_destination_terminal_number=byte_destination_terminal_number;
		this.v_reset_msg_receiving();
		this.msg_received.numBytesInMessage=0;
	}
	
	/**
	 *  check if the passed byte should be stuffed
	 * @param byte_to_stuff the byte to check
	 * @return true if the byte should be stuffed
	 */
	private boolean is_byte_to_stuff(byte byte_to_stuff){
		boolean ret_value_is_byte_to_stuff;
		
		ret_value_is_byte_to_stuff=false;
		if (  (byte_to_stuff==this.byte_SERIAL_EOM)
			||(byte_to_stuff==this.byte_SERIAL_DLE)
			||(byte_to_stuff==this.byte_SERIAL_STX)
			||(byte_to_stuff==this.byte_SERIAL_ETX)
		){
			ret_value_is_byte_to_stuff=true;
		}
		
		return ret_value_is_byte_to_stuff;
	}
	
	/**
	 * check if the passed binary byte should be stuffed
	 * @param byte_to_stuff the byte to check
	 * @return true if the binary byte should be stuffed
	 */
	private boolean is_binary_byte_to_stuff(byte byte_to_stuff){
		boolean ret_value_is_binary_byte_to_stuff;
		
		ret_value_is_binary_byte_to_stuff=false;
		if (  (this.is_byte_to_stuff(byte_to_stuff))
			||(byte_to_stuff==this.byte_RADIO_DLE)
			||(byte_to_stuff==this.byte_RADIO_NULL)
		){
			ret_value_is_binary_byte_to_stuff=true;
		}
		
		return ret_value_is_binary_byte_to_stuff;
	}

	public void stop()
	{
		asacLEDs.stop();
		if (this.thread != null)
		{
			this.thread.interrupt();
			this.thread = null;
		}
		this.handleNORpackets.stop(100);
		this.asac_radio_data_link_layer.stop();
	}
	
	/**
	 * a class which handles the stuffing of the messages
	 * @author root
	 *
	 */
	public class StuffParams{
		public final int i_max_bytes_to_read;
		public final int i_max_bytes_to_write;
		public int i_num_bytes_written;
		public int i_num_bytes_read;	
		public StuffParams(int max_bytes_to_read,int max_bytes_to_write){
			i_max_bytes_to_read=max_bytes_to_read;
			i_max_bytes_to_write=max_bytes_to_write;
			i_num_bytes_written=0;
			i_num_bytes_read=0;
		}
	}
	
	/**
	 *  execute a stuff of the binary source array into the destination array, given the stuff parameters this routine never fails, at most it won't do anything on the given arrays
	 * @param b_src the source byte array
	 * @param b_dst the destination byte array
	 * @param params the parameters of the stuffing (e.g. max bytes to read / write etc)
	 */
	public void binaryBufferStuff( final byte[] b_src, byte[] b_dst, StuffParams params){
		int i_num_max_bytes_to_read;
		int i_num_max_bytes_to_write;
		int i_num_bytes_written;
		int i_num_bytes_read;
		i_num_max_bytes_to_read=params.i_max_bytes_to_read;
		i_num_max_bytes_to_write=params.i_max_bytes_to_write;
		// check for strange errors!
		if (b_src.length<i_num_max_bytes_to_read){
			i_num_max_bytes_to_read=b_src.length;
		}
		if (b_dst.length<i_num_max_bytes_to_write){
			i_num_max_bytes_to_write=b_dst.length;
		}
		i_num_bytes_written=0;
		i_num_bytes_read=0;	
		int idx_write=0;
		for (int idx_read=0;idx_read<i_num_max_bytes_to_read;idx_read++){
			if(i_num_bytes_read>=i_num_max_bytes_to_read){
				break;
			}
			// if the binary byte should be stuffed...
			if (is_binary_byte_to_stuff(b_src[idx_read])){
				if(i_num_max_bytes_to_write-i_num_bytes_written<2){
					break;
				}				
				b_dst[idx_write++]=byte_RADIO_DLE;
				b_dst[idx_write++]=(byte) (b_src[idx_read]|0x80);
				i_num_bytes_written+=2;
				i_num_bytes_read++;
			}
			// else just copy
			else{
				if(i_num_max_bytes_to_write-i_num_bytes_written<1){
					break;
				}
				b_dst[idx_write++]=b_src[idx_read];
				i_num_bytes_written++;
				i_num_bytes_read++;
			}
		}
		params.i_num_bytes_read=i_num_bytes_read;
		params.i_num_bytes_written=i_num_bytes_written;
	}
	
	
	// the check code received
    private int icrc_received;
    
    /**
     * update the message received with the bytes which come from the data link layer
     * @param bytes the bytes received
     * @param i_num_bytes_rx the number of bytes to check
     */
	public void v_update_msg_receiving(byte[] bytes, int i_num_bytes_rx){
		final String localTag=TAG+" receiving thread";
		// loop through bytes received
		for (int idx_byte_rx=0;idx_byte_rx<i_num_bytes_rx; idx_byte_rx++){
			// status machine to handle received bytes
			switch(this.status_msg_receiving){
				// waiting for initial STX
				case wait_stx:
				default:
					// if an STX has been received, proceed waiting the remaining part of the message
					if (bytes[idx_byte_rx]==byte_SERIAL_STX){
						if (log_enable.is_enabled(enum_arnl_log_enable.receiving_thread)){
							Log.i(localTag,"STX found");
						}
						this.msg_crc_receiving.init_crc();
						msg_receiving.b_message_valid=false;
						this.msg_receiving.numBytesInMessage=0;
						this.receive_return_code=enum_rnl_rx_return_code.ok;
						this.status_msg_receiving=enum_rnl_status.wait_terminal_number;
					}
					break;
				// waiting for terminal number
				case wait_terminal_number:
					if (bytes[idx_byte_rx]==byte_SERIAL_DLE){
						this.status_msg_receiving=enum_rnl_status.wait_terminal_number_after_dle;
						break;
					}
					this.msg_crc_receiving.update_crc(bytes[idx_byte_rx]);//update the crc 
					this.i_terminal_number_msg_receiving=bytes[idx_byte_rx]; //save the  byte
					this.status_msg_receiving=enum_rnl_status.read_body;					
					if (log_enable.is_enabled(enum_arnl_log_enable.receiving_thread)){
						Log.i(localTag,"terminal number found"+String.valueOf(this.i_terminal_number_msg_receiving));
					}
					break;
				// waiting for terminal number character after DLE
				case wait_terminal_number_after_dle: {
					byte b;
					b = (byte) ((bytes[idx_byte_rx] & ~0x80) & 0xff);
					this.msg_crc_receiving.update_crc(b);//update the crc using the stuffed byte
					this.i_terminal_number_msg_receiving = b; //save the unstuffed byte
					this.status_msg_receiving = enum_rnl_status.read_body;
					break;
				}
				// reading the message body
				case read_body:
				{
					byte b;
					b=(byte)(bytes[idx_byte_rx]);
					// end of message detected?
					if (b==byte_SERIAL_EOM){
						if (log_enable.is_enabled(enum_arnl_log_enable.receiving_thread)){
							Log.i(localTag,"end of body found, length"+String.valueOf(this.msg_receiving.numBytesInMessage));
						}
						this.status_msg_receiving=enum_rnl_status.read_crc_hi;
						break;
					}
					// escape character received?
					if (b==byte_SERIAL_DLE){
						this.status_msg_receiving=enum_rnl_status.read_body_after_dle;
						break;
					}
					this.msg_crc_receiving.update_crc(b);//update the CRC
					// maybe the message is too long?
					if (this.msg_receiving.numBytesInMessage<this.msg_receiving.message.length){
						this.msg_receiving.message[this.msg_receiving.numBytesInMessage]=b; //save the byte
						this.msg_receiving.numBytesInMessage++;
					}
					// message is really too long
					else{
						// change the return code
						if (this.receive_return_code==enum_rnl_rx_return_code.ok){
							this.receive_return_code=enum_rnl_rx_return_code.too_long_body;
							this.rx_stats.i_num_msg_rx_retcodes[receive_return_code.ordinal()]++;
						}
					}					
					break;
				}
				case read_body_after_dle:
				{
					byte b;
					b=(byte)((bytes[idx_byte_rx]&~0x80)&0xff);
					this.msg_crc_receiving.update_crc(b);//update the CRC
					if (this.msg_receiving.numBytesInMessage<this.msg_receiving.message.length){
						this.msg_receiving.message[this.msg_receiving.numBytesInMessage]=b; //save the byte
						this.msg_receiving.numBytesInMessage++;
					}
					else{
						if (this.receive_return_code==enum_rnl_rx_return_code.ok){
							this.receive_return_code=enum_rnl_rx_return_code.too_long_body;
							this.rx_stats.i_num_msg_rx_retcodes[receive_return_code.ordinal()]++;
						}
					}
					this.status_msg_receiving=enum_rnl_status.read_body;
					break;
				}
				// high byte of the CRC
				case read_crc_hi:
					if (bytes[idx_byte_rx]==byte_SERIAL_DLE){
						this.status_msg_receiving=enum_rnl_status.read_crc_hi_after_dle;
						break;
					}		
					this.icrc_received=(bytes[idx_byte_rx])&0xff;
					this.icrc_received<<=8;
					this.status_msg_receiving=enum_rnl_status.read_crc_lo;
					if (log_enable.is_enabled(enum_arnl_log_enable.receiving_thread)){
						Log.i(localTag,"crc hi found");
					}
					break;
				case read_crc_hi_after_dle:
					this.icrc_received=((bytes[idx_byte_rx]&~0x80)&0xff);
					this.icrc_received<<=8;
					this.status_msg_receiving=enum_rnl_status.read_crc_lo;
					break;
				// low byte of the CRC
				case read_crc_lo:
					if (bytes[idx_byte_rx]==byte_SERIAL_DLE){
						this.status_msg_receiving=enum_rnl_status.read_crc_lo_after_dle;
						break;
					}			
					this.icrc_received+=(bytes[idx_byte_rx]&0xff);
					int i_crc_expected=this.msg_crc_receiving.get_crc()&0xffff;
					int i_crc_received=this.icrc_received&0xffff;
					if (i_crc_expected != i_crc_received){
						if (log_enable.is_enabled(enum_arnl_log_enable.receiving_thread)){
							Log.i(localTag,"BAD CRC; expected "+String.valueOf(i_crc_expected)+ " received "+String.valueOf(i_crc_received));
						}
						if (this.receive_return_code==enum_rnl_rx_return_code.ok){
							this.receive_return_code=enum_rnl_rx_return_code.bad_crc;
							this.rx_stats.i_num_msg_rx_retcodes[receive_return_code.ordinal()]++;
						}
					}
					// check if CRC is correct
					this.status_msg_receiving=enum_rnl_status.check_eom;
					break;
				case read_crc_lo_after_dle:
					this.icrc_received+=(bytes[idx_byte_rx]&~0x80)&0xff;
					// check CRC
					if (this.msg_crc_receiving.get_crc()!=this.icrc_received){
						if (log_enable.is_enabled(enum_arnl_log_enable.receiving_thread)){
							Log.i(localTag,"BAD CRC after DLE; expected "+String.valueOf(this.msg_crc_receiving.get_crc())+ " received "+String.valueOf(this.icrc_received));
						}
						if (this.receive_return_code==enum_rnl_rx_return_code.ok){
							this.receive_return_code=enum_rnl_rx_return_code.bad_crc;
							this.rx_stats.i_num_msg_rx_retcodes[receive_return_code.ordinal()]++;
						}
					}
					// check if CRC is correct
					this.status_msg_receiving=enum_rnl_status.check_eom;
					break;
				// now ETX should be received
				case check_eom:
					if (log_enable.is_enabled(enum_arnl_log_enable.receiving_thread)){
						Log.i(localTag,"checking EOM (SERIAL_STX)");
					}
					if (bytes[idx_byte_rx]!=this.byte_SERIAL_ETX){
						if (log_enable.is_enabled(enum_arnl_log_enable.receiving_thread)){
							Log.i(localTag,"found byte"+String.valueOf(bytes[idx_byte_rx])+"instead of eom");
						}
						if (this.receive_return_code==enum_rnl_rx_return_code.ok){
							this.receive_return_code=enum_rnl_rx_return_code.missing_etx_at_message_end;
							this.rx_stats.i_num_msg_rx_retcodes[receive_return_code.ordinal()]++;
						}
					}
					if (this.receive_return_code==enum_rnl_rx_return_code.ok){
						msg_receiving.b_message_valid=true;
						//msg_received=msg_receiving;
						this.msg_received=msg_received.Copy(msg_receiving);
						this.i_terminal_number_msg_received=this.i_terminal_number_msg_receiving;
						//this.b_new_valid_msg_received=true;
						this.rx_stats.i_num_msg_rx_retcodes[receive_return_code.ordinal()]++;
						// signal that a new object has been produced, this should be done at the very end, else 
						// e.g. the statistics are not refreshed in the correct manner
						synchro_msg_received.signal_new_object_produced();
						if (log_enable.is_enabled(enum_arnl_log_enable.receiving_thread)){
							Log.i(localTag,"message received OK");
						}

						// queue the buffer read!
			    		try{
			    			AsacRadioMessageFromTerminal radio_msg=new AsacRadioMessageFromTerminal();
			    			radio_msg.from_terminal_number=i_terminal_number_msg_received;
			    			// if the message comes from the NOR, the first byte is the message ID, so put the message in the ID queue
			    			if (i_terminal_number_msg_received==i_byte_radio_module_terminal_number && msg_received.numBytesInMessage>0){
			    				// TODO: use method to shift right/left e byte array
			    				// save the message ID
			    				int id=msg_received.message[0];
			    				// shift the data bytes
			    				for (int i=0;i<msg_received.numBytesInMessage-1;i++){
			    					msg_received.message[i]=msg_received.message[i+1];
			    				}
			    				// set the correct number of data bytes in the message
			    				msg_received.numBytesInMessage-=1;
			    				// if the id is waiting for the ack, this should be the acknowledgment
			    				if (idQueue.is_waiting_ack_id(id)){
				    				// signal this message as acknowledge
				    				idQueue.signal_acknowledge(id,msg_received);
			    				}
			    				else{
			    					radio_msg=radio_msg.Copy(msg_received);
			    					queue_msgs.put(radio_msg);
			    				}
			    			}
			    			else{
		    					radio_msg=radio_msg.Copy(msg_received);
				    			// put a newly created object containing the number of bytes received (and with same length BTW)
				    			queue_msgs.put(radio_msg);
			    			}
							if (log_enable.is_enabled(enum_arnl_log_enable.receiving_thread)){
								Log.i(localTag,"message queued");
							}
			    		}catch (InterruptedException e) {
			                e.printStackTrace();
			            }						
					}
					// clear the message
					else{
						if (log_enable.is_enabled(enum_arnl_log_enable.receiving_thread)){
							Log.i(localTag,"error reciving the message: "+this.receive_return_code.toString());
						}
						this.msg_receiving.numBytesInMessage=0;
					}
					// back to initial status
					this.status_msg_receiving=enum_rnl_status.wait_stx;
					break;
			}
		}
	}
	private enum EnumNORreply_status{
		waiting,
		receivedOK,
		error
	}
	private class CheckNORreply_element{
		final int TIMEOUT_WAITING_NOR_ACK_MS=500;
		int timeoutMs;
		long elapsed_time;
		long base_time;
		Asac_radio_network_layer_message msg;
		EnumNORreply_status status;
		private boolean bMessageQueuedOK;
		private boolean bNeedsExplicitReply;
		private void build_element(Asac_radio_network_layer_message msg, int timeoutMs, boolean bQueuedOK, boolean bNeedsExplicitReply){
			this.timeoutMs=timeoutMs;
			this.setbMessageQueuedOK(false);
			this.msg=msg.Copy(msg);
			this.base_time=System.currentTimeMillis();
			this.elapsed_time=0;
			status=EnumNORreply_status.waiting;
			this.bNeedsExplicitReply=bNeedsExplicitReply;
		}
		public CheckNORreply_element (Asac_radio_network_layer_message msg, boolean bNeedsExplicitReply){
			this.build_element(msg,TIMEOUT_WAITING_NOR_ACK_MS,false,bNeedsExplicitReply);
		}
		public boolean isbMessageQueuedOK() {
			return bMessageQueuedOK;
		}
		public void setbMessageQueuedOK(boolean bMessageQueuedOK) {
			this.bMessageQueuedOK = bMessageQueuedOK;
		}
		public boolean checkTimeout(){
			long act_time=System.currentTimeMillis();
			if (act_time<this.base_time){
				this.base_time=System.currentTimeMillis();
			}
			else{
				this.elapsed_time=act_time-base_time;
			}
			if (this.elapsed_time>this.timeoutMs){
				return true;
			}
			return false;
		}
	}
	public class HandleNORpackets implements Runnable{
		final String localTag="checkNORreply";
		// maximum number of elements in the NOR reply queue
		final int MAXIMUM_NOR_PACKET_LIST_SIZE=32;
		private LinkedBlockingDeque<CheckNORreply_element> NORPacketList;
		private LinkedBlockingDeque<CheckNORreply_element> ackNORPacketList;
		private boolean bStopMe;
		private boolean isRunningMe;
		private boolean b_NOR_message_queue_full;
		private enum_radio_work_mode radio_work_mode;
		private enum_radio_strength radio_strength;
		private int radio_status_update_rx_numof;
		private Asac_radio_module_commands armc;
		@SuppressWarnings("unused")
		private int numOverflow;
		@SuppressWarnings("unused")
		private int numOverflowReply;
		public Thread thread = null;
		
		public boolean isB_NOR_message_queue_full() {
			return this.b_NOR_message_queue_full;
		}

		public void setB_NOR_message_queue_full(boolean b_NOR_message_queue_full) {
			this.b_NOR_message_queue_full = b_NOR_message_queue_full;
		}

		public enum_radio_work_mode getRadio_work_mode() {
			return radio_work_mode;
		}

		public void setRadio_work_mode(enum_short_radio_work_mode radio_work_mode) {
			this.radio_work_mode = this.armc.translate_Work_mode(radio_work_mode);
		}

		public enum_radio_strength getRadio_strength() {
			return radio_strength;
		}

		public void setRadio_strength(enum_short_radio_strength radio_strength) {
			this.radio_strength = this.armc.translate_Radio_strength(radio_strength);
		}

		public int getRadio_status_update_rx_numof() {
			return radio_status_update_rx_numof;
		}

		public void setRadio_status_update_rx_numof(int radio_status_update_rx_numof) {
			this.radio_status_update_rx_numof = radio_status_update_rx_numof;
		}
		private  Context m_the_context;
		public HandleNORpackets(Asac_radio_module_commands armc, Context m_the_context){
			this.m_the_context = m_the_context;
			NORPacketList=new LinkedBlockingDeque<CheckNORreply_element> ();
			 ackNORPacketList=new LinkedBlockingDeque<CheckNORreply_element> ();
			 setbStopMe(false);
			 setRunningMe(false);
			 this.armc=armc;
			 this.numOverflow=0;
		}
		public boolean stop(int timeoutMs){
			setbStopMe(true);
			long base_time=System.currentTimeMillis();
			// wait until thread stops
			while(isRunningMe()){
				try{
					Thread.sleep(10);
				}
				catch(InterruptedException e){};
				long act_time=System.currentTimeMillis();
				if (act_time<base_time || (act_time-base_time>timeoutMs)){
					break;
				}
			}
			if (thread != null)
			{
				if (isRunningMe()){
					thread.interrupt();
					thread = null;
				}
			}
			setbStopMe(false);
			return isRunningMe();
		}


		public void queue_element(Asac_radio_network_layer_message msg, boolean bNeedsExplicitReply){
			CheckNORreply_element e= new CheckNORreply_element(msg,bNeedsExplicitReply);
			NORPacketList.add(e);
		}
		public boolean hasMoreThan(int numElements){
			if (NORPacketList.size()>numElements){
				return true;
			}
			return false;
		}
		private void CheckNORreplyToSentMessage(CheckNORreply_element element, int timeSlotMs){
			// wait at least 1 millisecond to give breath to the system
			try {
				Thread.sleep(1);
			} catch (InterruptedException e) {}

			// wait the ACK from the NOR !
			// our request was numbered, so we wait until the NOR acknowledges the request
			if (!idQueue.b_wait_acknowledge(timeSlotMs, element.msg.message_identifier)){
				if (element.checkTimeout()){
					if (log_enable.is_enabled(enum_arnl_log_enable.post_messages)){
						Log.i(localTag,"timeout receiving NOR ack");
					}					
					element.status=EnumNORreply_status.error;
				}
			}
			else{
				element.status=EnumNORreply_status.receivedOK;
				final char short_reply_status_valid_header='$';
				// if the reply comes, check if the message was received OK by the NOR
				// the message is in the form: <$><message queued OK><message queue full><radio mode><rssi index>
				Asac_radio_network_layer_message m_ack=idQueue.get_message(element.msg.message_identifier);
				if (m_ack.message.length<5){
					if (log_enable.is_enabled(enum_arnl_log_enable.post_messages)){
						Log.e(localTag,"invalid NOR ack reply length:"+m_ack.message.length);
					}					
				}
				else if (m_ack.message[0]!=short_reply_status_valid_header){
					if (log_enable.is_enabled(enum_arnl_log_enable.post_messages)){
						Log.e(localTag,"invalid NOR ack reply message");
					}					
				}
				else{
					if (log_enable.is_enabled(enum_arnl_log_enable.post_messages)){
						Log.i(localTag,"reply from NOR just arrived");
					}
					
					this.setRadio_status_update_rx_numof(this.getRadio_status_update_rx_numof() + 1);
					// checking if message queued OK
					if (m_ack.message[1]=='1'){
						element.bMessageQueuedOK=true;
					}
					// checking if NOR queue is full
					boolean b_NOR_message_queue_full=true;
					if (m_ack.message[2]=='0'){
						b_NOR_message_queue_full=false;
					}
					this.setB_NOR_message_queue_full(b_NOR_message_queue_full);
					// check radio mode
					enum_short_radio_work_mode new_work_mode=enum_short_radio_work_mode.U;
					for (enum_short_radio_work_mode e:enum_short_radio_work_mode.values()){
						if (e.toString().charAt(0)==m_ack.message[3]){
							new_work_mode=e;
							break;
						}
					}				
					this.setRadio_work_mode(new_work_mode);
					
					// check radio strength
					enum_short_radio_strength new_radio_strength=enum_short_radio_strength.U;
					for (enum_short_radio_strength e:enum_short_radio_strength.values()){
						if (e.toString().charAt(0)==m_ack.message[4]){
							new_radio_strength=e;
							break;
						}
					}	
					this.setRadio_strength(new_radio_strength);
					{
						Intent i = new Intent();
						i.setAction("ASAC.action.SC_RADIO_INTENSITY_CHANGED");
						i.putExtra(AsacIntents.RADIO_INTENSITY_CHANGED_STRENGTH_INT, new_radio_strength.ordinal());
						i.putExtra(AsacIntents.RADIO_INTENSITY_CHANGED_WORK_MODE_INT, new_work_mode.ordinal());
						i.putExtra(AsacIntents.RADIO_INTENSITY_CHANGED_STRENGTH, new_radio_strength.toString());
						i.putExtra(AsacIntents.RADIO_INTENSITY_CHANGED_WORK_MODE, new_work_mode.toString());
						//i.putExtra(RADIO_INTENSITY_CHANGED_STRENGTH, radio_strength);
						this.m_the_context.sendBroadcast(i);
					}
					
				}
			}
		}

		synchronized boolean bWaitMessageAcknowldegedFromNOR(int messageId, int timeoutMs){
			boolean bMessageSent;
			boolean bMessageSentOK;
			boolean bTimeout;
			bMessageSent=false;
			bMessageSentOK=false;
			bTimeout=false;
			long base_time=System.currentTimeMillis();
			// wait until thread stops
			while(!bMessageSent && !bTimeout){
				try {
					wait(10,0);
				} catch (InterruptedException e) {
				}
				for (CheckNORreply_element e:ackNORPacketList){
					if (e.msg.message_identifier==messageId){
						bMessageSent=true;
						if (e.isbMessageQueuedOK()){
							bMessageSentOK=true;
						}
						ackNORPacketList.remove(e);
						break;
					}
				}
				long act_time=System.currentTimeMillis();
				if (act_time<base_time || (act_time-base_time>timeoutMs)){
					bTimeout=true;
				}
			}		
			return bMessageSentOK;
		}
		synchronized private void addExplicitReplyQueue(CheckNORreply_element element){
			if (ackNORPacketList.size()>MAXIMUM_NOR_PACKET_LIST_SIZE){
				if (log_enable.is_enabled(enum_arnl_log_enable.post_messages)){
					Log.e(localTag,"NOR reply queue overflow, cleaning UP the oldest message");
				}			
				try{
					CheckNORreply_element element_to_delete=ackNORPacketList.getFirst();
					ackNORPacketList.remove(element_to_delete);
				}catch (NoSuchElementException e){
					if (log_enable.is_enabled(enum_arnl_log_enable.post_messages)){
						Log.e(localTag,"error cleaning NOR reply queue from overflow");
					}
				}
				this.numOverflowReply++;
			}
			ackNORPacketList.add(element);
			notifyAll();
		}
		private void remove_waiting(CheckNORreply_element element){
			if (element.bNeedsExplicitReply){
				addExplicitReplyQueue(element);
			}
			idQueue.setfree(element.msg.message_identifier);
			NORPacketList.remove(element);
		}

		private void build_req_short_radio_status(Asac_radio_module_commands armc, Asac_radio_network_layer_message msg_req_radio_status){
			String s_msg_req_radio_status=null;
			s_msg_req_radio_status=armc.build_get_short_radio_status();
			msg_req_radio_status.message=s_msg_req_radio_status.getBytes();
			msg_req_radio_status.numBytesInMessage=s_msg_req_radio_status.length();
		}
		long last_time_radio_status_query = 0;
		public void reset_request_radio_status()
		{
			long now = System.currentTimeMillis();
			last_time_radio_status_query = now + delay_after_radio_busy_before_status_request_ms;
		}
		public boolean b_req_radio_status_enabled = true;
		public void run(){
			setRunningMe(true);
			try{
				LinkedList<CheckNORreply_element> repliesToRemove=new LinkedList<CheckNORreply_element> ();		
				while(!isbStopMe()){
					if (NORPacketList.isEmpty()){
						try{
							Thread.sleep(10);
						}catch(InterruptedException e){};
					}
					else {
						if (NORPacketList.size() > MAXIMUM_NOR_PACKET_LIST_SIZE) {
							if (log_enable.is_enabled(enum_arnl_log_enable.post_messages)) {
								Log.e(localTag, "NOR queue overflow, cleaning UP the oldest message");
							}
							try {
								CheckNORreply_element element = NORPacketList.getFirst();
								remove_waiting(element);
							} catch (NoSuchElementException e) {
								if (log_enable.is_enabled(enum_arnl_log_enable.post_messages)) {
									Log.e(localTag, "error cleaning NOR queue from overflow");
								}
							}
							this.numOverflow++;
						}
						repliesToRemove.clear();
						for (CheckNORreply_element element : NORPacketList) {
							if (isbStopMe()) {
								break;
							}
							CheckNORreplyToSentMessage(element, 1);
							if (element.status != EnumNORreply_status.waiting) {
								repliesToRemove.add(element);
							}
						}
						for (CheckNORreply_element reply : repliesToRemove) {
							remove_waiting(reply);
						}
					}
					long now = System.currentTimeMillis();
					// if the status request s enabled
					if (this.b_req_radio_status_enabled)
					{
						// and enough time has past and the post is not locked
						if (now > last_time_radio_status_query &&  now - last_time_radio_status_query > delay_between_radio_status_updates_ms)
						{
							// if I find the post locked, I 'll retry after some ms
							if (asac_radioRF_power.b_off_or_about_to_go_off() || !access_post.tryLock())
							{
								last_time_radio_status_query = now + delay_after_radio_busy_before_status_request_ms;
							}
							else
							{
								try {
									last_time_radio_status_query = now;
									Asac_radio_network_layer_message msg_req_radio_status= new Asac_radio_network_layer_message();
									// send a status request to keep info about radio alive
									// build the short radio status query message
									build_req_short_radio_status(armc, msg_req_radio_status);
									Asac_radio_network_layer_message msg_out=new Asac_radio_network_layer_message();
									// build the message
									build_message(enum_rnl_message_destination_type.message_destination_radio_module, msg_req_radio_status, msg_out);
									asac_radio_data_link_layer.put_message(msg_out);
									this.queue_element(msg_out, false);
								}catch(RuntimeException e){
									throw new RuntimeException("unable to query radio status",e);
								}
								catch(Exception e){
									throw new RuntimeException("unexpected exception",e);
								}
								finally {
									access_post.unlock();

								}
							}
						}
					}
				}
			}
			finally{
				setRunningMe(false);
			}
		}
		public boolean isRunningMe() {
			return isRunningMe;
		}
		public void setRunningMe(boolean isRunningMe) {
			this.isRunningMe = isRunningMe;
		}
		public boolean isbStopMe() {
			return bStopMe;
		}
		public void setbStopMe(boolean bStopMe) {
			this.bStopMe = bStopMe;
		}
	}
	
	
	
	
	/**
	 * 
	 * @param msg the message to post to the NOR
	 * @return OK if the message was acknowledged from the NOR
	 * @throws RuntimeException
	 */
	public EnumRnlReturnCode post(Asac_radio_network_layer_message msg, boolean bWaitMessageSent) throws RuntimeException{
		EnumRnlReturnCode return_code;
		final int TIMEOUT_WAITING_NOR_ACK_MS=1000;
		return_code=EnumRnlReturnCode.ok;
		final int MAX_TRY_POST_MESSAGE=3;
		final int DELAY_TRY_POST_MESSAGE_MS=10;
		final String localTag="post";
		boolean b_message_queued_ok=false;
		access_post.lock();
		try{
			for (int itry_post=0;itry_post<MAX_TRY_POST_MESSAGE;itry_post++){
				handleNORpackets.reset_request_radio_status();
				if (return_code!=EnumRnlReturnCode.ok){
					break;
				}
				if (b_message_queued_ok){
					break;
				}
				if (log_enable.is_enabled(enum_arnl_log_enable.post_messages)){
					Log.i(localTag,"putting message in NOR Queue");
				}
				// put the message in queue
				try{
					asacLEDs.pulseBlueOn();
					idQueue.v_mark_waiting_acknowledge(msg.message_identifier);
					asac_radio_data_link_layer.put_message(msg);
				}catch(RuntimeException e){
					throw new RuntimeException("error doing put of a post message",e);
				}
				boolean bWaitForTheReply=true;
				// I wait for the reply if the caller want so or if my queue seems a little bit too fulfilled
				if (!bWaitMessageSent&&!this.handleNORpackets.hasMoreThan(8)){
					bWaitForTheReply=false;
				}
				// put the element in NOR replies queue, with the flag stating if we should wait for the reply or not
				this.handleNORpackets.queue_element(msg, bWaitForTheReply);
				// if no wait and queue is not too full
				// TODO change this!!!
				if (!bWaitForTheReply){
					b_message_queued_ok=true;
					if (log_enable.is_enabled(enum_arnl_log_enable.post_messages)){
						Log.i(localTag,"message post without waiting for NOR reply");
					}				
				}
				else{
					if (!this.handleNORpackets.bWaitMessageAcknowldegedFromNOR(msg.message_identifier,TIMEOUT_WAITING_NOR_ACK_MS)){
						try {
							if (log_enable.is_enabled(enum_arnl_log_enable.post_messages)){
								Log.i(localTag,"NOR message not queued, retrying");
							}					
							Thread.sleep(DELAY_TRY_POST_MESSAGE_MS);
						} catch (InterruptedException e1) {
						}
					}
					else{
						b_message_queued_ok=true;
						if (log_enable.is_enabled(enum_arnl_log_enable.post_messages)){
							Log.i(localTag,"message was queued OK");
						}
						
					}
				}
			}
			if (!b_message_queued_ok){
				if (return_code==EnumRnlReturnCode.ok){
					if (log_enable.is_enabled(enum_arnl_log_enable.post_messages)){
						Log.e(localTag,"NOR message not queued, abandon after max retry");
					}					
					return_code=EnumRnlReturnCode.unable_queue_NOR_msg;
				}
			}
		}
		finally{
			access_post.unlock();
		}
		return return_code;
	}
	/**
	 * post a message: the message is first built, then sent to the serial port without waiting for the message reply 
	 * 
	 * @param destination the message destination 
	 * @param msg_in the message to post
	 * @return the return code of the post routine
	 * @throws RuntimeException
	 */
	public EnumRnlReturnCode postMessage(enum_rnl_message_destination_type destination, Asac_radio_network_layer_message msg_in) throws RuntimeException{
		Asac_radio_network_layer_message msg_out=new Asac_radio_network_layer_message();
		// build the message
		EnumRnlReturnCode return_code=build_message(destination,msg_in,msg_out);
		// build was OK?
		if (return_code==EnumRnlReturnCode.ok){
			// if the message was built OK, put the message in queue
			try{
				return_code=this.post(msg_out,true);
			}catch(RuntimeException e){
				throw new RuntimeException("error doing put of a post message",e);
			}
		}
		return return_code;
	}
	/**
	 *  post a message: the message is first built, then sent to the serial port without waiting for the message reply 
	 *  the body is NOT stuffed; this routine is useful when the bytes to send are ALREADY stuffed
	 * @param destination
	 * @param msg_in
	 * @return
	 * @throws RuntimeException
	 */
	public EnumRnlReturnCode post_message_no_body_stuff_generic(enum_rnl_message_destination_type destination, Asac_radio_network_layer_message msg_in, boolean bWaitMessageSent) throws RuntimeException{
		Asac_radio_network_layer_message msg_out=new Asac_radio_network_layer_message();
		// build the message
		EnumRnlReturnCode return_code=build_message(destination,msg_in,msg_out,false);
		// build was OK?
		if (return_code==EnumRnlReturnCode.ok){
			try{
				return_code=this.post(msg_out, bWaitMessageSent);
			}catch(RuntimeException e){
				throw new RuntimeException("error doing put of a post stuffless message",e);
			}
		}
		return return_code;
	}
	/**
	 *  post a message: the message is first built, then sent to the serial port without waiting for the message reply 
	 *  the body is NOT stuffed; this routine is useful when the bytes to send are ALREADY stuffed
	 * @param destination
	 * @param msg_in
	 * @return
	 * @throws RuntimeException
	 */
	public EnumRnlReturnCode postMessageNoStuff(enum_rnl_message_destination_type destination, Asac_radio_network_layer_message msg_in) throws RuntimeException{
		return post_message_no_body_stuff_generic(destination, msg_in, true);
	}
	
	/**
	 *  post a message: the message is first built, then sent to the serial port without waiting for the message reply 
	 *  the body is NOT stuffed; this routine is useful when the bytes to send are ALREADY stuffed
	 * @param destination
	 * @param msg_in
	 * @return
	 * @throws RuntimeException
	 */
	public EnumRnlReturnCode postMessageNoStuffNoWait(enum_rnl_message_destination_type destination, Asac_radio_network_layer_message msg_in) throws RuntimeException{
		return post_message_no_body_stuff_generic(destination, msg_in, false);
	}
	
	
	/**
	 * 
	 * @param destination the destination of the message
	 * @param msg_in the input message coming from the network layer
	 * @param msg_out the output message, 
	 * @return
	 */
	public EnumRnlReturnCode build_message(enum_rnl_message_destination_type destination, Asac_radio_network_layer_message msg_in, Asac_radio_network_layer_message msg_out){
		return build_message(destination,msg_in, msg_out, true);
	}
	/**
	 * wait until all of the data in the output buffer have been sent
	 */
	public void sync(){
		asac_radio_data_link_layer.sync();
	}
	
	/**
	 * build a message so it is ready for transmission
	 * @param destination where to send the message
	 * @param msg_in the message containing the body
	 * @param msg_out the destination message where to copy the newly built message
	 * @param b_stuff_body states if the body should be stuffed or if it is already stuffed
	 * @return
	 */
	public EnumRnlReturnCode build_message(enum_rnl_message_destination_type destination, Asac_radio_network_layer_message msg_in, Asac_radio_network_layer_message msg_out, boolean b_stuff_body){
		// the maximum message epilog is: eom, dle+crc_lo, dle+crc_hi, etx
		final int MAX_ROOM_MSG_EPILOG=6;
        byte byte_current;
        EnumRnlReturnCode b_message_built_ok=EnumRnlReturnCode.ok;
        int idx_byte_in_msg=0;
        int idx_out_excessive=msg_out.message.length;
        profile.log("build init");
		// HITS IS REALLY NEEDED BECAUSE ELSE WE CAN HAVE THE THREAD REQUESTING THE RADIO STATUS AND THE NORMAL MESSAGES THREAD
		// USING THE GLOBAL VARIABLE msg_crc at the very same time!!!
		access_build_message.lock();
		try {
			// no valid message
			msg_out.b_message_valid = false;
			// crc code init
			msg_crc.init_crc();
			// is there room for the very beginning of the message stx+[dle]+terminal number?
			if (idx_byte_in_msg + 3 >= idx_out_excessive) {
				// if error code isn't set, update statistics and error code
				if (b_message_built_ok.equals(EnumRnlReturnCode.ok)) {
					b_message_built_ok = EnumRnlReturnCode.too_long_body;
					tx_stats.i_num_msg_tx_retcodes[b_message_built_ok.ordinal()]++;
				}
				return b_message_built_ok;
			}
			// add STX
			msg_out.message[idx_byte_in_msg++] = byte_SERIAL_STX;
			// transmit terminal number
			{
				// set the destination address
				if (destination == enum_rnl_message_destination_type.message_destination_radio_module) {
					byte_current = m_byte_radio_module_terminal_number;
				} else {
					byte_current = m_byte_destination_terminal_number;
				}
				// stuff the byte, if needed, and update CRC
				if (is_byte_to_stuff(byte_current)) {
					msg_out.message[idx_byte_in_msg++] = byte_SERIAL_DLE;
					//no serial dle in the CRC!!!!
					//msg_crc.update_crc(byte_SERIAL_DLE);
					msg_out.message[idx_byte_in_msg++] = (byte) (byte_current | 0x80);
					msg_crc.update_crc((byte_current | 0x80));
				}
				// add without stuff, update CRC
				else {
					msg_out.message[idx_byte_in_msg++] = byte_current;
					msg_crc.update_crc(byte_current);
				}
			}
			{
				// get a new identifier
				msg_out.message_identifier = idQueue.i_get_new_id();
				Log.i("build message", "getting new id " + msg_out.message_identifier);
				byte_current = (byte) msg_out.message_identifier;
				msg_out.message[idx_byte_in_msg++] = byte_current;
				msg_crc.update_crc(byte_current);
			}

			profile.log("build body");
			// body transmit, with or without stuffing
			if (!b_stuff_body) {
				// no stuffing needed
				if (idx_byte_in_msg + MAX_ROOM_MSG_EPILOG + msg_in.numBytesInMessage >= idx_out_excessive) {
					b_message_built_ok = EnumRnlReturnCode.too_long_body;
					tx_stats.i_num_msg_tx_retcodes[b_message_built_ok.ordinal()]++;

				} else {
					// copy the bytes without stuffing, update the CRC
					for (int body_idx = 0; body_idx < msg_in.numBytesInMessage; body_idx++) {
						byte_current = msg_in.message[body_idx];
						msg_out.message[idx_byte_in_msg++] = byte_current;
						msg_crc.update_crc(byte_current);
					}
				}
			} else {
				// stuffing needed
				for (int body_idx = 0; body_idx < msg_in.numBytesInMessage; body_idx++) {
					byte_current = msg_in.message[body_idx];
					// is there room for the next byte?
					if (idx_byte_in_msg + MAX_ROOM_MSG_EPILOG + 2 >= idx_out_excessive) {
						b_message_built_ok = EnumRnlReturnCode.too_long_body;
						tx_stats.i_num_msg_tx_retcodes[b_message_built_ok.ordinal()]++;
						break;
					}
					// stuff the byte if needed, update the CRC too
					if (is_byte_to_stuff(byte_current)) {
						msg_out.message[idx_byte_in_msg++] = byte_SERIAL_DLE;
						//no serial dle in the CRC!!!!
						//msg_crc.update_crc(byte_SERIAL_DLE);
						msg_out.message[idx_byte_in_msg++] = (byte) (byte_current | 0x80);
						msg_crc.update_crc((byte_current | 0x80));
					} else {
						msg_out.message[idx_byte_in_msg++] = byte_current;
						msg_crc.update_crc(byte_current);
					}
				}
			}
			profile.log("build eom");

			// is there room for the end of the message???
			if (idx_byte_in_msg + MAX_ROOM_MSG_EPILOG >= idx_out_excessive) {
				// update statistics
				if (b_message_built_ok.equals(EnumRnlReturnCode.ok)) {
					b_message_built_ok = EnumRnlReturnCode.too_long_body;
					tx_stats.i_num_msg_tx_retcodes[b_message_built_ok.ordinal()]++;
				}
				return b_message_built_ok;
			}
			// serial_eom put
			msg_out.message[idx_byte_in_msg++] = byte_SERIAL_EOM;
			// now, put CRC, first high byte and then low byte. with byte stuff but without crc update
			{
				int crc = msg_crc.get_crc();
				// get CRC high
				byte_current = (byte) (crc >> 8);
				if (is_byte_to_stuff(byte_current)) {
					msg_out.message[idx_byte_in_msg++] = byte_SERIAL_DLE;
					msg_out.message[idx_byte_in_msg++] = (byte) (byte_current | 0x80);
				} else {
					msg_out.message[idx_byte_in_msg++] = byte_current;
				}
				// get CRC low
				byte_current = (byte) (crc & 0xff);
				if (is_byte_to_stuff(byte_current)) {
					msg_out.message[idx_byte_in_msg++] = byte_SERIAL_DLE;
					msg_out.message[idx_byte_in_msg++] = (byte) (byte_current | 0x80);
				} else {
					msg_out.message[idx_byte_in_msg++] = byte_current;
				}
			}
			// send ETX
			msg_out.message[idx_byte_in_msg++] = byte_SERIAL_ETX;
			// save the message length
			msg_out.numBytesInMessage = idx_byte_in_msg;
			// the message is valid
			msg_out.b_message_valid = true;
			// update statistics
			if (b_message_built_ok.equals(EnumRnlReturnCode.ok)) {
				tx_stats.i_num_msg_tx_retcodes[b_message_built_ok.ordinal()]++;
			}
		}
		finally
		{
			access_build_message.unlock();
		}

		profile.log("build ends");
		
		return b_message_built_ok;
		
	}


}

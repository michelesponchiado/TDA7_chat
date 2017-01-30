package asac_radio_transport_layer;


// statistics of the transport layer
public class TransportLayerStats{
	public int numRepeatTx;
	public int lastTripTimeMs;
	public int maxTripTimeMs;
	public int minTripTimeMs;
	public int averageTripTimeMs;
	public int numMsgTx;
	public int numMessagesRx;
	public int i_num_msg_err;
	public int i_num_msg_ok;
	public int numTooLongBodyTx;
	public int numUnknownErrorTx;
	public int numMessagesTooLongRx;
	public int i_num_try_post_message;
	public int repeatPostErrors;
	public ReceiveFileStats receive_file;
	public TransmitFileStats transmit_file;

	public int i_num_sendfile_unk_replies; // number of unknown (! (ACK or NAK)) replies received while sending a file
	public int numNorReplyError;
	public int numNorQueueTimeoutError;
	public int numNorQueueStalledError;
	public SendfileStats sendfileStats;
	
	public TransportLayerStats(){
		receive_file=new ReceiveFileStats();
		sendfileStats=new SendfileStats();
		transmit_file = new TransmitFileStats();
		clear_stats();
	}
	
	public void clear_stats(){
		numRepeatTx=0;
		lastTripTimeMs=0;
		maxTripTimeMs=0;
		minTripTimeMs=0;
		averageTripTimeMs=0;
		numMsgTx=0;
		numMessagesRx=0;
		i_num_msg_err=0;
		i_num_msg_ok=0;
		this.sendfileStats.clear();
	}
	public void clear_sendfile_stats(){
		this.sendfileStats.clear();
	}
	public int getI_sendfile_percentage() {
		return sendfileStats.getI_sendfile_percentage();
	}


	public long getL_sendfile_offset() {
		return sendfileStats.getL_sendfile_offset();
	}

	public void setSendfileOffset(long l_sendfile_offset) {
		this.sendfileStats.setL_sendfile_offset(l_sendfile_offset);
	}
	public void setSendfilePercentage(int value){
		this.sendfileStats.setI_sendfile_percentage(value);
	}
	public void sentBytes(long nb){
		this.sendfileStats.addBytes(nb);
	}
	public double speedBytesPerSecond(){
		return this.sendfileStats.getSpeedBytesPerSecond();
	}
}

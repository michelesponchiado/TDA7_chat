package asac_radio_transport_layer;



// the parameters of the Transport class
public class TransportParams{
	/**
	 * the parameters inherent to the transmit and receive procedure
	 * @author root
	 *
	 */
	public class TransmitAndReceiveParams{
		private final int DEFAULT_TIMEOUT_MS=800;
		private final int DEFAULT_MAX_NUM_REPET=8;
		private final int DEFAULT_PAUSE_AFTER_REPET_MS=20;
		private final int DEFAULT_PURGE_MAX_DURATION_MS=1000;
		private final int DEFAULT_PURGE_SILENCE_MS=100;
		
		// the timeout for the whole transmit and receive procedure
		private int timeoutMs;
		// the maximum number of repetitions for a message sent
		private int maxRepetitionsNumOf;
		// a small pause after the infamous "REPET" message
		private int pauseAfterRepetMs;
		// the maximum duration of the purge procedure
		private int purgeSilenceDurationMs;
		// max delay to wait for incoming messages
		private int purgeMaxDurationMs;
		
		public TransmitAndReceiveParams(){
			this.reset_default();
		}
		public void reset_default(){
			setTimeoutMs(DEFAULT_TIMEOUT_MS);
			setMaxRepetitionsNumOf(DEFAULT_MAX_NUM_REPET);
			setPauseAfterRepetMs(DEFAULT_PAUSE_AFTER_REPET_MS);
			setPurgeSilenceDurationMs(DEFAULT_PURGE_SILENCE_MS);
			setPurgeMaxDurationMs(DEFAULT_PURGE_MAX_DURATION_MS);
		}
		public int getTimeoutMs() {
			return timeoutMs;
		}
		public void setTimeoutMs(int timeoutMs) {
			this.timeoutMs = timeoutMs;
		}
		public int getMaxRepetitionsNumOf() {
			return maxRepetitionsNumOf;
		}
		public void setMaxRepetitionsNumOf(int maxRepetitionsNumOf) {
			this.maxRepetitionsNumOf = maxRepetitionsNumOf;
		}
		public int getPauseAfterRepetMs() {
			return pauseAfterRepetMs;
		}
		public void setPauseAfterRepetMs(int pauseAfterRepetMs) {
			this.pauseAfterRepetMs = pauseAfterRepetMs;
		}
		public int getPurgeSilenceDurationMs() {
			return purgeSilenceDurationMs;
		}
		public void setPurgeSilenceDurationMs(int purgeSilenceDurationMs) {
			this.purgeSilenceDurationMs = purgeSilenceDurationMs;
		}
		public int getPurgeMaxDurationMs() {
			return purgeMaxDurationMs;
		}
		public void setPurgeMaxDurationMs(int purgeMaxDurationMs) {
			this.purgeMaxDurationMs = purgeMaxDurationMs;
		}
	}
	/**
	 * the parameters inherent to the sendfile procedure
	 * @author root
	 *
	 */
	public class SendfileParams{
		private final int DEFAULT_MAX_PACKET_WAITING=6; //=8;
		private final int DEFAULT_MAX_NUM_REPET=16;
		private final int DEFAULT_PURGE_SILENCE_MS=200;
		private final int DEFAULT_PURGE_MAX_DURATION_MS=400;
		private final int DEFAULT_PAUSE_BETWEEN_BURST_MS=1;
		private final long REPLY_TIMEOUT_DEFAULT_MS=1000;
		private final long REPLY_TIMEOUT_MIN_MS=100;
		private final long REPLY_TIMEOUT_MAX_MS=10000;
		
		
		private int maxPacketWaiting;
		private int maxRepetitionsNumOf;
		private int purgeSilenceDurationMs;
		private int purgeMaxDurationMs;
		private int pauseBetweenBurstMs;
		private long timeoutMs;
		
		
		public void reset_default(){
			setMaxPacketWaiting(DEFAULT_MAX_PACKET_WAITING);
			setMaxRepetitionsNumOf(DEFAULT_MAX_NUM_REPET);
			setPurgeSilenceDurationMs(DEFAULT_PURGE_SILENCE_MS);
			setPurgeMaxDurationMs(DEFAULT_PURGE_MAX_DURATION_MS);
			setPauseBetweenBurstMs(DEFAULT_PAUSE_BETWEEN_BURST_MS);
			setTimeoutMs(REPLY_TIMEOUT_DEFAULT_MS);
			
		}
		public SendfileParams(){
			this.reset_default();
		}

		public int getMaxPacketWaiting() {
			return maxPacketWaiting;
		}

		public void setMaxPacketWaiting(int maxPacketWaiting) {
			this.maxPacketWaiting = maxPacketWaiting;
		}

		public int getMaxRepetitionsNumOf() {
			return maxRepetitionsNumOf;
		}

		public void setMaxRepetitionsNumOf(int maxRepetitionsNumOf) {
			this.maxRepetitionsNumOf = maxRepetitionsNumOf;
		}

		public int getPurgeSilenceDurationMs() {
			return purgeSilenceDurationMs;
		}

		public void setPurgeSilenceDurationMs(int purgeSilenceDurationMs) {
			this.purgeSilenceDurationMs = purgeSilenceDurationMs;
		}

		public int getPurgeMaxDurationMs() {
			return purgeMaxDurationMs;
		}

		public void setPurgeMaxDurationMs(int purgeMaxDurationMs) {
			this.purgeMaxDurationMs = purgeMaxDurationMs;
		}

		public int getPauseBetweenBurstMs() {
			return pauseBetweenBurstMs;
		}

		public void setPauseBetweenBurstMs(int pauseBetweenBurstMs) {
			this.pauseBetweenBurstMs = pauseBetweenBurstMs;
		}
		/**
		 * 
		 * @return the actual send file receiving packet timeout
		 */
		public long getTimeoutMs() {
			return this.timeoutMs;
		}

		/**
		 * 
		 * @param l_timeout_send_file_reply_ms the needed send file receiving packet timeout 
		 */
		public void setTimeoutMs(long l_timeout_send_file_reply_ms) {
			if ((l_timeout_send_file_reply_ms>=REPLY_TIMEOUT_MIN_MS) && (l_timeout_send_file_reply_ms<=REPLY_TIMEOUT_MAX_MS)){
				this.timeoutMs = l_timeout_send_file_reply_ms;
			}
		}
		
	}
	public class ReceivefileParams{
		private final int DEFAULT_TIMEOUT_RX_FILE_PACKET=1000;
		// max number of REPET frames to send
		private final int DEFAULT_MAX_KILLFILE_REPETITIONS=10;
		// max number of consecutive synchronization retries after an out of sequence reply from the PC
		private final int DEFAULT_MAX_NUM_CONSECUTIVE_TRY_RESYNC_SEQUENCE=16;
		// maximum number of consecutive timeouts waiting for a packet from the PC
		private final int DEFAULT_MAX_CONSECUTIVE_TIMEOUTS=8;
		private final long DEFAULT_PURGE_SILENCE_MS=200;
		private final long DEFAULT_PURGE_MAX_DURATION_MS=5000;
		private final int DEFAULT_MAX_TRY_SEND_NACK=8;
		private final int DEFAULT_SLEEP_BETWEEN_NACK_MS=200;
		private final int DEFAULT_MAX_LOOP_FILESIZE=10;
		
		
		private int timeoutRxFilePacket;
		private int maxKillfileRepetitions;
		private int maxConsecutiveTryResync;
		private int maxConsecutiveTimeouts;
		private long purgeSilenceDurationMs;
		private long purgeMaxDurationMs;
		private int numTrySendNack;
		private int pauseBetweenNackMs;
		private int maxLoopFileSizeInfo;
		
		public void reset_default(){
			setTimeoutRxFilePacket(DEFAULT_TIMEOUT_RX_FILE_PACKET);
			setMaxKillfileRepetitions(DEFAULT_MAX_KILLFILE_REPETITIONS);
			setMaxConsecutiveTryResync(DEFAULT_MAX_NUM_CONSECUTIVE_TRY_RESYNC_SEQUENCE);
			setMaxConsecutiveTimeouts(DEFAULT_MAX_CONSECUTIVE_TIMEOUTS);
			setPurgeSilenceDurationMs(DEFAULT_PURGE_SILENCE_MS);
			setPurgeMaxDurationMs(DEFAULT_PURGE_MAX_DURATION_MS);
			setNumTrySendNack(DEFAULT_MAX_TRY_SEND_NACK);
			setPauseBetweenNackMs(DEFAULT_SLEEP_BETWEEN_NACK_MS);
			setMaxLoopFileSizeInfo(DEFAULT_MAX_LOOP_FILESIZE);
		}
		public ReceivefileParams(){
			this.reset_default();
		}
		public int getTimeoutRxFilePacket() {
			return timeoutRxFilePacket;
		}
		public void setTimeoutRxFilePacket(int timeoutRxFilePacket) {
			this.timeoutRxFilePacket = timeoutRxFilePacket;
		}
		public int getMaxKillfileRepetitions() {
			return maxKillfileRepetitions;
		}
		public void setMaxKillfileRepetitions(int maxKillfileRepetitions) {
			this.maxKillfileRepetitions = maxKillfileRepetitions;
		}
		public int getMaxConsecutiveTryResync() {
			return maxConsecutiveTryResync;
		}
		public void setMaxConsecutiveTryResync(int maxConsecutiveTryResync) {
			this.maxConsecutiveTryResync = maxConsecutiveTryResync;
		}
		public int getMaxConsecutiveTimeouts() {
			return maxConsecutiveTimeouts;
		}
		public void setMaxConsecutiveTimeouts(int maxConsecutiveTimeouts) {
			this.maxConsecutiveTimeouts = maxConsecutiveTimeouts;
		}
		public long getPurgeSilenceDurationMs() {
			return purgeSilenceDurationMs;
		}
		public void setPurgeSilenceDurationMs(long purgeSilenceDurationMs) {
			this.purgeSilenceDurationMs = purgeSilenceDurationMs;
		}
		public long getPurgeMaxDurationMs() {
			return purgeMaxDurationMs;
		}
		public void setPurgeMaxDurationMs(long purgeMaxDurationMs) {
			this.purgeMaxDurationMs = purgeMaxDurationMs;
		}
		public int getNumTrySendNack() {
			return numTrySendNack;
		}
		public void setNumTrySendNack(int numTrySendNack) {
			this.numTrySendNack = numTrySendNack;
		}
		public int getPauseBetweenNackMs() {
			return pauseBetweenNackMs;
		}
		public void setPauseBetweenNackMs(int pauseBetweenNackMs) {
			this.pauseBetweenNackMs = pauseBetweenNackMs;
		}
		public int getMaxLoopFileSizeInfo() {
			return maxLoopFileSizeInfo;
		}
		public void setMaxLoopFileSizeInfo(int maxLoopFileSizeInfo) {
			this.maxLoopFileSizeInfo = maxLoopFileSizeInfo;
		}
	}
	
	
	public TransmitAndReceiveParams transmitAndReceive;
	public SendfileParams sendfile;
	public ReceivefileParams receivefile;

	public TransportParams(){
		transmitAndReceive= new TransmitAndReceiveParams();
		sendfile = new SendfileParams();
		receivefile= new ReceivefileParams();
	}
	
	
}

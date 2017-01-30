package asac_radio_transport_layer;

import java.util.LinkedList;

public class SendfileStats {
	final int MAX_SIZE_QUEUE=64;
	private int percentage;
	private long offset;
	private int speedDepthMs;
	private class ByteChunk{
		long numBytesWritten;
		long time;
		public ByteChunk(long nb){
			this.numBytesWritten=nb;
			this.time=System.currentTimeMillis();
		}
	}
	private LinkedList<ByteChunk> chunkWritten;
	public long getL_sendfile_offset() {
		return offset;
	}
	public void setL_sendfile_offset(long l_sendfile_offset) {
		this.offset = l_sendfile_offset;
	}
	public int getI_sendfile_percentage() {
		return percentage;
	}
	public void setI_sendfile_percentage(int i_sendfile_percentage) {
		this.percentage = i_sendfile_percentage;
	}
	public SendfileStats(){
		setSpeedDepthMs(2000);
		chunkWritten=new LinkedList<ByteChunk> ();
		this.clear();
	}
	private void purgeQueue(){
		LinkedList<ByteChunk> removeList=new LinkedList<ByteChunk>();
		long actualTime=System.currentTimeMillis();
		for (ByteChunk b:this.chunkWritten){
			if (actualTime-b.time>getSpeedDepthMs()){
				removeList.add(b);
			}
		}
		for (ByteChunk bRemove:removeList){
			this.chunkWritten.remove(bRemove);
		}
		int iElemToRemove=this.chunkWritten.size()-MAX_SIZE_QUEUE;
		if (iElemToRemove>0){
			for (int i=0;i<iElemToRemove;i++){
				if (!this.chunkWritten.isEmpty()){
					this.chunkWritten.removeFirst();
				}
			}
		}
	}
	public double getSpeedBytesPerSecond(){
		// purge the Queue from the old elements
		purgeQueue();
		long numBytes=0;
		long act_time=System.currentTimeMillis();
		long old_time=act_time;
		for (ByteChunk b:this.chunkWritten){
			numBytes+=b.numBytesWritten;
			if (old_time>b.time){
				old_time=b.time;
			}
		}
		double timeElpasedMs=act_time-old_time;
		if (timeElpasedMs<=0){
			timeElpasedMs=10.0;
		}
		double speedBytesS=numBytes*1000/timeElpasedMs;
		return speedBytesS;
	}
	public void addBytes(long nb){
		if (this.chunkWritten.size()>MAX_SIZE_QUEUE){
			this.purgeQueue();
		}
		this.chunkWritten.add(new ByteChunk(nb));
	}
	public void clear(){
		chunkWritten.clear();
		percentage=0;
		offset=0;
	}
	public int getSpeedDepthMs() {
		return speedDepthMs;
	}
	public void setSpeedDepthMs(int speedDepthMs) {
		this.speedDepthMs = speedDepthMs;
	}
}

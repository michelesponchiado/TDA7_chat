package asac_radio_transport_layer;



public class AckNackMessages {
	public enum EnumAckNackRx{
		ack_received,
		nak_received,
		unknown
		
	};
	/**
	 * a simple class o handle ACK and NAK frames
	 * @author root
	 *
	 */
	class Tda7_acknak_frame{
		private final byte[] the_frame;
		EnumAckNackRx the_type;
		Tda7_acknak_frame(byte [] b, EnumAckNackRx acknak){
			// create the buffer
			the_frame=new byte[b.length];
			// copy the buffer data
			System.arraycopy(b, 0,the_frame,0,b.length);
			//the_frame=b;
			the_type=acknak;
		}
	}
	/**
	 * my weird way to store ACK and NAK frames
	 */
	private final byte [] ACK_MESSAGE;
	private final byte [] NAK_MESSAGE;
	private final Tda7_acknak_frame[] ACKNAK_MSGS;
	
	public AckNackMessages(){
		ACK_MESSAGE=new byte[]{'A','C','K'};
		NAK_MESSAGE=new byte[]{'N','A','K'};
		ACKNAK_MSGS=new Tda7_acknak_frame[]{
				new Tda7_acknak_frame(ACK_MESSAGE,EnumAckNackRx.ack_received),
				new Tda7_acknak_frame(NAK_MESSAGE,EnumAckNackRx.nak_received)
			};
	}
	byte [] getAckMessage(){
		return ACK_MESSAGE;
	}
	byte [] getNackMessage(){
		return NAK_MESSAGE;
	}
	
	public EnumAckNackRx check(byte [] b_array){
		EnumAckNackRx retcode=EnumAckNackRx.unknown;
		for (Tda7_acknak_frame m: ACKNAK_MSGS){
			boolean b_found=true;
			for (int i=0;i<m.the_frame.length; i++){
				if (m.the_frame[i]!=b_array[i]){ //radio_message_rx.msg.message[i]){
					b_found=false;
					break;
				}
			}
			if (b_found){
				retcode=m.the_type;
				break;
			}
		}
		return retcode;
	}

}

package asac_radio_network_layer_message;

//the class for the in/out messages
public class Asac_radio_network_layer_message{
	private final int ASAC_RADIO_NETWORK_LAYER_MESSAGE_SIZE=1024;
	public byte[] message;
	public int numBytesInMessage;
	public int i_from_terminal_number;
	public boolean b_message_valid;
	public int message_identifier;
	public void clear(){
		b_message_valid=false;
		i_from_terminal_number=0;
		numBytesInMessage=0;
	}
	public Asac_radio_network_layer_message(){
		message=new byte[ASAC_RADIO_NETWORK_LAYER_MESSAGE_SIZE];
		clear();
	}
	// creates a copy o source object:
	// only the min(ASAC_RADIO_NETWORK_LAYER_MESSAGE_SIZE, source.num_bytes_in_messsage) will be copied from source into message 
	public Asac_radio_network_layer_message Copy(Asac_radio_network_layer_message source){
		Asac_radio_network_layer_message n=new Asac_radio_network_layer_message();
		n.numBytesInMessage	=source.numBytesInMessage;
		n.i_from_terminal_number=source.i_from_terminal_number;
		n.b_message_valid		=source.b_message_valid;
		n.message_identifier=source.message_identifier;
		if (source.numBytesInMessage<n.message.length){
			System.arraycopy(source.message, 0, n.message, 0, source.numBytesInMessage);
		}
		else{
			System.arraycopy(source.message, 0, n.message, 0, n.message.length);
		}
		return n;
	}
	public Asac_radio_network_layer_message CopyWithoutMessage(Asac_radio_network_layer_message source){
		Asac_radio_network_layer_message n=new Asac_radio_network_layer_message();
		n.numBytesInMessage	=0;
		n.i_from_terminal_number=source.i_from_terminal_number;
		n.b_message_valid		=source.b_message_valid;
		return n;
	}
}

	
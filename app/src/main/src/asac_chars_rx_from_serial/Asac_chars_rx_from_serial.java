package asac_chars_rx_from_serial;
public class Asac_chars_rx_from_serial {
	public int num_bytes_in_buffer;	
	public byte[] the_bytes;
	public Asac_chars_rx_from_serial(int n_bytes_in_buffer, byte[] bytes){
		// explicit number of bytes into the buffer, 'cause I don't want to rely on length field
		num_bytes_in_buffer=n_bytes_in_buffer;
		// allocate the appropriate amount of bytes
		the_bytes= new byte[n_bytes_in_buffer];
		// copy the source buffer into the destination buffer, only the needed bytes
		System.arraycopy(
	            bytes, 0,
				the_bytes, 0,
				n_bytes_in_buffer
	        );

	}
}

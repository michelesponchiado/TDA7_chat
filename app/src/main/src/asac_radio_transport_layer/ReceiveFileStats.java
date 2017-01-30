package asac_radio_transport_layer;

/**
 * 
 * @author root
 *
 */
public class ReceiveFileStats{
	public int i_num_nack_sent;
	public int i_num_ack_sent;
	public int i_num_resync_sent;
	public int i_num_OK;
	public int i_num_err;
	public long last_file_bytes_RX;
	public long l_bytes_RX;
	public long l_file_size;
	public long start_time;
	public long end_time;
	public double last_transfer_rate_byte_s;
	/**
	 * clear all of the receive file statistics
	 */
	public void clearall(){
		i_num_nack_sent=0;
		i_num_ack_sent=0;
		i_num_OK=0;
		i_num_err=0;
		last_file_bytes_RX=0;
		l_bytes_RX=0;
		start_time=0;
		end_time=0;
		last_transfer_rate_byte_s=0;
		i_num_resync_sent=0;
		
	}
	/**
	 * constructor
	 */
	public ReceiveFileStats(){
		clearall();
	}
}
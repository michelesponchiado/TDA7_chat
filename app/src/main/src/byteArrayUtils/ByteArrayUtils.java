package byteArrayUtils;
public class ByteArrayUtils{
	public ByteArrayUtils(){
		
	}
	/**
	 * Look for the specified byte inside the byte buffer starting from an index
	 * @param buffer the byte buffer where to look for the byte
	 * @param the_byte the byte we need to look for
	 * @param i_start_index the index from where the search begins
	 * @param i_found_index the index where we found the byte, if found, else unchanged
	 * @return index >=0 of byte found, else -1
	 */
	public int lookByteInBuffer(byte [] buffer, byte the_byte, int i_start_index){
		boolean b_found_OK=false;
		int i_found_index=-1;
		if (i_start_index<buffer.length && i_start_index>=0){
			for (int i=i_start_index;i<buffer.length;i++){
				if (buffer[i]==the_byte){
					b_found_OK=true;
					i_found_index=i;
					break;
				}
			}
		}
		if (b_found_OK){
			return i_found_index;
		}
		return -1;
	}
	
	public boolean byteArrayBeginsWith(byte [] byte_array, int i_num_bytes_in_buffer, byte [] prefix){
		boolean b_retcode=true;
		if (i_num_bytes_in_buffer<prefix.length){
			b_retcode=false;
		}
		else{
			for (int i_check_msg_idx=0;i_check_msg_idx<prefix.length;i_check_msg_idx++){
				if (byte_array[i_check_msg_idx]!=prefix[i_check_msg_idx]){
					b_retcode=false;
					break;
				}
			}
		}
		return b_retcode;
		
	}
	
	public boolean byteArrayHasSequenceAtIndex(byte [] byte_array, int i_num_bytes_in_buffer, int index_start,byte [] prefix){
		boolean b_retcode=true;
		if (i_num_bytes_in_buffer<index_start+prefix.length){
			b_retcode=false;
		}
		else{
			for (int i_check_msg_idx=0;i_check_msg_idx<prefix.length;i_check_msg_idx++){
				if (byte_array[i_check_msg_idx+index_start]!=prefix[i_check_msg_idx]){
					b_retcode=false;
					break;
				}
			}
		}
		return b_retcode;
		
	}
	public long getLongFromByteBuffer(byte[] buffer, int i_start_index, final int i_max_index ){
		int index=i_start_index;
		long the_long;
		the_long=0;
		int i_num_loop=0;
		long sign=1;
		// convert from byte array to long...
		while (index<=i_max_index){
			char c;
			c=(char)buffer[index];
			if (i_num_loop==0 && c=='-'){
				sign=-1;
				index++;
			}
			else if (Character.isDigit(c)){
				the_long*=10;
				the_long+=Character.digit(c,10);
				index++;
			}
			else{
				break;
			}
			i_num_loop++;
		}	
		return the_long*sign;
	}
	
}

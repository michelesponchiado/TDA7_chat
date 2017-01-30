package asac_radio_transport_layer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;


// this is a class that handles a file buffer
/**
 * this class handles a file buffer, useful with the stuff routines of the library
 * @author root
 *
 */
public class FileByteBuffer{
	public enum enum_FileByteBuffer_status{
		ok,
		no_more_chars_sorry,
		
	}
	
	enum_FileByteBuffer_status status;
	final int byte_buffer_size=1024;
	byte [] buffer;
	int i_num_bytes_in_buffer;
	int idx_next_byte_in_buffer;
	long loffset_read_from_file;
	long lfile_size;
	RandomAccessFile file_input;
	boolean b_end_of_file_reached;
	long l_virtual_offset;
	
	// checks whether there are still characters available from the file
	public boolean finished(){
		if (status==enum_FileByteBuffer_status.no_more_chars_sorry){
			return true;
		}
		return false;
	}
	
	// refreshes the buffer status
	public void statusUpdate(){
		if (file_input==null){
			b_end_of_file_reached=true;
			status=enum_FileByteBuffer_status.no_more_chars_sorry;
		}
		else{
			if (loffset_read_from_file>=lfile_size){
				b_end_of_file_reached=true;
			}
			if (i_num_bytes_in_buffer<=0 && b_end_of_file_reached){
				status=enum_FileByteBuffer_status.no_more_chars_sorry;
			}
			else{
				status=enum_FileByteBuffer_status.ok;
			}
		}
	}
	
	/**
     * returns some bytes from file buffer, if available 
     * please note that the bytes are only copied if available, the buffer indexes are not updated
     * @param  the buffer where the available bytes should be copied
     * @return number of bytes successfully copied, possibly 0, max buf_dst.length
     *  
     */		
	public int takeSomeBytes(byte [] buf_dst){
		int i_num_bytes_copied;
		i_num_bytes_copied=0;
		if (i_num_bytes_in_buffer>0){
			if (buf_dst.length<i_num_bytes_in_buffer){
				i_num_bytes_copied=buf_dst.length;
			}
			else{
				i_num_bytes_copied=i_num_bytes_in_buffer;
			}
			System.arraycopy(buffer, idx_next_byte_in_buffer, buf_dst,0,i_num_bytes_copied);
		}
		statusUpdate();
		return i_num_bytes_copied;
	}
	
	/**
     * informs the class that some bytes have been used, so the buffer indexes are updated
     * @param  the number of bytes used and to discard
     * @return none
     *  
     */				
	public void setNumOfCharsUsed(int i_num_char_used){
		if (i_num_char_used>=i_num_bytes_in_buffer){
			i_num_bytes_in_buffer=0;
			idx_next_byte_in_buffer=0;
			l_virtual_offset+=i_num_bytes_in_buffer;
		}
		else{
			idx_next_byte_in_buffer+=i_num_char_used;
			i_num_bytes_in_buffer-=i_num_char_used;
			l_virtual_offset+=i_num_char_used;
		}
		statusUpdate();
	}
	/**
     * repositions the buffer at specified offset from the beginning of the file
     * @param  the offset from the beginning of the file
     * @return true if repositioning was done OK, else false
     *  
     */				
	public boolean rewind(long l_offset){
		boolean retcode=true;
		if (file_input==null){
			return false;
		}
		i_num_bytes_in_buffer=0;
		idx_next_byte_in_buffer=0;
		loffset_read_from_file=l_offset;
		l_virtual_offset=l_offset;		
		try {
			file_input.seek(l_offset);
		} catch (IOException e) {
			retcode=false;
		}
		b_end_of_file_reached=false;
		if (loffset_read_from_file>=lfile_size){
			b_end_of_file_reached=true;
		}			
		statusUpdate();
		return retcode;
	}
	/**
     * tries to refill the buffer up to the specified number of bytes
     * @param  the expected number of bytes in the buffer after the refill completes
     * @return true if refill was done OK, else false
     *  
     */				
	public boolean refillUpTo(int i_expected_number_of_bytes_in_buffer){
		boolean b_buffer_filledup_ok;
		b_buffer_filledup_ok=false;
		if (file_input==null){
			b_buffer_filledup_ok=false;
		}
		else if (b_end_of_file_reached){
			if (i_num_bytes_in_buffer<=0){
				b_buffer_filledup_ok=false;
			}
			else {
				b_buffer_filledup_ok=true;
			}
		}
		else if (i_num_bytes_in_buffer>=i_expected_number_of_bytes_in_buffer){
			b_buffer_filledup_ok=true;
		}
		else{
			int n_bytes_read;
			// no bytes read as of now
			n_bytes_read=0;
			// shift left buffer, if necessary
			if (idx_next_byte_in_buffer>0){
				System.arraycopy(buffer, idx_next_byte_in_buffer, buffer,0,i_num_bytes_in_buffer);
			}
			// restart from index 0
			idx_next_byte_in_buffer=0;
			// try reading some other bytes
			try{
				n_bytes_read=file_input.read(buffer, i_num_bytes_in_buffer, byte_buffer_size-i_num_bytes_in_buffer);
			}catch (IOException e){
				n_bytes_read=0;
			}
			// if some other bytes have been read, increase offset, increase number of bytes in buffer
			if (n_bytes_read>=0){
				i_num_bytes_in_buffer+=n_bytes_read;
				loffset_read_from_file+=n_bytes_read;
				b_buffer_filledup_ok=true;
			}
			else{
				b_buffer_filledup_ok=false;
			}
		}
		statusUpdate();
		return b_buffer_filledup_ok;
	}
	/**
     * returns the actual "virtual" offset inside the file
     * @return the actual "virtual" offset inside the file
     *  
     */				
	public long actualFileOffset(){
		return l_virtual_offset;
	}
	/**
     * returns the actual file read percentage
     * @return a value between 0 (inclusive) and 100 (inclusive) which is the actual  read file percentage
     *  
     */				
	public int actualReadPercentage(){
		int i_act_perc=100;
		if (lfile_size>0){
			i_act_perc=(int)( (l_virtual_offset*100)/lfile_size);
		}
		// limit between 0 and 100
		i_act_perc=Math.min(Math.max(i_act_perc, 0),100);
		return i_act_perc;
	}
	/**
	 * closes the buffer
	 */
	public void close(){
		if (file_input!=null){
			try {
				file_input.close();
			} catch (IOException e) {
			}
			file_input=null;
		}
		
	}
	/**
	 * 
	 * @return the length of the input file
	 */
	public long length(){
		return lfile_size;
	}
	/**
	 * the class constructor
	 * @param the file_name path
	 */
	public FileByteBuffer(String file_name){
		buffer=new byte[byte_buffer_size];
		i_num_bytes_in_buffer=0;
		idx_next_byte_in_buffer=0;
		loffset_read_from_file=0;
		lfile_size=0;
		file_input=null;
		b_end_of_file_reached=false;
		l_virtual_offset=0;
		// open file for input
		try {
			file_input = new RandomAccessFile(file_name, "r");
		} catch (FileNotFoundException e1) {
			file_input=null;
		}
		// get file length
		if (file_input!=null){
			try{
				lfile_size=file_input.length();
			}catch (IOException e){
				file_input=null;
			}	
		}
		statusUpdate();
	}
	
}

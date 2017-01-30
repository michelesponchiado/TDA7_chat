package Profile;

import java.util.ArrayList;

public class LogProfile{
	private final int i_size_profile_buffer=64;
	ArrayList<String> profilebuf =null;
	int idx_profilebuf;
	
	public LogProfile(){
		profilebuf=new ArrayList<String> (i_size_profile_buffer);
		for (int i=0;i<i_size_profile_buffer;i++){
			profilebuf.add("");
		}
		idx_profilebuf=0;
	}
	
	public void log(String s){
		
		if ( (idx_profilebuf>=i_size_profile_buffer) ||(idx_profilebuf<0)){
			idx_profilebuf=0;
		}
		profilebuf.set(idx_profilebuf,System.currentTimeMillis()+":"+s);
		if (++idx_profilebuf>=i_size_profile_buffer){
			idx_profilebuf=0;
		}
	}
}

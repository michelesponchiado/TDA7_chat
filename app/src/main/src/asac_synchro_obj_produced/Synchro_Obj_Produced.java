
package asac_synchro_obj_produced;
//a beautiful object to synchronize and check if new messages are available
	public class Synchro_Obj_Produced {
	    // Message sent from producer
	    // to consumer.
	    private volatile long l_num_object_produced=0;
    	// actual number of objects produced
    	private long l_base_num_produced;
    	public void begin(){
    		l_base_num_produced=l_num_object_produced;
    	}
    	public boolean new_object_already_available(){
    		if (l_base_num_produced!=l_num_object_produced){
    			return true;
    		}
    		return false;
    	}
	    // a new object produced, with a timeout in ms
	    public synchronized boolean wait_new_object_produced(long l_timeout_ms) {
	    	// check period {ms}
	    	long l_wait_time_ms=100;
	    	// no new object produced
	    	boolean b_new_object_produced=false;
	    	// check period
	    	if (l_wait_time_ms>l_timeout_ms){
	    		l_wait_time_ms=1;
	    	}
	    	// set actual time ms
	    	long l_base_time_ms=System.currentTimeMillis();
	        // Wait until a new object is available.
	        while (l_base_num_produced==l_num_object_produced) {
	        	// wait some ms
	            try {
	                wait(l_wait_time_ms);
	            } catch (InterruptedException e) {}
	            // new object produced? OK!
	            if (l_base_num_produced!=l_num_object_produced){
	            	b_new_object_produced=true;
	            	break;
	            }
	            // check actual time
	        	long l_actual_time_ms=System.currentTimeMillis();
	        	// too long wait???
	            if (l_actual_time_ms-l_base_time_ms>l_timeout_ms){
	            	break;
	            }
	        }
            if (l_base_num_produced!=l_num_object_produced){
            	b_new_object_produced=true;
            	l_base_num_produced=l_num_object_produced;
            }
	        // return true if a new object has been produced, else return false
	        return b_new_object_produced;
	    }
	    // signal that a new object has been produced
	    public synchronized void signal_new_object_produced() {
	        // Store message.
	        l_num_object_produced++;
	        // Notify consumer that status
	        // has changed.
	        notifyAll();
	    }
	}
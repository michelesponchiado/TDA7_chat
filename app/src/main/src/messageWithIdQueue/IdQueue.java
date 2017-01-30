package messageWithIdQueue;

import java.util.ArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import asac_radio_network_layer_message.Asac_radio_network_layer_message;

public class IdQueue {
	private ArrayList<IdQueueElement> queue;
	private int i_start_id;
	public int INVALID_ID=-1;
	private int i_next_to_use;
	private final Lock access_queue = new ReentrantLock(true);
	private long l_num_solve_no_more_ids;
	public int i_get_new_id(){
		int i_new_id;
		i_new_id=INVALID_ID;
		access_queue.lock();
		boolean b_new_index_found=false;
		int i_new_index_found=-1;
		try{
			for (int i=this.i_next_to_use;i<queue.size();i++){
				if (queue.get(i).b_is_free()){
					i_new_index_found=i;
					b_new_index_found=true;
					break;
				}
			}
			if (!b_new_index_found){
				for (int i=0;i<this.i_next_to_use && i<queue.size();i++){
					if (queue.get(i).b_is_free()){
						i_new_index_found=i;
						b_new_index_found=true;
						break;
					}
				}
			}
			if (!b_new_index_found){
				i_new_index_found=0;
				b_new_index_found=true;
				this.setL_num_solve_no_more_ids(this.getL_num_solve_no_more_ids()+1);
			}
			if (b_new_index_found){
				i_new_id=map_index_to_id(i_new_index_found);
				queue.get(i_new_index_found).setwaitingack();
				this.i_next_to_use=i_new_index_found+1;
				if (this.i_next_to_use>=queue.size()){
					this.i_next_to_use=0;
				}
			}
		}
		finally{
			access_queue.unlock();
		}
		return i_new_id;
	}
	
	private int map_index_to_id(int index){
		if ((index>=0)&&(index<queue.size())){
			return index+this.i_start_id;
		}
		return INVALID_ID;
	}
	private int new_index_to_id(int index){
		if (index>=0){
			return index+this.i_start_id;
		}
		return INVALID_ID;
	}
	private int unmap_id_to_index(int id){
		int index;
		index=id-this.i_start_id;
		if ((index>=0)&&(index<queue.size())){
			return index;
		}
		return INVALID_ID;
	}
	public IdQueue(int num_elements_in_queue, int i_start_id){
		queue=new ArrayList<IdQueueElement>(num_elements_in_queue);
		setL_num_solve_no_more_ids(0);
		this.i_start_id=i_start_id;
		this.i_next_to_use=0;
		for (int i=0;i<num_elements_in_queue;i++){
			queue.add(new IdQueueElement(new_index_to_id(i)));
		}
	}
	public boolean b_is_valid_id(int id){
		if ((id>=this.i_start_id)&&(id<this.i_start_id+queue.size())){
			return true;
		}
		return false;
	}

	public void v_mark_waiting_acknowledge(int id){
		int index;
		index = unmap_id_to_index(id);
		if (index==INVALID_ID){
			return;
		}
		queue.get(index).setwaitingack();
	}

	public void setfree( int id) {
    	int index;
    	index = unmap_id_to_index(id);
    	if (index==INVALID_ID){
    		return ;
    	}
    	queue.get(index).setfree();
	}
    // a new object produced, with a timeout in ms
	synchronized public boolean b_wait_acknowledge(long l_timeout_ms, int id) {
    	int index;
    	index = unmap_id_to_index(id);
    	if (index==INVALID_ID){
    		return false;
    	}
    	// no new object produced
    	boolean b_retcode=false;
    	// set actual time ms
    	long l_base_time_ms=System.currentTimeMillis();
        // Wait until a new object is available.
        while (queue.get(index).b_is_waiting_for_acknowledge()) {
        	// wait some milliseconds
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {}
            // new object produced? OK!
            if (queue.get(index).b_is_acknowledged()){
            	b_retcode=true;
            	break;
            }
            // check actual time
        	long l_actual_time_ms=System.currentTimeMillis();
        	// too long wait???
            if ((l_actual_time_ms-l_base_time_ms>l_timeout_ms) || (l_base_time_ms>l_actual_time_ms)){
            	break;
            }
        }
        // new object produced? OK!
        if (queue.get(index).b_is_acknowledged()){
        	b_retcode=true;
        }
        // return true if a new object has been produced, else return false
        return b_retcode;
    }
    // signal that a new object has been produced
    synchronized public void signal_acknowledge(int id,Asac_radio_network_layer_message message) {
    	int index;
    	index = unmap_id_to_index(id);
    	if (index==INVALID_ID){
    		return;
    	}	  
    	// set the message
    	queue.get(index).copymessage(message);
    	queue.get(index).setacknowledged();
    	//notifyAll();
    }
    // signal that a new object has been produced
    public boolean is_waiting_ack_id(int id) {
    	int index;
    	index = unmap_id_to_index(id);
    	if (index==INVALID_ID){
    		return false;
    	}	  
    	// set the message
    	if (queue.get(index).b_is_waiting_for_acknowledge()){
    		return true;
    	}
    	return false;
    }
    // signal that a new object has been produced
    public Asac_radio_network_layer_message get_message(int id) {
    	int index;
    	index = unmap_id_to_index(id);
    	if (index==INVALID_ID){
    		return null;
    	}	  
    	// get the message
    	Asac_radio_network_layer_message message=new Asac_radio_network_layer_message();
    	message=message.Copy(queue.get(index).message);
    	return message;
    }

		public long getL_num_solve_no_more_ids() {
			return l_num_solve_no_more_ids;
		}

		public void setL_num_solve_no_more_ids(long l_num_solve_no_more_ids) {
			this.l_num_solve_no_more_ids = l_num_solve_no_more_ids;
		}
	}
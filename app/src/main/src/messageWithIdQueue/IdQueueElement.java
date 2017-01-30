package messageWithIdQueue;

import asac_radio_network_layer_message.Asac_radio_network_layer_message;
enum enum_idQueue_element_status{
	idle,
	waiting_acknowledge,
	acknowledged,
	free
}


public class IdQueueElement {
	int identifier;
	Asac_radio_network_layer_message message;
	enum_idQueue_element_status status;
	public IdQueueElement(int identifier){
		message=new Asac_radio_network_layer_message();
		status=enum_idQueue_element_status.idle;
		this.identifier=identifier;
	}
	public boolean b_is_waiting_for_acknowledge(){
		if (this.status==enum_idQueue_element_status.waiting_acknowledge){
			return true;
		}
		return false;
	}
	public boolean b_is_acknowledged(){
		if (this.status==enum_idQueue_element_status.acknowledged){
			return true;
		}
		return false;
	}
	public boolean b_is_free(){
		if (   (this.status==enum_idQueue_element_status.free)
			|| (this.status==enum_idQueue_element_status.idle)
		    ){
			return true;
		}
		return false;
	}
	public void copymessage(Asac_radio_network_layer_message message){
		this.message=this.message.Copy(message);
	}
	public void setfree(){
		this.status=enum_idQueue_element_status.free;
	}
	public void setacknowledged(){
		this.status=enum_idQueue_element_status.acknowledged;
	}
	public void setwaitingack(){
		this.status=enum_idQueue_element_status.waiting_acknowledge;
	}
}

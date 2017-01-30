package asac_radio_transport_layer;

import asac_radio_network_layer.AsacRadioNetworkLayer;
import asac_radio_network_layer.AsacRadioNetworkLayer.enum_arnl_log_enable;

    public class LogTransportLayer{
    	public enum EnumTransportLogEnable{
     		transmit_and_receive,
     		receive_file,
     		send_file,
     		network_layer_receiving_thread,
     		data_link_layer_receiving_thread,
     		data_link_layer_write_bytes_to_serial_port,
     		network_layer_post_messages,
     		
     	};
    	private boolean[] b_enable= new boolean[EnumTransportLogEnable.values().length];
    	AsacRadioNetworkLayer arnl;
    	
    	public LogTransportLayer(AsacRadioNetworkLayer arnl){
    		this.arnl=arnl;
    		disable_all();
    	}
    	public void disable_all(){
    		for (EnumTransportLogEnable e:EnumTransportLogEnable.values()){
    			enable_disable(e, false);
    		}
    	}
    	public boolean isEnabled(EnumTransportLogEnable e){
    		return b_enable[e.ordinal()];
    	}
    	public void enable_disable(EnumTransportLogEnable e, boolean value){
    		b_enable[e.ordinal()]=value;
    		switch(e){
	    		case network_layer_receiving_thread:
	    		{
	    			this.arnl.log_enable.enable_disable(enum_arnl_log_enable.receiving_thread, value);
	    		}
	    		case data_link_layer_receiving_thread:
	    		{
	    			this.arnl.log_enable.enable_disable(enum_arnl_log_enable.ardl_receiving_thread, value);
	    			break;
	    		}
	    		case data_link_layer_write_bytes_to_serial_port:
	    		{
	    			this.arnl.log_enable.enable_disable(enum_arnl_log_enable.ardl_write_bytes_to_serial_port, value);
	    			break;
	    		}
	    		case network_layer_post_messages:
	    		{
	    			this.arnl.log_enable.enable_disable(enum_arnl_log_enable.post_messages, value);
	    			break;
	    		}	    		
	    		default:
	    		{
	    			break;
	    		}
    		}
    	}
    	public void enable(EnumTransportLogEnable e){
    		enable_disable(e,true);
    	}
    	public void disable(EnumTransportLogEnable e){
    		enable_disable(e,false);
    	}
    }

package asac_radio_module_commands;
//import java.io.IOException;
import java.util.*;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
//import android.util.Log;
import asac_radio_network_layer.AsacRadioNetworkLayer.EnumRnlReturnCode;


public class Asac_radio_module_commands {
	
	// MODE= 
	//	radio mode, can be one of the following
	//   OFF
	//   SEARCH
	//   WORK
	//   UNKNOWN
	// TXQUEUE=
	//   tells if radio tx queue is empty or not, can be one of the following
	//   EMPTY-->empty
	//   NORMAL-->not empty, can be also full
	//	 UNKNOWN
	// STRENGTH=
	//   radio signal strength qualitative indicator,  can be one of the following
	//	 OFF
	//	 LOW
	//	 GOOD
	//	 EXCELLENT
	//	 UNKNOWN
	// RSSI=
	//	 rssi value as integer, 0 if radio is not in work node, a good value is e.g. 400
	public enum enum_radio_work_mode{
		off,
		search,
		work,
		unknown
	};
	public enum enum_short_radio_work_mode{
		F,
		S,
		W,
		U
	};
	public enum enum_radio_txqueue_status{
		empty,
		normal,
		unknown
	};
	public enum enum_radio_strength{
		off,
		low,
		good,
		excellent,
		unknown
	};
	public enum enum_short_radio_strength{
		F,
		L,
		G,
		E,
		U
	};
	private enum_radio_work_mode work_mode;
	private enum_radio_txqueue_status txqueue_status;
	private enum_radio_strength radio_strength;
	private int rssi;
	private Context m_the_context;
	
	public Asac_radio_module_commands(Context m_the_context){
		this.m_the_context = m_the_context;
		setWork_mode(enum_radio_work_mode.off);
		setTxqueue_status(enum_radio_txqueue_status.unknown);
		setRadio_strength(enum_radio_strength.unknown);
		setRssi(0);
		this.firmwareVersion="??";
		this.firmwareDate="??";
		this.firmwareTime="??";
	}

	public enum enum_radio_modes{
		enum_radio_mode_RF_MODE_CMD_IDLE, 		//=0,  
		enum_radio_mode_RF_MODE_CMD_RXFIX,		// 1
		enum_radio_mode_RF_MODE_CMD_TXFIX,		// 2
		enum_radio_mode_RF_MODE_CMD_TXFIX_MOD,	// 3

		enum_radio_mode_RF_MODE_CMD_TXPACK,		// 4
		enum_radio_mode_RF_MODE_CMD_RXPACKTX,	// 5
		enum_radio_mode_RF_MODE_CMD_TXPACKRX,	// 6

		enum_radio_mode_RF_MODE_CMD_MODULE,		// 10
		enum_radio_mode_RF_MODE_CMD_NOR,		// 11
		enum_radio_mode_RF_MODE_CMD_TYPHMOD,	// 12
		
		enum_radio_mode_RF_MODE_CMD_TDA7,	// 13 // the tda7 mode

	}
	
	public enum enum_module_reply_type{
		enum_radio_module_reply_none,
		enum_radio_module_reply_module_parameters, 	 
		enum_radio_module_reply_module_info, 	 
		enum_radio_module_reply_module_test, 	 
		enum_radio_module_reply_module_reset,
		enum_radio_module_reply_radio_status,
		enum_radio_module_reply_short_radio_status,
		enum_radio_module_reply_radio_firmware_version,
		enum_radio_module_reply_set_internal_params_default,
		enum_radio_module_reply_set_channel_default,
		enum_radio_module_reply_get_protect_code,
		enum_radio_module_reply_set_protect_code,

	}	
	public int[]  i_radio_modes= new int[EnumRnlReturnCode.values().length];
	
	//@Override
    public void onCreate(Bundle savedInstanceState) {
        //super.onCreate(savedInstanceState);
    	// initialize array of radio modes
    	i_radio_modes[enum_radio_modes.enum_radio_mode_RF_MODE_CMD_IDLE.ordinal()]=0;
    	i_radio_modes[enum_radio_modes.enum_radio_mode_RF_MODE_CMD_RXFIX.ordinal()]=1;
    	i_radio_modes[enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TXFIX.ordinal()]=2;
    	i_radio_modes[enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TXFIX_MOD.ordinal()]=3;

    	i_radio_modes[enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TXPACK.ordinal()]=4;
    	i_radio_modes[enum_radio_modes.enum_radio_mode_RF_MODE_CMD_RXPACKTX.ordinal()]=5;
    	i_radio_modes[enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TXPACKRX.ordinal()]=6;
    	
    	i_radio_modes[enum_radio_modes.enum_radio_mode_RF_MODE_CMD_MODULE.ordinal()]=10;
    	i_radio_modes[enum_radio_modes.enum_radio_mode_RF_MODE_CMD_NOR.ordinal()]=11;
    	i_radio_modes[enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TYPHMOD.ordinal()]=12;
    	
    	i_radio_modes[enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TDA7.ordinal()]=13;
    }
    /*
    private void v_assign_radio_mode(int i_mode){
    	switch(i_mode){
			case 0:
				parameters.module_mode=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_IDLE;
				break;
			case 1:
				parameters.module_mode=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_RXFIX;
				break;
			case 2:
				parameters.module_mode=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TXFIX;
				break;
			case 3:
				parameters.module_mode=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TXFIX_MOD;
				break;
			case 4:
				parameters.module_mode=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TXPACK;
				break;
			case 5:
				parameters.module_mode=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_RXPACKTX;
				break;
			case 6:
				parameters.module_mode=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TXPACKRX;
				break;
			case 10:
				parameters.module_mode=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_MODULE;
				break;
			case 11:
				parameters.module_mode=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_NOR;
				break;
			case 12:
				parameters.module_mode=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TYPHMOD;
				break;
			case 13:
				parameters.module_mode=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TDA7;
				break;
    	}
    }
    */
	private void v_assign_radio_crypt(int i_new_crypt){
		if ((i_new_crypt>=1)&&(i_new_crypt<=16)){
			parameters.i_crypt_code=i_new_crypt;
		}
	}
	public int read_radio_crypt(){
		return parameters.i_crypt_code;
	}
    private void v_assign_terminal_number(int i_new_terminal_number){
		if ((i_new_terminal_number>=1)&&(i_new_terminal_number<=100)){
			parameters.i_terminal_number=i_new_terminal_number;
		}
	}
    
    
 // parameters: 
    // * running mode
	// * cryptographic code
	// * terminal (device) number
	public class Asac_radio_module_parameters{
		private enum_radio_modes module_mode=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TDA7;
		private int i_crypt_code=1;
		private int i_terminal_number=1;
	}
	Asac_radio_module_parameters parameters = new Asac_radio_module_parameters();    
	private int i_reply_timeout=0;
	// how many reply should we wait for the correct reply from radio?
	private final int timeout_reply_numof_default=1;
    
	List<String> expected_responses = new ArrayList<String>();
	private final String req_module_parameters="AT+ASACMOD=?";
	private final String reply_module_parameters="+ASACMOD";
	private final String req_module_get_protect_code = "AT+ASACGETPROTECT?";
	private final String reply_module_get_protect_code = "AT+ASACGETPROTECT=";
	private final String req_module_set_protect_code = "AT+ASACSETPROTECT=";
	private final String reply_module_set_protect_code = "AT+ASACSETPROTECT=";
	private final String req_module_test="AT+ASAC";
	private final String reply_module_test="+ASAC:OK";
	private final String set_module_parameters="AT+ASACMOD=";
	private final String req_module_info="AT+ASACI?";
	private final String reply_module_info="+ASACI?";
	private final String req_module_parameters_save="AT+ASACSAVE";
	private final String reply_module_parameters_save="+ASACSAVE: Saved!";
	private final String req_module_reset="\u001BR"; // esc R--> module reset, esc is written as unicode \u001B
	private String string_module_info=new String();
	private final String req_module_radio_status="AT+RADIOSTATUS?";
	private final String reply_module_radio_status="+RADIOSTATUS?";
	private final String req_module_radio_firmware_version="AT+FWVERSION?";
	private final String reply_module_radio_firmware_version="+FWVERSION=";
	private final String req_module_short_radio_status="$";
	//DEFAULT SETTING FOR THE INTERNAL RADIO PARAMETERS: Coef=-26,Pwr=0,Ramp=0,SizePr=31,Squeelch=0,Dev=120,BW=0
	private final String req_module_radio_set_internal_params_at_default="AT+ASACRADIO=-26,0,0,31,0,120,0";
	private final String reply_module_radio_set_internal_params_at_default="+ASACRADIO:";
	private final String req_module_radio_set_channel_at_default="AT+ASACCHANNEL=1";
	private final String reply_module_radio_set_channel_at_default="AT+ASACCHANNEL:";
	private final String reply_module_short_radio_status="$";
	private String firmwareVersion;
	private String firmwareDate;
	private String firmwareTime;
	// esempio di query e reply
	// 	query: 
	// 		AT+RADIOSTATUS?
	// 	reply: 
	// 		RADIOSTATUS? MODE=WORK,TXQUEUE=EMPTY,STRENGTH=EXCELLENT,RSSI=400
	// 
	// descrizione dei vari parametri:
	// MODE= 
	//	radio mode, can be one of the following
	//   OFF
	//   SEARCH
	//   WORK
	//   UNKNOWN
	// TXQUEUE=
	//   tells if radio tx queue is empty or not, can be one of the following
	//   EMPTY-->empty
	//   NORMAL-->not empty, can be also full
	//	 UNKNOWN
	// STRENGTH=
	//   radio signal strength qualitative indicator,  can be one of the following
	//	 OFF
	//	 LOW
	//	 GOOD
	//	 EXCELLENT
	//	 UNKNOWN
	// RSSI=
	//	 rssi value as integer, 0 if radio is not in work node, a good value is e.g. 400

	// builds the get module parameters message
	public String build_get_module_parameters(){
		String req=req_module_parameters;
		expected_responses.add(reply_module_parameters);
		i_reply_timeout+=timeout_reply_numof_default;
		return req;
	}
	// builds the get crypt code
	public String build_get_module_protect_code(){
		String req= req_module_get_protect_code;
		expected_responses.add(reply_module_get_protect_code);
		i_reply_timeout+=timeout_reply_numof_default;
		return req;
	}
	// builds the set crypt code
	public String build_set_module_protect_code(){
		String req= req_module_set_protect_code;
		expected_responses.add(reply_module_set_protect_code);
		i_reply_timeout+=timeout_reply_numof_default;
		return req;
	}
	// builds the get module parameters message
	public String build_get_radio_status(boolean b_enable_short_status_reply){
		String req=req_module_radio_status;
		if (b_enable_short_status_reply){
			req+='1';
		}
		else{
			req+='0';
		}
		expected_responses.add(reply_module_radio_status);
		i_reply_timeout+=timeout_reply_numof_default;
		return req;
	}

	public String build_get_radio_firmware_version(){
		String req=req_module_radio_firmware_version;
		expected_responses.add(reply_module_radio_firmware_version);
		i_reply_timeout+=timeout_reply_numof_default;
		return req;
	}
	public String build_set_radio_internal_params_at_default()
	{
		String req=req_module_radio_set_internal_params_at_default;
		expected_responses.add(reply_module_radio_set_internal_params_at_default);
		i_reply_timeout+=timeout_reply_numof_default;
		return req;
	}
	public String build_set_radio_channel_at_default()
	{

		String req=req_module_radio_set_channel_at_default;
		expected_responses.add(reply_module_radio_set_channel_at_default);
		i_reply_timeout+=timeout_reply_numof_default;
		return req;
	}

	// builds the get module parameters message
	public String build_get_short_radio_status(){
		String req=req_module_short_radio_status;
		if (!expected_responses.contains(reply_module_short_radio_status)){
			expected_responses.add(reply_module_short_radio_status);
		}
		i_reply_timeout+=timeout_reply_numof_default;
		return req;
	}
	// builds the get module parameters message
	public void force_expect_radio_status(){
		if (!expected_responses.contains(reply_module_radio_status)){
			expected_responses.add(reply_module_radio_status);
		}
	}
	// builds the get module parameters message
	public void force_expect_short_radio_status(){
		if (!expected_responses.contains(reply_module_short_radio_status)){
			expected_responses.add(reply_module_short_radio_status);
		}
	}
	// builds the test module message
	public String build_module_test(){
		String req=req_module_test;
		expected_responses.add(reply_module_test);
		i_reply_timeout+=timeout_reply_numof_default;
		return req;
	}
	// example: "AT+ASACMOD=12,5,1,0" (mode, crypt code, terminal number, free unused parameter)
	// set the module parameters
	public String build_set_module_parameters(final Asac_radio_module_parameters radio_module_parameters_in){
		String req=
				 set_module_parameters
				+i_radio_modes[radio_module_parameters_in.module_mode.ordinal()]
				+","
				+radio_module_parameters_in.i_crypt_code
				+","
				+radio_module_parameters_in.i_terminal_number
				+","
				+"0";//last parameter by now is unused...
		expected_responses.add(reply_module_parameters);
		i_reply_timeout+=timeout_reply_numof_default;
		return req;
	}
	public void get_module_parameters(Asac_radio_module_parameters asac_radio_module_parameters_out){
		asac_radio_module_parameters_out=parameters;
	}
	// builds the get module info message
	public String build_get_module_info(){
		String req=req_module_info;
		// clear the module info actual string
		string_module_info="";
		expected_responses.add(reply_module_info);
		i_reply_timeout+=timeout_reply_numof_default;
		return req;
	}
	
	// builds the message to save the module parameters into the eeprom
	public String build_save_module_parameters(){
		String req=req_module_parameters_save;
		expected_responses.add(reply_module_parameters_save);
		i_reply_timeout+=timeout_reply_numof_default;
		return req;
	}
	
	// returns last module info read from the module
	public String get_module_info(){
		return string_module_info;
	}
	
	// reset the module
	public String build_reset_module(){
		String req=req_module_reset;
		return req;
	}
	private void setFirmwareVersion(String s){
		this.firmwareVersion=s;
		
	}
	private void setFirmwareDate(String s){
		this.firmwareDate=s;
		
	}
	public void setFirmwareTime(String s){
		this.firmwareTime=s;
	}
	public String getFirmwareVersion(){
		return this.firmwareVersion;
		
	}
	public String getFirmwareDate(){
		return this.firmwareDate;
		
	}
	public String  getFirmwareTime(){
		return this.firmwareTime;
	}
	
	
	private class List_modes{
		private final int the_length=8;
		public String name[]=new String[the_length];
		public enum_radio_modes mode[]=new enum_radio_modes[the_length];
		public int length(){
			return the_length;
		}
		public List_modes(){ 
				name[0]="NOR ";
				mode[0]=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_NOR;
				
				name[1]="TYPHMOD";
				mode[1]=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TYPHMOD;
				
				name[2]="MODULE";
				mode[2]=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_MODULE;
				
				name[3]="TX PACK";
				mode[3]=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TXPACKRX;
				
				name[4]="TX=";
				mode[4]=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TXFIX;
				
				name[5]="RX";
				mode[5]=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_RXFIX;
				
				name[6]="IDLE MODE";
				mode[6]=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_IDLE;
				
				name[7]="TDA7";
				mode[7]=enum_radio_modes.enum_radio_mode_RF_MODE_CMD_TDA7;
		}		
	}
	private List_modes list_modes=new List_modes();
	private boolean b_message_queue_NOR_is_full;
	

	/*
    if (strncmp(cmd,"AT+ASACI?",9)==0){
        sprintf(risp,"+ASACI? -CHANNEL:(Ch=%u, Mod=%u); -MODE:(Mode=%u, Code=%u, Nt=%u); -RADIO:(Coef=%i, Pwr=%u, Ramp=%u, SizePr=%u, Squelch=%u Dev=%u, BW=%u)"
                    ,RFPK.ChannelRF ,RFPK.FhMode        ,RFPK.Mode ,RFPK.Protect ,RFPK.TermNr,   RadioBase.OffsetCalPll
                                                                                                ,RadioBase.PowerPa  
                                                                                                ,RadioBase.PowerRaise
                                                                                                ,RadioBase.SzPreamb
                                                                                                ,RadioBase.RssiBase 
                                                                                                ,RadioBase.Deviaz
                                                                                                ,RadioBase.BandWidth
                                                                                                 );
            return;
}
 sprintf(risp,"+ASACMOD:TYPHMOD - Code=%u, Nt=%u, Fs=%u",RFPK.Protect
                                                                          ,RFPK.TermNr
                                                                          ,Nor.FunzSoftware);
*/	
	private static int i_skip_blank(String s, int index, int len){
    	char c;
    	while ( index < len ){
    		c=s.charAt(index);
    		if (!Character.isWhitespace(c)){
    			break;
    		}
    		index++;
    	}
    	return index;
    }
	
	private static int myparseInt( final String s )
	{
	    // Check for a sign.
	    int num  = 0;
	    int sign = 1;
	    int index=0;
	    boolean b_valid_number=false;
	    final int len  = s.length( );
	    // skip initial blank
	    index=i_skip_blank(s,index,len);
	    
	    char ch  = s.charAt( index );
	    if ( ch == '-' ){
	        sign = -1;
	    	index++;
	    }
	    else if ( ch == '+' ){
	        sign = +1;
	    	index++;
	    }
	    // skip blank after the sign
	    index=i_skip_blank(s,index,len);

	    // Build the number.
	    while ( index < len ){
	    	if ((s.charAt(index)<'0') || (s.charAt(index)>'9')){
	    		break;
	    	}
	        num = num*10 + s.charAt( index++ )-'0';
	        // at least one digit found: the number is valid
	        b_valid_number=true;
	    }
	    if (b_valid_number){
	    	num=sign*num;
	    }
	    else{
	    	num=0;
	    }
	    return num;
	} 	
	public enum_module_reply_type check_responses(String rx_string, StringBuilder reply_string){
		if (expected_responses.isEmpty()){
			return enum_module_reply_type.enum_radio_module_reply_none;
		}
		enum_module_reply_type reply=enum_module_reply_type.enum_radio_module_reply_none;
		boolean b_message_handled=false;
		// empty reply string
		reply_string.setLength(0);
		for(Iterator<String> it=expected_responses.iterator(); it.hasNext(); ) {
			if (b_message_handled){
				break;
			}
			String next_str=it.next();
		    if (rx_string.startsWith(next_str)){
		    	if(next_str.equalsIgnoreCase(reply_module_parameters)){
		    		b_message_handled=true;
		    		String[] clauses={"+ASACMOD:","Code=","Nt="};
		    		int i_crypt=0, i_terminal_number=0;
		    		int idx_next_parameter;
		    		it.remove();
		    		reply=enum_module_reply_type.enum_radio_module_reply_module_parameters;
		    		reply_string.append("asacmod reply: ");
		    		for (int i=0;i<clauses.length;i++){
		    			idx_next_parameter=rx_string.indexOf(clauses[i]);
		    			if (idx_next_parameter<0){
		    				reply_string.append("invalid reply");
		    				break;
		    			}
		    			switch(i){
		    				case 0:
		    					for (int idx=0;idx<list_modes.length();idx++){
		    						if (rx_string.indexOf(list_modes.name[idx])>0){
		    							reply_string.append(";mode "+list_modes.name[idx]);
		    							parameters.module_mode=list_modes.mode[idx];
		    							break;
		    						}
		    					}
					    		break;
		    				case 1:
				    			i_crypt=Integer.parseInt(rx_string.substring(idx_next_parameter+clauses[i].length()));
				    			v_assign_radio_crypt(i_crypt);
				    			reply_string.append(";crypt "+i_crypt);
				    			break;
		    				case 2:
		    					i_terminal_number=Integer.parseInt(rx_string.substring(idx_next_parameter+clauses[i].length()));
		    					reply_string.append(";terminal number "+i_terminal_number);
				    			v_assign_terminal_number(i_terminal_number);
				    			break;
		    			}
		    		}
		    		// parse the reply
		    	}
/*
 printf(risp,"+ASACI? -CHANNEL:(Ch=%u, Mod=%u); -MODE:(Mode=%u, Code=%u, Nt=%u); 
 -RADIO:(Coef=%i, Pwr=%u, Ramp=%u, SizePr=%u, Squelch=%u Dev=%u, BW=%u)"
                        ,RFPK.ChannelRF ,RFPK.FhMode        ,RFPK.Mode ,RFPK.Protect ,RFPK.TermNr,   RadioBase.OffsetCalPll
                                                                                                    ,RadioBase.PowerPa  
                                                                                                    ,RadioBase.PowerRaise
                                                                                                    ,RadioBase.SzPreamb
                                                                                                    ,RadioBase.RssiBase 
                                                                                                    ,RadioBase.Deviaz
                                                                                                    ,RadioBase.BandWidth
                                                                                                     );
 * */		    	
		    	else if(next_str.equalsIgnoreCase(reply_module_info)){
		    		b_message_handled=true;
		    		String[] clauses={"Ch=","Mod=","Mode=","Code=","Nt=","Coef=","Pwr=","Ramp=","SizePr=","Squelch=","Dev=","BW=","Prot=","HVers="};
		    		int idx_next_parameter;
		    		it.remove();
		    		reply=enum_module_reply_type.enum_radio_module_reply_module_info;
		    		reply_string.append("ASACI reply: ");
		    		// save the info string!
		    		string_module_info=rx_string;
		    		for (int i=0;i<clauses.length;i++){
		    			idx_next_parameter=rx_string.indexOf(clauses[i]);
		    			if (idx_next_parameter<0){
		    				reply_string.append("-->invalid reply, not found"+clauses[i]);
		    				break;
		    			}
		    			String test=rx_string.substring(idx_next_parameter+clauses[i].length());
		    			int value;
		    			value=myparseInt(test);
		    			reply_string.append(clauses[i]+value+",");
		    		}
		    	}
				else if (next_str.equalsIgnoreCase(reply_module_get_protect_code)) {
					b_message_handled=true;
					it.remove();
					reply=enum_module_reply_type.enum_radio_module_reply_get_protect_code;
					reply_string.append("asacradio get protect code reply: ");
					// save the info string!
					string_module_info=rx_string;
					int idx_next_parameter=rx_string.indexOf(reply_module_get_protect_code);
					int i_crypt=Integer.parseInt(rx_string.substring(idx_next_parameter + reply_module_get_protect_code.length()));
					reply_string.append(i_crypt);
					v_assign_radio_crypt(i_crypt);
				}
				else if (next_str.equalsIgnoreCase(reply_module_set_protect_code)) {
					b_message_handled=true;
					it.remove();
					reply=enum_module_reply_type.enum_radio_module_reply_set_protect_code;
					reply_string.append("asacradio set protect code reply: ");
					// save the info string!
					string_module_info=rx_string;
					int idx_next_parameter=rx_string.indexOf(reply_module_set_protect_code);
					int i_new_crypt=Integer.parseInt(rx_string.substring(idx_next_parameter + reply_module_get_protect_code.length()));
					reply_string.append(i_new_crypt);
				}

				else if(next_str.equalsIgnoreCase(reply_module_radio_set_internal_params_at_default)){
					b_message_handled=true;
					String[] clauses={"Coef=","Pwr=","Ramp=","SizePr=","Squelch=","Dev=","BW="};
					int idx_next_parameter;
					it.remove();
					reply=enum_module_reply_type.enum_radio_module_reply_set_internal_params_default;
					reply_string.append("asacradio internal parameters reply: ");
					// save the info string!
					string_module_info=rx_string;
					for (int i=0;i<clauses.length;i++){
						idx_next_parameter=rx_string.indexOf(clauses[i]);
						if (idx_next_parameter<0){
							reply_string.append("-->invalid reply, not found"+clauses[i]);
							break;
						}
						String test=rx_string.substring(idx_next_parameter+clauses[i].length());
						int value;
						value=myparseInt(test);
						reply_string.append(clauses[i]+value+",");
					}
				}
				else if (next_str.equalsIgnoreCase(reply_module_radio_set_channel_at_default))
				{
					b_message_handled=true;
					String[] clauses={"Ch=","Mod="};
					int idx_next_parameter;
					it.remove();
					reply=enum_module_reply_type.enum_radio_module_reply_set_channel_default;
					reply_string.append("asacradio channel reply: ");
					// save the info string!
					string_module_info=rx_string;
					for (int i=0;i<clauses.length;i++){
						idx_next_parameter=rx_string.indexOf(clauses[i]);
						if (idx_next_parameter<0){
							reply_string.append("-->invalid reply, not found"+clauses[i]);
							break;
						}
						String test=rx_string.substring(idx_next_parameter+clauses[i].length());
						int value;
						value=myparseInt(test);
						reply_string.append(clauses[i]+value+",");
					}
				}
		    	else if (next_str.equalsIgnoreCase(reply_module_radio_firmware_version)){
		    		b_message_handled=true;
		    		boolean b_valid_message=true;
		    		String[] clauses={reply_module_radio_firmware_version,"DATE=","TIME="};
		    		String sep_radio_fw_version=",";
		    		boolean[] b_numeric_clause={false,false,false};
		    		int idx_next_parameter;
		    		it.remove();
		    		reply_string.append("asacmod reply: ");
		    		// save the info string!
		    		string_module_info=rx_string;
		    		for (int i=0;i<clauses.length;i++){
		    			idx_next_parameter=rx_string.indexOf(clauses[i]);
		    			if (idx_next_parameter<0){
		    				reply_string.append("-->invalid reply, not found"+clauses[i]);
		    				b_valid_message=false;
		    				break;
		    			}
		    			String value_as_string=rx_string.substring(idx_next_parameter+clauses[i].length());
		    			int value;
		    			value=0;
		    			if (b_numeric_clause[i]){
		    				value=myparseInt(value_as_string);
			    			reply_string.append(clauses[i]+String.valueOf(value)+",");
		    			}else{
		    				int idx_next_sep=0;
		    				idx_next_sep=value_as_string.indexOf(sep_radio_fw_version);
		    				if (idx_next_sep>=0){
		    					value_as_string=value_as_string.substring(0, idx_next_sep);
		    				}
		    				reply_string.append(clauses[i]+value_as_string+",");
		    			}
		    			switch(i){
			    			case 0:
			    			{
			    				setFirmwareVersion(value_as_string);
			    				break;
			    			}
			    			case 1:
			    			{
			    				setFirmwareDate(value_as_string);
			    				break;
			    			}
			    			case 2:
			    			{
			    				setFirmwareTime(value_as_string);
			    				break;
			    			}
			    			default:
			    			{
			    				break;
			    			}
		    			}
		    			
		    		}
		    		if (b_valid_message){
		    			reply=enum_module_reply_type.enum_radio_module_reply_radio_firmware_version;
		    		}
		    		
		    	}
		    	else if(next_str.equalsIgnoreCase(reply_module_short_radio_status)){
		    		if (rx_string.length()>=5){
		    			boolean b_message_queued_OK=false;
		    			if( rx_string.charAt(1)=='1'){
		    				b_message_queued_OK=true;
		    			}
		    			set_radio_queue_OK(b_message_queued_OK);
		    			
		    			boolean b_queue_NOR_is_full=false;
		    			if( rx_string.charAt(2)=='1'){
		    				b_queue_NOR_is_full=true;
		    			}
		    			set_radio_queue_full(b_queue_NOR_is_full);
		    			
		    			// handles work mode
    					String s_work_mode=Character.toString(rx_string.charAt(3));
	    				enum_short_radio_work_mode new_work_mode=enum_short_radio_work_mode.U;
	    				for (enum_short_radio_work_mode e:enum_short_radio_work_mode.values()){
	    					if (e.toString().equalsIgnoreCase(s_work_mode)){
	    						new_work_mode=e;
	    						break;
	    					}
	    				}
	    				setWork_mode(new_work_mode);
	    				
	    				enum_short_radio_strength new_radio_strength=enum_short_radio_strength.U;
	    				String s_radio_strength=Character.toString(rx_string.charAt(4));
	    				for (enum_short_radio_strength e:enum_short_radio_strength.values()){
	    					if (e.toString().equalsIgnoreCase(s_radio_strength)){
	    						new_radio_strength=e;
	    						break;
	    					}
	    				}
	    				setRadio_strength(new_radio_strength);
	    				reply=enum_module_reply_type.enum_radio_module_reply_short_radio_status;
	    				
		    		}
		    	}
		    	else if(next_str.equalsIgnoreCase(reply_module_radio_status)){
		    		b_message_handled=true;
		    		boolean b_valid_message=true;
		    		String[] clauses={"MODE=","TXQUEUE=","STRENGTH=","RSSI="};
		    		String sep_radio_status=",";
		    		boolean[] b_numeric_clause={false,false,false,true};
		    		int idx_next_parameter;
		    		it.remove();
		    		reply_string.append("asac status reply: ");
		    		// save the info string!
		    		string_module_info=rx_string;
		    		for (int i=0;i<clauses.length;i++){
		    			idx_next_parameter=rx_string.indexOf(clauses[i]);
		    			if (idx_next_parameter<0){
		    				reply_string.append("-->invalid reply, not found"+clauses[i]);
		    				b_valid_message=false;
		    				break;
		    			}
		    			String value_as_string=rx_string.substring(idx_next_parameter+clauses[i].length());
		    			int value;
		    			value=0;
		    			if (b_numeric_clause[i]){
		    				value=myparseInt(value_as_string);
			    			reply_string.append(clauses[i]+String.valueOf(value)+",");
		    			}else{
		    				int idx_next_sep=0;
		    				idx_next_sep=value_as_string.indexOf(sep_radio_status);
		    				if (idx_next_sep>=0){
		    					value_as_string=value_as_string.substring(0, idx_next_sep);
		    				}
		    				reply_string.append(clauses[i]+value_as_string+",");
		    			}
		    			switch(i){
			    			case 0:
			    			{
			    				enum_radio_work_mode new_work_mode=enum_radio_work_mode.unknown;
			    				for (enum_radio_work_mode e:enum_radio_work_mode.values()){
			    					if (e.toString().equalsIgnoreCase(value_as_string)){
			    						new_work_mode=e;
			    						break;
			    					}
			    				}
			    				setWork_mode(new_work_mode);
			    				break;
			    			}
			    			case 1:
			    			{
			    				enum_radio_txqueue_status new_txqueue_status=enum_radio_txqueue_status.unknown;
			    				for (enum_radio_txqueue_status e:enum_radio_txqueue_status.values()){
			    					if (e.toString().equalsIgnoreCase(value_as_string)){
			    						new_txqueue_status=e;
										break;
									}
			    				}
								setTxqueue_status(new_txqueue_status);
			    				break;
			    			}
			    			case 2:
			    			{
			    				enum_radio_strength new_radio_strength=enum_radio_strength.unknown;
			    				for (enum_radio_strength e:enum_radio_strength.values()){
			    					if (e.toString().equalsIgnoreCase(value_as_string)){
			    						new_radio_strength=e;
										break;
									}
			    				}
								setRadio_strength(new_radio_strength);
			    				break;
							}
			    			case 3: {
								setRssi(value);
			    				break;
			    			}
			    			default:
			    			{
			    				break;
			    			}
		    			}
		    			
		    		}
		    		if (b_valid_message){
		    			reply=enum_module_reply_type.enum_radio_module_reply_radio_status;
		    		}
		    	}
		    	else if(next_str.equalsIgnoreCase(reply_module_parameters_save)){
		    		b_message_handled=true;
		    		reply=enum_module_reply_type.enum_radio_module_reply_module_reset;
		    		reply_string.append(rx_string);
		    		it.remove();
		    	}
		    	else if (next_str.equalsIgnoreCase(reply_module_test)){
		    		b_message_handled=true;
		    		reply=enum_module_reply_type.enum_radio_module_reply_module_test;
		    		reply_string.append(rx_string);
		    		it.remove();
		    	}
		    	
		    }
		}
		if (--i_reply_timeout<=0) {
			i_reply_timeout=0;
			expected_responses.clear();
		}
		return reply;
	}
	
	private void set_radio_queue_full(boolean b_message_queue_NOR_is_full) {
		this.setB_message_queue_NOR_is_full(b_message_queue_NOR_is_full);
		
	}

	private boolean last_message_queued_OK=false;
	private void set_radio_queue_OK(boolean b_message_queued_OK) {
		// TODO Auto-generated method stub
		setLast_message_queued_OK(b_message_queued_OK);
		
	}
	public enum_radio_work_mode getWork_mode() {
		return work_mode;
	}
	public void setWork_mode(enum_radio_work_mode work_mode) {
		this.work_mode = work_mode;
	}
	public void setWork_mode(enum_short_radio_work_mode short_work_mode) {
		switch(short_work_mode){
		case F:
			this.work_mode = enum_radio_work_mode.off;
			break;
		case S:
			this.work_mode = enum_radio_work_mode.search;
			break;
		case U:
		default:
			this.work_mode = enum_radio_work_mode.unknown;
			break;
		case W:
			this.work_mode = enum_radio_work_mode.work;
			break;
		
		}
		{
			Intent i = new Intent();
			i.setAction("ASAC.action.SC_RADIO_INTENSITY_CHANGED");
			i.putExtra(RADIO_INTENSITY_CHANGED_WORK_MODE, work_mode);
			this.m_the_context.sendBroadcast(i);
		}

	}
	public enum_radio_work_mode translate_Work_mode(enum_short_radio_work_mode short_work_mode) {
		enum_radio_work_mode wm=enum_radio_work_mode.off;;
		switch(short_work_mode){
		case F:
			wm = enum_radio_work_mode.off;
			break;
		case S:
			wm = enum_radio_work_mode.search;
			break;
		case U:
		default:
			wm = enum_radio_work_mode.unknown;
			break;
		case W:
			wm = enum_radio_work_mode.work;
			break;
		
		}
		return wm;
		
	}
	public enum_radio_txqueue_status getTxqueue_status() {
		return txqueue_status;
	}
	public void setTxqueue_status(enum_radio_txqueue_status txqueue_status) {
		this.txqueue_status = txqueue_status;
	}
	public enum_radio_strength getRadio_strength() {
		return radio_strength;
	}
	public void setRadio_strength(enum_radio_strength radio_strength) {
		this.radio_strength = radio_strength;
	}
	public void setRadio_strength(enum_short_radio_strength short_radio_strength) {
		switch(short_radio_strength){
		case E:
			this.radio_strength = enum_radio_strength.excellent;
			break;
		case F:
			this.radio_strength = enum_radio_strength.off;
			break;
		case G:
			this.radio_strength = enum_radio_strength.good;
			break;
		case L:
			this.radio_strength = enum_radio_strength.low;
			break;
		case U:
		default:
			this.radio_strength = enum_radio_strength.unknown;
			break;
			
		}
		{
			Intent i = new Intent();
			i.setAction("ASAC.action.SC_RADIO_INTENSITY_CHANGED");
			i.putExtra(RADIO_INTENSITY_CHANGED_STRENGTH, radio_strength);
			this.m_the_context.sendBroadcast(i);
		}
	}
	public enum_radio_strength translate_Radio_strength(enum_short_radio_strength short_radio_strength) {
		enum_radio_strength rs=enum_radio_strength.off;
		switch(short_radio_strength){
		case E:
			rs = enum_radio_strength.excellent;
			break;
		case F:
			rs = enum_radio_strength.off;
			break;
		case G:
			rs = enum_radio_strength.good;
			break;
		case L:
			rs = enum_radio_strength.low;
			break;
		case U:
		default:
			rs = enum_radio_strength.unknown;
			break;
			
		}
		return rs;
		
	}
	public final String RADIO_INTENSITY_CHANGED_RSSI = "RADIO_INTENSITY_CHANGED_RSSI";
	public final String RADIO_INTENSITY_CHANGED_STRENGTH = "RADIO_INTENSITY_CHANGED_STRENGTH";
	public final String RADIO_INTENSITY_CHANGED_WORK_MODE = "RADIO_INTENSITY_CHANGED_WORK_MODE";


	public int getRssi() {
		return rssi;
	}
	public void setRssi(int rssi)
	{
		this.rssi = rssi;
		Intent i = new Intent();
		i.setAction("ASAC.action.SC_RADIO_INTENSITY_CHANGED");
		i.putExtra(RADIO_INTENSITY_CHANGED_RSSI, rssi);
		this.m_the_context.sendBroadcast(i);
	}
	public boolean isLast_message_queued_OK() {
		return last_message_queued_OK;
	}
	public void setLast_message_queued_OK(boolean last_message_queued_OK) {
		this.last_message_queued_OK = last_message_queued_OK;
	}
	public boolean isB_message_queue_NOR_is_full() {
		return b_message_queue_NOR_is_full;
	}
	public void setB_message_queue_NOR_is_full(boolean b_message_queue_NOR_is_full) {
		this.b_message_queue_NOR_is_full = b_message_queue_NOR_is_full;
	}
}

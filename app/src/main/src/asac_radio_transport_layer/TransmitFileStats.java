package asac_radio_transport_layer;

/**
 * Created by michele on 18/07/16.
 */
public class TransmitFileStats {
    public long last_file_bytes_TX;
    public long l_file_size;
    public void clearall(){
        last_file_bytes_TX=0;
        l_file_size=0;
    }
    /**
     * constructor
     */
    public TransmitFileStats(){
        clearall();
    }
}


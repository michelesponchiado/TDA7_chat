package asacRadioRFPower;

import android.content.Context;
import android.os.PowerManager;
import android.os.RadioRF;
import android.util.Log;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by michele on 08/07/16.
 */
public class Asac_radioRF_power implements Runnable{
    private static final String TAG = "Asac_radioRF_power";
    private final int delay_after_radio_on_ms = 600;
    private final int delay_after_radio_off_ms = 100;
    private final int delay_wait_requests_ms = 100;
    private final int DELAY_BETWEEN_CHECK_REQUESTS_MS = 10;
    private RadioRF radioRF = null;
    private boolean enable = false;
    private boolean radio_is_on = false;
    private boolean radio_exists = false;
    // a semaphore to handle access to the network layer
    private final Lock access_request = new ReentrantLock(true);
    public static final int MAX_DELAY_WAIT_REQUEST_RADIO_MS = 5000;

    private enum_radio_power_requests at_startup_req;
    private long timeout_off_ms = 10000;
    private final int MIN_TIMEOUT_OFF_MS = 5000;
    private final int MAX_TIMEOUT_OFF_MS = 60*60*1000;
    private boolean auto_off_enabled = false;
    private long base_auto_off_ms = 0;
    private long base_go_off_ms = 0;
    public enum enum_radio_power_requests{
        on,
        off,
        release_on,
        refresh_on,
        none;
        private static enum_radio_power_requests[] allValues = values();
        public static enum_radio_power_requests fromOrdinal(int n) {return allValues[n];}
    };
    private int[]  num_radio_requests= new int[enum_radio_power_requests.values().length];
    private int[]  num_radio_acks= new int[enum_radio_power_requests.values().length];

    /**
     * receive network layer status of the receiving message check
     * @author root
     *
     */
    private enum enum_pwr_handle_status{
        idle,
        init,
        accept_every_request,
        switch_radio_on,
        radio_on,
        init_wait_off,
        wait_off,
        about_to_go_off,
        switch_radio_off,
        radio_off,
    };
    private enum_pwr_handle_status pwr_handle_status = enum_pwr_handle_status.idle;
    private Context m_the_Context;
    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;
    private Thread thread = null;
    public Asac_radioRF_power(Context m_the_Context, boolean enable, enum_radio_power_requests at_startup_radio_req, long timeout_off_ms) throws RuntimeException {
        this.m_the_Context = m_the_Context;
        powerManager = (PowerManager) this.m_the_Context.getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"RadioPowerWakeLock");

        RadioRF radioRF = (RadioRF)m_the_Context.getSystemService(m_the_Context.RADIORF_SERVICE);
        if (radioRF == null || !radioRF.exists()) {
            throw new RuntimeException("Unable to access RadioRF service");
        }
        this.radioRF = radioRF;
        this.enable = enable;
        this.at_startup_req = at_startup_radio_req;
        this.radio_exists = ((this.radioRF != null) && (this.radioRF.exists()));
        this.pwr_handle_status = enum_pwr_handle_status.init;
        if (timeout_off_ms > MIN_TIMEOUT_OFF_MS && timeout_off_ms < MAX_TIMEOUT_OFF_MS)
        {
            this.timeout_off_ms = timeout_off_ms;
            this.auto_off_enabled = true;
        }
        else
        {
            this.auto_off_enabled = false;
        }
        // thread start
        try{
            thread = new Thread(this);
            thread.start();
        }catch (IllegalThreadStateException	e){
            Log.e(TAG, "the power handling thread can' t be started");
            throw new RuntimeException("Unable to start the power handling thread");
        }
    }

    private void myDelay(long ms)
    {
        try{
            Thread.sleep(ms);
        }catch(InterruptedException e) {

        }

    }
    private enum enum_switch_radio{
        on,
        off,
    };

    private void switch_radio(enum_switch_radio onoff)
    {
        if (onoff == enum_switch_radio.on)
        {
            radioRF.on();
            myDelay(delay_after_radio_on_ms);
            this.radio_is_on = true;
        }
        else
        {
            this.radio_is_on = false;
            radioRF.off();
            myDelay(delay_after_radio_off_ms);
        }

    }


    // a new object produced, with a timeout in ms
    private enum_radio_power_requests wait_new_radio_request(long l_timeout_ms) {
        // check period {ms}
        long l_wait_time_ms=DELAY_BETWEEN_CHECK_REQUESTS_MS;
        // check period
        if (l_wait_time_ms>l_timeout_ms){
            l_wait_time_ms=1;
        }
        // set actual time ms
        long l_base_time_ms=System.currentTimeMillis();
        enum_radio_power_requests req = enum_radio_power_requests.none;
        boolean is_timeout = false;
        // Wait until a new object is available.
        while (req == enum_radio_power_requests.none && ! is_timeout) {
            myDelay(l_wait_time_ms);
            for (int i = 0; i < num_radio_requests.length; i++)
            {
                int nreq = num_radio_requests[i];
                int nacks = num_radio_acks[i];
                if (nreq != nacks)
                {

                    num_radio_acks[i] = nreq;
                    req = enum_radio_power_requests.fromOrdinal(i);
                    Log.i(TAG,"new request: " + req.toString());
                }
            }

            // check actual time
            long l_actual_time_ms=System.currentTimeMillis();
            // too long wait???
            if (l_actual_time_ms-l_base_time_ms>l_timeout_ms){
                is_timeout = true;
            }
        }
        return req;
    }

    public boolean is_radio_on()
    {
        return this.radio_is_on;
    }
    private boolean wait_radio_request(enum_radio_power_requests req, long l_timeout_ms)
    {
        if (req != enum_radio_power_requests.on && req != enum_radio_power_requests.off)
        {
            return true;
        }
        // set actual time ms
        long l_base_time_ms=System.currentTimeMillis();
        boolean is_timeout = false;
        boolean req_accomplished = false;
        while(!is_timeout && ! req_accomplished)
        {
            switch(req)
            {
                case on:
                {
                    if (is_radio_on())
                    {
                        req_accomplished = true;
                    }
                    break;
                }
                case off:
                {
                    if (!is_radio_on())
                    {
                        req_accomplished = true;
                    }
                    break;
                }
                case release_on:
                {
                    if (this.pwr_handle_status == enum_pwr_handle_status.wait_off)
                    {
                        req_accomplished = true;
                    }
                    break;
                }
                default:
                {
                    req_accomplished = true;
                }
            }
            if (!req_accomplished)
            {
                // check actual time
                long l_actual_time_ms=System.currentTimeMillis();
                // too long wait???
                if (l_actual_time_ms-l_base_time_ms>l_timeout_ms){
                    is_timeout = true;
                }
                else
                {
                    myDelay(50);
                }
            }
        }
        return req_accomplished;
    }
    public boolean b_off_or_about_to_go_off()
    {
        if (    (this.pwr_handle_status == enum_pwr_handle_status.about_to_go_off)
              ||(this.pwr_handle_status == enum_pwr_handle_status.switch_radio_off)
              ||(this.pwr_handle_status == enum_pwr_handle_status.radio_off)
           )
        {
            return true;
        }
        return false;
    }
    public void stop()
    {
        if (this.thread != null)
        {
            this.thread.interrupt();
            this.thread = null;
        }
    }
    // a new radio power request
    public boolean is_OK_radio_power_request(enum_radio_power_requests e, long timeout_ms) {
        boolean returnCode = true;
        boolean acknowledged = false;
        boolean b_wait_request_ack = true;
        Log.i(TAG,"issued radio req: "+ e.toString());

        // check if it is really necessary to wait for the request to be satisfied
        switch(e)
        {
            case on:
            {
                if (is_radio_on() && !b_off_or_about_to_go_off())
                {
                    b_wait_request_ack = false;
                }
                break;
            }
            case release_on:
            case refresh_on:
            {
                b_wait_request_ack = false;
                break;
            }
            default:
            {
                break;
            }
        }
        // issue the request
        access_request.lock();
        {
            final int idx = e.ordinal();
            int num_ack = this.num_radio_acks[idx];
            // Store new request
            this.num_radio_requests[idx]++;
            //notifyAll();
            // only if an explicit ack is needed, wait for it
            if (b_wait_request_ack) {
                Log.i(TAG,"waiting ack of: "+ e.toString());
                final int max_delay_ms = 3000;
                final int delay_base_ms = 20;
                final int max_num_loop = 1 + max_delay_ms / delay_base_ms;
                int num_loop = max_num_loop;
                while (b_wait_request_ack && !acknowledged && (num_loop > 0)) {
                    acknowledged = this.num_radio_acks[idx] != num_ack;
                    myDelay(delay_base_ms);
                    num_loop--;
                }
            }
        }
        access_request.unlock();
        // if necessary, wait until the operation is completed
        if (b_wait_request_ack && acknowledged)
        {
            Log.i(TAG,"received ack OK of: "+ e.toString());
            switch(e)
            {
                case on:
                case off:
                {
                    returnCode = false;
                    if (wait_radio_request(e, timeout_ms))
                    {
                        returnCode = true;
                    }
                    break;
                }
                default:
                {
                    break;
                }
            }
        }
        Log.i(TAG,"exiting with code: "+ returnCode);

        return returnCode;
    }
    /**
     * the power handling request thread...
     */
    public void run() {
        try
        {
            while(true){
                if (!this.enable || !this.radio_exists)
                {
                    try{
                        Thread.sleep(100);
                    }catch(InterruptedException e){

                    }
                    continue;
                }
                switch (this.pwr_handle_status)
                {
                    case idle:
                    {
                        break;
                    }
                    case init:
                    {

                        if (this.at_startup_req == enum_radio_power_requests.on)
                        {
                            Log.i(TAG,"init goto ON");
                            this.pwr_handle_status = enum_pwr_handle_status.switch_radio_on;
                        }
                        else if (this.at_startup_req == enum_radio_power_requests.off)
                        {
                            Log.i(TAG,"init goto OFF");
                            this.pwr_handle_status = enum_pwr_handle_status.switch_radio_off;
                        }
                        else
                        {
                            Log.i(TAG,"init goto every req");
                            this.pwr_handle_status = enum_pwr_handle_status.accept_every_request;
                        }
                        break;
                    }
                    case accept_every_request:
                    {
                        enum_radio_power_requests new_req = wait_new_radio_request(delay_wait_requests_ms);
                        switch (new_req)
                        {
                            case off:
                            {
                                Log.i(TAG,"aer goto OFF");
                                this.pwr_handle_status = enum_pwr_handle_status.switch_radio_off;
                                break;
                            }
                            case on:
                            {
                                Log.i(TAG,"aer goto ON");
                                this.pwr_handle_status = enum_pwr_handle_status.switch_radio_on;
                                break;
                            }
                            case none:
                            {
                                break;
                            }
                        }

                    }
                    case switch_radio_on:
                    {
                        wakeLock.acquire();
                        switch_radio(enum_switch_radio.on);
                        Log.i(TAG, "radio module is powered ON and settled");
                        this.pwr_handle_status = enum_pwr_handle_status.radio_on;
                        break;
                    }
                    case radio_on:
                    {
                        enum_radio_power_requests new_req = wait_new_radio_request(delay_wait_requests_ms);
                        switch (new_req)
                        {
                            case off:
                            {
                                Log.i(TAG,"radio_on: goto OFF");
                                this.pwr_handle_status = enum_pwr_handle_status.switch_radio_off;
                                break;
                            }
                            case release_on:
                            {
                                if (this.auto_off_enabled)
                                {
                                    Log.i(TAG,"radio_on: goto wait OFF");
                                    this.pwr_handle_status = enum_pwr_handle_status.init_wait_off;
                                }
                                break;
                            }
                            case none:
                            {
                                break;
                            }
                            default:
                            {
                                Log.i(TAG,"radio_on: the radio is already ON");
                                break;
                            }
                        }
                        break;
                    }
                    case init_wait_off:
                    {
                        this.base_auto_off_ms = System.currentTimeMillis();
                        this.pwr_handle_status = enum_pwr_handle_status.wait_off;
                        break;
                    }
                    case wait_off:
                    {
                        if (!this.auto_off_enabled)
                        {
                            this.pwr_handle_status = enum_pwr_handle_status.radio_on;
                        }
                        else
                        {
                            enum_radio_power_requests new_req = wait_new_radio_request(delay_wait_requests_ms);
                            switch (new_req)
                            {
                                case off:
                                {
                                    Log.i(TAG,"wo goto OFF");
                                    this.pwr_handle_status = enum_pwr_handle_status.switch_radio_off;
                                    break;
                                }
                                case on:
                                {
                                    Log.i(TAG,"wo goto ON");
                                    this.pwr_handle_status = enum_pwr_handle_status.radio_on;
                                    break;
                                }
                                case refresh_on:
                                {
                                    Log.i(TAG,"wo refresh ON");
                                    this.pwr_handle_status = enum_pwr_handle_status.init_wait_off;
                                    break;
                                }
                                case none:
                                default:
                                {
                                    long now_ms = System.currentTimeMillis();
                                    if (now_ms - this.base_auto_off_ms > this.timeout_off_ms)
                                    {
                                        this.base_go_off_ms = now_ms;
                                        Log.i(TAG,"about to go OFF");
                                        this.pwr_handle_status = enum_pwr_handle_status.about_to_go_off;
                                    }
                                    break;
                                }
                            }

                        }
                        break;
                    }
                    case about_to_go_off:
                    {
                        enum_radio_power_requests new_req = wait_new_radio_request(delay_wait_requests_ms);
                        switch (new_req)
                        {
                            case off:
                            {
                                Log.i(TAG,"goto OFF");
                                this.pwr_handle_status = enum_pwr_handle_status.switch_radio_off;
                                break;
                            }
                            case on:
                            {
                                Log.i(TAG,"goto ON");
                                this.pwr_handle_status = enum_pwr_handle_status.radio_on;
                                break;
                            }
                            case refresh_on:
                            {
                                Log.i(TAG,"refresh ON");
                                this.pwr_handle_status = enum_pwr_handle_status.init_wait_off;
                                break;
                            }
                            case none:
                            default:
                            {
                                long now_ms = System.currentTimeMillis();
                                if (now_ms - this.base_go_off_ms > 500)
                                {
                                    this.pwr_handle_status = enum_pwr_handle_status.switch_radio_off;
                                }
                                break;
                            }
                        }
                        break;
                    }
                    case switch_radio_off:
                    {
                        Log.i(TAG,"OFF");
                        switch_radio(enum_switch_radio.off);
                        wakeLock.release();
                        this.pwr_handle_status = enum_pwr_handle_status.radio_off;
                        break;
                    }
                    case radio_off:
                    {
                        enum_radio_power_requests new_req = wait_new_radio_request(delay_wait_requests_ms);
                        if (new_req == enum_radio_power_requests.on)
                        {
                            this.pwr_handle_status = enum_pwr_handle_status.switch_radio_on;
                        }
                        break;
                    }
                }
            }
        }
        finally{
            wakeLock.release();
        }
    }
}

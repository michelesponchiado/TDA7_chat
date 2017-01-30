package asacLEDs;

/**
 * Created by michele on 06/07/16.
 */

import android.content.Context;
import android.os.EEPROM;
import android.os.RadioRF;
import android.os.RemoteException;
import android.util.Log;

public class Asac_LEDs implements Runnable {
    private static final String TAG = "Asac_LEDs";

    private RadioRF radioRF = null;
    private boolean autoBlueLed = false;
    private Thread thread = null;
    public Asac_LEDs(Context m_the_Context) throws RuntimeException {
        RadioRF radioRF = (RadioRF)m_the_Context.getSystemService(m_the_Context.RADIORF_SERVICE);
        if (radioRF == null || !radioRF.exists()) {
            throw new RuntimeException("Unable to access RadioRF service");
        }
        this.radioRF = radioRF;
// thread start
        try{
            thread = new Thread(this);
            thread.start();
        }catch (IllegalThreadStateException	e){
            Log.e(TAG, "the power handling thread can' t be started");
            throw new RuntimeException("Unable to start the power handling thread");
        }
    }
    public enum enum_auto_LED_status
    {
        init_wait_on,
        wait_on,
        on,
        init_wait_off,
        wait_off,
        off,
    }
    private enum_auto_LED_status status_autoBlue = enum_auto_LED_status.wait_on;
    private int autoblueLEDon_req;
    private int autoblueLEDon_ack;
    private long base_wait_auto_blue;
    private long time_blueLED_auto_on_ms = 100;
    public void enableAutoBluedLED(boolean enable)
    {
        autoBlueLed = enable;
    }
    public void pulseBlueOn()
    {
        autoblueLEDon_req ++;
    }
    public void stop()
    {
        if (this.thread != null)
        {
            this.thread.interrupt();
            this.thread = null;
        }
    }
    public void run() {
        while(true)
        {
            if (!autoBlueLed)
            {
                try{
                    Thread.sleep(200);
                }catch(InterruptedException e){

                }
                continue;
            }
            try{
                Thread.sleep(10);
            }catch(InterruptedException e){

            }
            switch(status_autoBlue)
            {
                case init_wait_on:
                {
                    status_autoBlue = enum_auto_LED_status.wait_on;
                    break;
                }
                case wait_on:
                {
                    if (autoblueLEDon_req != autoblueLEDon_ack)
                    {
                        autoblueLEDon_ack = autoblueLEDon_req;
                        status_autoBlue = enum_auto_LED_status.on;
                    }
                    break;
                }
                case on:
                {
                    try
                    {
                        blueOn();
                    }
                    catch(RuntimeException e)
                    {
                        ;
                    }
                    status_autoBlue = enum_auto_LED_status.init_wait_off;
                    break;
                }
                case init_wait_off:
                {
                    base_wait_auto_blue = System.currentTimeMillis();
                    status_autoBlue = enum_auto_LED_status.wait_off;
                    break;
                }
                case wait_off:
                {
                    if (autoblueLEDon_req != autoblueLEDon_ack)
                    {
                        autoblueLEDon_ack = autoblueLEDon_req;
                        status_autoBlue = enum_auto_LED_status.on;
                    }
                    else
                    {
                        if (System.currentTimeMillis() - base_wait_auto_blue >= time_blueLED_auto_on_ms)
                        {
                            status_autoBlue = enum_auto_LED_status.off;

                        }
                    }
                    break;
                }
                case off:
                {
                    try
                    {
                        blueOff();
                    }
                    catch(RuntimeException e)
                    {
                        ;
                    }
                    status_autoBlue = enum_auto_LED_status.init_wait_on;
                    break;
                }
            }

        }
    }
    public void blueOn() throws RuntimeException
    {
        if (radioRF == null || !radioRF.exists())
        {
            throw new RuntimeException("Unable to access RadioRF service;");
        }
        try
        {
            radioRF.LEDBlueOn();
        }
        catch(RemoteException e)
        {
            throw new RuntimeException("LED operation error:"+e.toString());
        }
    }

    public void blueOff() throws RuntimeException
    {
        if (radioRF == null || !radioRF.exists())
        {
            throw new RuntimeException("Unable to access RadioRF service;");
        }
        try
        {
            radioRF.LEDBlueOff();
        }
        catch(RemoteException e)
        {
            throw new RuntimeException("LED operation error:"+e.toString());
        }
    }

    public void greenOn() throws RuntimeException
    {
        if (radioRF == null || !radioRF.exists())
        {
            throw new RuntimeException("Unable to access RadioRF service;");
        }
        try
        {
            radioRF.LEDGreenOn();
        }
        catch(RemoteException e)
        {
            throw new RuntimeException("LED operation error:"+e.toString());
        }
    }
    public void greenOff() throws RuntimeException
    {
        if (radioRF == null || !radioRF.exists())
        {
            throw new RuntimeException("Unable to access RadioRF service;");
        }
        try
        {
            radioRF.LEDGreenOff();
        }
        catch(RemoteException e)
        {
            throw new RuntimeException("LED operation error:"+e.toString());
        }
    }

}

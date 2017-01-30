/**
 * Created by michele on 28/06/16.
 */

package asacEEPROM;
import android.content.Context;
import android.os.EEPROM;
import android.os.RemoteException;

public class Asac_EEPROM {
    private EEPROM eeprom = null;
    public Asac_EEPROM(Context m_the_Context) throws RuntimeException {
        EEPROM eeprom = (EEPROM) m_the_Context.getSystemService(m_the_Context.EEPROM_SERVICE);
        if (eeprom == null || !eeprom.exists()) {
            throw new RuntimeException("Unable to access EEPROM service");
        }
        this.eeprom = eeprom;
    }
    public enum Enum_EEPROM_area{
        enum_EEPROM_area_user,
        enum_EEPROM_area_tech,
        enum_EEPROM_area_factory,
    };

    private final int idx_EEPROM_area_user = 0;
    private final int idx_EEPROM_area_tech = 1;
    private final int idx_EEPROM_area_factory = 2;

    private final int offset_factory_EEPROM_serial_number = 0;
    private final int offset_factory_EEPROM_terminal_number = 0;
    private final int offset_technician_EEPROM_auto_shutdown = 0;

    private final int EEPROM_PASSWORD_FACTORY = 0x25ACFAC0;
    private final int EEPROM_PASSWORD_TECH = 0x1EC4A3EA;


    public final int maxEEPROM_user_words = 10*1024;
    public final int maxEEPROM_tech_words = 4*1024;
    public final int maxEEPROM_factory_words = 2*1024;

    private int index_from_area(Enum_EEPROM_area a) throws RuntimeException
    {
        int idx = idx_EEPROM_area_user;
        switch(a)
        {
            case enum_EEPROM_area_user:
            {
                idx = idx_EEPROM_area_user;
                break;
            }
            case enum_EEPROM_area_tech:
            {
                idx = idx_EEPROM_area_tech;
                break;
            }
            case enum_EEPROM_area_factory:
            {
                idx = idx_EEPROM_area_factory;
                break;
            }
            default:
            {
                throw new RuntimeException("Invalid area: " + a.toString());
            }
        }
        return idx;
    }

    private void writeEEPROM(int area, int word_index, int word_write, int password) throws RuntimeException
    {
        if (eeprom == null || !eeprom.exists())
        {
            throw new RuntimeException("Unable to access EEPROM service;");
        }
        try
        {
            eeprom.writeEEPROM(area, word_index, word_write, password);

        }
        catch(RemoteException e)
        {
            throw new RuntimeException("Error writing EEPROM:"+e.toString());
        }
    }
    private void writeEEPROM(int area, int byte_index, byte [] byteArray, int num_of_bytes, int password) throws RuntimeException
    {
        if (eeprom == null || !eeprom.exists())
        {
            throw new RuntimeException("Unable to access EEPROM service;");
        }
        try
        {
            eeprom.writeEEPROMbyteArray(area, byte_index, byteArray, num_of_bytes, password);

        }
        catch(RemoteException e)
        {
            throw new RuntimeException("Error writing EEPROM:"+e.toString());
        }
    }
    private int readEEPROM(int area, int word_index, int password) throws RuntimeException
    {
        if (eeprom == null || !eeprom.exists())
        {
            throw new RuntimeException("Unable to access EEPROM service;");
        }
        int word_read = 0;
        try
        {
            word_read = eeprom.readEEPROM(area, word_index, password);

        }
        catch(RemoteException e)
        {
            throw new RuntimeException("Error reading EEPROM:"+e.toString());
        }
        return word_read;
    }
    private int readEEPROM(int area, int byte_index, byte [] byteArray, int num_of_bytes, int password) throws RuntimeException
    {
        if (eeprom == null || !eeprom.exists())
        {
            throw new RuntimeException("Unable to access EEPROM service;");
        }
        int word_read = 0;
        try
        {
            word_read = eeprom.readEEPROMbyteArray(area, byte_index, byteArray, num_of_bytes, password);

        }
        catch(RemoteException e)
        {
            throw new RuntimeException("Error reading EEPROM:"+e.toString());
        }
        return word_read;
    }

    public int readTDA7_SN()
    {
        return readEEPROM(idx_EEPROM_area_factory, offset_factory_EEPROM_serial_number, 0);
    }
    public void writeTDA7_SN(int sn)
    {
        writeEEPROM(idx_EEPROM_area_factory, offset_factory_EEPROM_serial_number, sn, EEPROM_PASSWORD_FACTORY);
    }
    public int readTDA7_auto_shutdown()
    {
        return readEEPROM(idx_EEPROM_area_tech, offset_technician_EEPROM_auto_shutdown, 0);
    }
    public void writeTDA7_auto_shutdown(int as_minutes)
    {
        writeEEPROM(idx_EEPROM_area_tech, offset_technician_EEPROM_auto_shutdown, as_minutes, EEPROM_PASSWORD_TECH);
    }
    public int readTDA7_terminalNumber()
    {
        return readEEPROM(idx_EEPROM_area_user, offset_factory_EEPROM_terminal_number, 0);
    }
    public void writeTDA7_terminalNumber(int tn)
    {
        writeEEPROM(idx_EEPROM_area_user, offset_factory_EEPROM_terminal_number, tn, 0);
    }

    public void writeTDA7_EEPROM_WORD(Enum_EEPROM_area area, int word_index, int word_value, int password)
    {
        writeEEPROM(index_from_area(area), word_index, word_value, password);
    }
    public int readTDA7_EEPROM_WORD(Enum_EEPROM_area area, int word_index, int password)
    {
        return readEEPROM(index_from_area(area), word_index, password);
    }
    public void writeTDA7_EEPROM_BYTES(Enum_EEPROM_area area, int byte_index, byte [] byteArray, int num_of_bytes, int password) throws RuntimeException
    {
        writeEEPROM(index_from_area(area), byte_index, byteArray, num_of_bytes, password);
    }
    public void readTDA7_EEPROM_BYTES(Enum_EEPROM_area area, int byte_index, byte [] byteArray, int num_of_bytes, int password) throws RuntimeException
    {
        readEEPROM(index_from_area(area), byte_index, byteArray, num_of_bytes, password);
    }

}

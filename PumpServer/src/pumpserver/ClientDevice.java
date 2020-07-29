/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pumpserver;

import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author sg
 */
public class ClientDevice {
    InetAddress     ipAddress;
    public int      ticks_passed=-1;
    public int      log_tick=-1;
    //settings for remote control
    public String   strID="undef";
    public String   flowRate="0";
    public String   revActivityCycle="0";
    public String   scaleFactor="0";
    public String   remote="undef";
    public String   direction="undef";
    public String   revPeriod="undef";
    
    //settings actually working in the pump(sent by the pump). In manual mode, they are used to keep track of manual settings
    public String   r_strID="undef";
    public String   r_flowRate="0";
    public String   r_revDutyCycle="0";
    public String   r_scaleFactor="0";
    public String   r_remote="undef";
    public String   r_direction="undef";
    public String   r_revPeriod="undef";
            
    FileWriter fw;  //each pump has its own log file  
    boolean online; //true if pump online
    public String   prevLogLine="";
    
    
   public ClientDevice(InetAddress address, String rawStr) {
        
       try {
            String parts[] = rawStr.trim().split(";");
            strID=parts[1];

            if (strID.startsWith("RPI1"))
            {
                flowRate="0";
                revActivityCycle="0";
                scaleFactor="0";
                remote="0";
                direction="0";
                revPeriod="0";
                ipAddress = address;
                ticks_passed = 0;
                online = true;
            }
            else
            {
                flowRate=parts[2];
                revActivityCycle=parts[3];
                scaleFactor=parts[4];
                remote=parts[5];
                direction=parts[6];
                revPeriod=parts[7];
                ipAddress = address;
                ticks_passed = 0;
                online = true;
           }
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
            LocalDateTime now = LocalDateTime.now();
            String filename = new String(dtf.format(now)+"-"+strID+".log");
            fw=new FileWriter(filename);
            fw.write("Date\tTime\tPumpIP\tPumpID\tFlowRate\tFlowScale\tRevPeriod\tRevDuration\tDir\tControl\n");
        } catch (IOException ex) {
            Logger.getLogger(ClientDevice.class.getName()).log(Level.SEVERE, null, ex);
        }
   }
   public void ParseRealSettings(String rawStr) {
        String parts[] = rawStr.trim().split(";");
        r_strID=parts[1];
        r_flowRate=parts[2];
        r_revDutyCycle=parts[3];
        r_scaleFactor=parts[4];
        r_remote=parts[5];
        r_direction=parts[6];
        r_revPeriod=parts[7];
        
   }
    
}


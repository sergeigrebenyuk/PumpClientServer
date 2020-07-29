/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pumpserver;

import java.awt.Color;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.JApplet;


/**
 *
 * @author sg  
 */


public class tickListener extends Thread {
 
    
    private DatagramSocket tick_socket;
    private boolean running;
    private byte[] buf = new byte[256];
    int _tick_port = 4472; 
    MainFrame GUI;
    PumpServer app;
    EchoServer echoServer;
    HashMap<String, ClientDevice> clientMap;
    int rpi_ticks_passed;
    
    public tickListener(MainFrame gui, HashMap<String, ClientDevice> pMap, PumpServer _app, EchoServer _echoServer) throws UnknownHostException, IOException {
            GUI = gui;
            app = _app;
            clientMap=pMap;
            tick_socket = new DatagramSocket(_tick_port);
            rpi_ticks_passed=0;
            echoServer = _echoServer;
    }
    
       
    public void run() {
        running = true;
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd\tHH:mm:ss");
            Timer timer = new Timer("PumpTimer");//create a new Timer
                timer.scheduleAtFixedRate(timerTask, 0, app.parTickTimerPeriod*1000);            
        while (running) {
           
            try {
                // get new packet...
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                tick_socket.receive(packet);
                //check if it is a new client client went online
                if ( packet.getPort()!= _tick_port ) continue;
                String str = new String(buf);
                
                if ( (str.startsWith("Client-tick")) ) {
                    InetAddress client_addr = packet.getAddress();
                    
                    ClientDevice client = clientMap.get(client_addr.getHostAddress());
                    if (client!=null) {
                        client.ticks_passed=0; 
                        client.online=true;
                        if (client.strID.startsWith("Pump"))
                        {
                            client.ParseRealSettings(str);
                            GUI.UpdateRealSettings(client);
                        }
                    
                        GUI.PopulateGUI(clientMap);

                        //if (++client.log_tick>app.parPumpLogPeriod){
                            client.log_tick=0;
                            LocalDateTime now = LocalDateTime.now();
                            String logLine = String.format("%s\t%s\t%s\t%s\t%s\t%s", client.r_flowRate,
                                    client.r_scaleFactor,client.r_revPeriod,client.r_revDutyCycle,client.r_direction,client.r_remote);

                            if (!logLine.contentEquals(client.prevLogLine))
                            {
                                client.fw.write(String.format("%s\t%s\t%s\t%s\n", dtf.format(now),client.ipAddress.getHostAddress(),client.strID,logLine));
                                client.fw.flush();
                                client.prevLogLine = logLine;
                            }
                        //}
                    }
                    else{//
                        ClientDevice pump=new ClientDevice(client_addr, str); 
                        clientMap.put(client_addr.getHostAddress(), pump);
                        GUI.PopulateGUI(clientMap);
                    }
                    
                }
//                if ( str.startsWith("Rpi-tick") ) {
//                    InetAddress client_addr = packet.getAddress();
//                        rpi_ticks_passed=0; 
//                        echoServer.rpi_online=true;
//                        //Logger.getLogger(tickListener.class.getName()).log(Level.INFO, "Rpi online");
//                        
//                 }
               
            } catch (IOException ex) {
                Logger.getLogger(tickListener.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        tick_socket.close();
        
    }
    
    
    //Start timer to watch online status of the pumps
    TimerTask timerTask = new TimerTask() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy/MM/dd\tHH:mm:ss");
        // The task will increment time (seconds) since the last "tick" message from client pumps
        @Override
        public void run() {

            for (Map.Entry<String, ClientDevice> entry : app.pumpMap.entrySet()) {
                ClientDevice client = app.pumpMap.get(entry.getKey().toString());
                if (++client.ticks_passed > app.parPumpTimeOut)
                {
                    client.ticks_passed=0;
                    client.online=false;
                    //if (++client.log_tick>parPumpLogPeriod){
                    //    client.log_tick=0;
                        LocalDateTime now = LocalDateTime.now();
                        String logLine = "Device is offline\n";
                        try {
                            if (!logLine.contentEquals(client.prevLogLine))
                            {
                                client.fw.write(String.format("%s\t%s\t%s\t%s\n", dtf.format(now),client.ipAddress.getHostAddress(),client.strID,logLine));
                                client.fw.flush();
                                client.prevLogLine = logLine;
                                
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(PumpServer.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    //}
                }
            }

            if (++rpi_ticks_passed > app.parPumpTimeOut)
                {
                    rpi_ticks_passed=0;
                    echoServer.rpi_online=false;
                    //}
                }
            app.echoServer.GUI.PopulateGUI(app.pumpMap);
        }
    };
      
}
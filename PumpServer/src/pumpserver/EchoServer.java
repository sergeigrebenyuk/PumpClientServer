/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pumpserver;

import java.awt.Color;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
//import java.util.HashMap;
import javax.swing.JApplet;


/**
 *
 * @author sg
 */


public class EchoServer extends Thread {
 
    private DatagramSocket socket;
    private boolean running;
    private byte[] buf = new byte[256];
    int _port = 4471; 
    MainFrame GUI;
    PumpServer app;
    boolean rpi_online;
    
    public EchoServer(MainFrame _gui, PumpServer _app) throws UnknownHostException, IOException {
            GUI = _gui;
            socket = new DatagramSocket(_port);
            rpi_online = false;
            app = _app;
        AdvertiseServer();
    }
    
    public List<InetAddress> listAllBroadcastAddresses() throws SocketException {
    List<InetAddress> broadcastList = new ArrayList<>();
    Enumeration<NetworkInterface> interfaces 
      = NetworkInterface.getNetworkInterfaces();
    while (interfaces.hasMoreElements()) {
        NetworkInterface networkInterface = interfaces.nextElement();
 
        if (networkInterface.isLoopback() || !networkInterface.isUp()) {
            continue;
        }
 
        networkInterface.getInterfaceAddresses().stream() 
          .map(a -> a.getBroadcast())
          .filter(Objects::nonNull)
          .forEach(broadcastList::add);
    }
    return broadcastList;
}
    public final void AdvertiseServer() throws SocketException, UnknownHostException, IOException{
        socket.setBroadcast(true);
        List<InetAddress> addr_list = listAllBroadcastAddresses();
        for (InetAddress addr : addr_list) 
        {
            byte[] buffer = "New-server;".getBytes();
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, addr, _port );
            socket.send(packet);
            Logger.getLogger(EchoServer.class.getName()).log(Level.INFO, addr.getHostAddress());
        }
        socket.setBroadcast(false);
    } 
    public void run() {
        running = true;
 
        while (running) {
           
            try {
                // get new packet...
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                //check if it is a new client pump went online
                if ( packet.getPort()!= _port ) continue;
                String str = new String(buf);
                Logger.getLogger(EchoServer.class.getName()).log(Level.INFO, "---> "+str);                

                if ( (str.startsWith("New-client")) ) {
                    InetAddress new_client_addr = packet.getAddress();
                    if (app.pumpMap.get(new_client_addr.getHostAddress())==null)
                    {
                        ClientDevice pump=new ClientDevice(new_client_addr, str); 
                        app.pumpMap.put(new_client_addr.getHostAddress(), pump);
                    }
                    GUI.PopulateGUI(app.pumpMap);

                    //Logger.getLogger(EchoServer.class.getName()).log(Level.INFO, "New pump is online : "+pump.ipAddress.getHostAddress()+pump.strID);                

                    //Send ack
                    byte[] ack = "Ack-reg;".getBytes();
                    packet = new DatagramPacket(ack, ack.length, new_client_addr, _port );
                    socket.send(packet);
                }
//                if ( (str.startsWith("Rpi-online;")) ) {
//                    InetAddress new_client_addr = packet.getAddress();
//                    
//                    Logger.getLogger(EchoServer.class.getName()).log(Level.INFO, "RPi board is online at "+new_client_addr.getHostAddress());                
//                    if (pumpMap.get(new_client_addr.getHostAddress())==null)
//                    {
//                        ClientDevice pump=new ClientDevice(new_client_addr, "RPI1"); 
//                        pumpMap.put(new_client_addr.getHostAddress(), pump);
//                    }
//                    rpi_online=true;
//                    GUI.PopulateGUI(pumpMap);
//                   
//                    //Send ack
//                    byte[] ack = "Ack-reg;".getBytes();
//                    packet = new DatagramPacket(ack, ack.length, new_client_addr, _port );
//                    socket.send(packet);
//                }
                if ( (str.startsWith("Params-ack")) ) {

                    ClientDevice pump=app.pumpMap.get(packet.getAddress().getHostAddress());
                    GUI.UpdateStatus(str.substring(0, str.lastIndexOf(";")));
                    pump.ParseRealSettings(str);
                    GUI.UpdateRealSettings(pump);
                }
                
               
                
            } catch (IOException ex) {
                Logger.getLogger(EchoServer.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        socket.close();
    }
    public  void UpdateClientSettings(String pars, InetAddress addr) {
        try {
            
            
            //String pars = "New-params;"+pump.strID+";"+pump.flowRate+";"+pump.revDutyCycle+";"+pump.scaleFactor+";"+pump.remote;
            byte[] buf1 = pars.getBytes();
            DatagramPacket packet = new DatagramPacket(buf1, buf1.length, addr, _port );
            socket.send(packet);
            
        } catch (IOException ex) {
            Logger.getLogger(EchoServer.class.getName()).log(Level.SEVERE, null, ex);
            GUI.sendSettings.setBackground(Color.red);
        }
        
    }; 
    
      
}
/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pumpserver;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javax.swing.JApplet;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 *
 * @author sg
 */


public class PumpServer extends JApplet {
    
    private static final int JFXPANEL_WIDTH_INT = 300;
    private static final int JFXPANEL_HEIGHT_INT = 250;
    //private static JFXPanel fxContainer;
    public static  MainFrame frame;
    public static EchoServer echoServer;
    public static tickListener tickServer;
    
    public static int parPumpTimeOut=3;
    public static int parPumpLogPeriod=1;
    public static int parTickTimerPeriod=2;
    
    public static HashMap<String, ClientDevice> pumpMap;
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            
            @Override
            public void run() {
                try {
                    try {
                        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
                    } catch (Exception e) {
                    }
                    pumpMap = new HashMap<String, ClientDevice>();
                    
                    MainFrame frame = new MainFrame();
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    
                    PumpServer applet = new PumpServer();
                    applet.init();
                    
                    //frame.setContentPane(applet.getContentPane());
                    
                    frame.pack();
                    frame.setLocationRelativeTo(null);
                    frame.setVisible(true);
                    // Start socket server to communicate with pumps
                    echoServer = new EchoServer(frame, applet);
                    frame.SetAppPntr(applet);
                    echoServer.start();
                    
                    tickServer = new tickListener(frame,pumpMap,applet,echoServer);
                    tickServer.start();
                    applet.start();
                } catch (IOException ex) {
                    Logger.getLogger(PumpServer.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        });
    }
        
    
}


///////////////////////////////////////////////////////////////////////////////
// Client part of the remote pump control system.
// Developed for custom-developed peristaltic pumps based on Arduino UNO Rev.2
// WiFi boards.
// For comments/suggestions, please mail to: sergeigrebenyuk@gmail.com
// (c)2019 Sergei Grebenyuk

#include <SPI.h>
#include <WiFiNINA.h>
#include <WiFiUdp.h>
#include <EEPROM.h>

///////////////////////////////////////////////////////////////////////////////
// Hard coded unique pump identifier and other parameters
#define PUMP_ID     "Pump-03"
#define TICK_PERIOD 2000

/////////////////////////////////////////////////////////////////////////////// 
// Hard coded credentials for WiFi network
#include "auth.h"
//char ssid[]   = "Wifi SSID";        
//char pass[]   = "Password";  

///////////////////////////////////////////////////////////////////////////////
// Defining GPIO pins that are hardwired to TMC2100 driver board, LEDs, knobs 
// and switches on the pump case. Please refer to 
// https://reprap.org/wiki/TMC2100 for documentation on TMC2100 board breakout

#define   pinLED         LED_BUILTIN
#define   pinEN         2
#define   pinM1         3
#define   pinM2         4
#define   pinM3         5
#define   pinSTP        6
#define   pinDIR        7

#define   pinLED_PWR    8   //used 4.8K resistor in series
#define   pinLED_REV    9   //4.8K
#define   pinLED_REM    10  //4.8K
#define   pinLED_RATE   11  //0.5K

#define   pinDIR_SW     12
#define   pinRate       A0

#define   rateMinFreq   50;
#define   rateMaxFreq   700;


// settings for a pump
typedef struct{
    String        strID= PUMP_ID;   //  UID
    unsigned int  flowRate= 0;      //  rotation speed in percents from min to max rpm for a given scale factor
    unsigned int  revActiveTime=0;  //  Duration in seconds of the intermittent reverse pumping
    unsigned int  scaleFactor=1;    //  Scale factor for the pumping speed. Resulting RPMs are defined by N * flowRate * scaleFactor (N depends on the specific mode of TMC2100 board)
    bool          remote=true;      //  true is pump is under remote control
    bool          dirFwd=true;      //  true is maind direction of pumping is set to Forward
    unsigned int  revPeriod=0;      //  Period in seconds of the reversing cycle
    unsigned int  dummy=0;      //  Period in seconds of the reversing cycle
    
  } TPumpSettings;


TPumpSettings pumpSettings;     // Stores settings for remote control mode
TPumpSettings manualSettings;   // Stores settings for manual control mode
enum state_type {ST_READY, ST_CMD_NEWPARAM, ST_CMD_PURGE};

WiFiUDP Udp;        // UDP socket for communicating parameters with Pump Server
WiFiUDP Udp_tick;   // UDP socket for communicating live ticks to Pump Server

state_type state;
bool active = true;
bool bRemoteMode = false;
float _rateLED = 0;
float _ratePUMP = 0;
float _oldRatePUMP = 0;
int c=0;
uint32_t tickRevPeriod = 0;
uint32_t tickKeepAlive = 0;
bool curDir;
bool oldDir;
int status = WL_IDLE_STATUS;
int keyIndex = 0;            // your network key Index number (needed only for WEP)

unsigned int _port = 4471;      // local port to listen on
unsigned int _tick_port = 4472;      // local port to listen on
IPAddress local_ip=IPAddress(0,0,0,0);
IPAddress broad_ip=IPAddress(0,0,0,0);
IPAddress server_ip=IPAddress(0,0,0,0);


void setup(){
  
  /////////////////////////////////////////////////////////////////////////////
  // Set up pin configurations
	// - TMC2100 related
  pinMode(pinEN, OUTPUT); digitalWrite(pinEN, HIGH);  // Disable motor while initializing
  pinMode( pinM1, OUTPUT ); digitalWrite( pinM1, LOW ); 
  //pinMode( pinM1, INPUT );     // set in tri-state
  pinMode( pinM2, OUTPUT ); digitalWrite( pinM2, LOW ); 
  pinMode( pinM2, INPUT );     // set in tri-state
  pinMode( pinM3, OUTPUT ); digitalWrite( pinM3, LOW ); 
  //digitalWrite( pinM3, LOW ); pinMode( pinM3, OUTPUT );  pinMode( pinM3, INPUT );
  pinMode(pinDIR, OUTPUT); digitalWrite(pinDIR, HIGH);
  pinMode(pinSTP, OUTPUT);
  
  // - Manual controls and LEDs related
  pinMode(pinLED_PWR, OUTPUT); digitalWrite(pinLED_PWR, HIGH);
  pinMode(pinLED_REV, OUTPUT); digitalWrite(pinLED_REV, HIGH); 
  pinMode(pinLED_RATE, OUTPUT); digitalWrite(pinLED_RATE, HIGH); 
  pinMode(pinLED_REM, OUTPUT);  digitalWrite(pinLED_REM, HIGH); 
  pinMode(pinDIR_SW,INPUT_PULLUP);

  /////////////////////////////////////////////////////////////////////////////
  // Turn off motor 
  tone(pinSTP, 0);

  /////////////////////////////////////////////////////////////////////////////
  // Read parameters from memory
  byte did_save=0;
  EEPROM.get(0, did_save); 
  if (did_save){
    EEPROM.get(sizeof(byte), pumpSettings); 
    EEPROM.get(sizeof(TPumpSettings), manualSettings); 
  }
  else
  {
    did_save = 1;
    EEPROM.put(0, did_save); 
    EEPROM.put(sizeof(byte), pumpSettings); 
    EEPROM.put(sizeof(TPumpSettings), manualSettings); 
  }

  /////////////////////////////////////////////////////////////////////////////
  // Set up debug channel and display stored settings
  Serial.begin(9600); while (!Serial) {}    
  char  ReplyBuffer[255];
  sprintf(ReplyBuffer,"Manual from EEPROM;%s;%u;%u;%u;%s;%s;%u;",PUMP_ID,manualSettings.flowRate,manualSettings.revActiveTime,manualSettings.scaleFactor,manualSettings.remote?"remote":"manual",manualSettings.dirFwd?"fwd":"rev",manualSettings.revPeriod);
  sprintf(ReplyBuffer,"Remote from EEPROM;%s;%u;%u;%u;%s;%s;%u;",PUMP_ID,pumpSettings.flowRate,pumpSettings.revActiveTime,pumpSettings.scaleFactor,pumpSettings.remote?"remote":"manual",pumpSettings.dirFwd?"fwd":"rev",pumpSettings.revPeriod);
  Serial.println(ReplyBuffer);
    
  /////////////////////////////////////////////////////////////////////////////
  // Conect to WiFi network and set up communication
  while (status != WL_CONNECTED) {
      status = WiFi.begin(ssid, pass);
      delay(2000);
  }
  WiFi.noLowPowerMode();
  local_ip = WiFi.localIP();
  Serial.print("Local IP: "); Serial.println(local_ip);
  
  // Calculate brroadcast address from local IP and subnet mask
  unsigned long bits = (unsigned long)WiFi.subnetMask() ^ 0xffffffff;
  unsigned long bcast = (unsigned long)local_ip | bits;
  broad_ip = bcast;

  // Broadcast handshake message
  Udp.beginMulticast(broad_ip,_port);
  char packetBuffer[255]; 
  sprintf(packetBuffer,"New-client;%s;%u;%u;%u;%s;%s;%u;",PUMP_ID,pumpSettings.flowRate,pumpSettings.revActiveTime,pumpSettings.scaleFactor,pumpSettings.remote?"remote":"manual",pumpSettings.dirFwd?"fwd":"rev",pumpSettings.revPeriod);
  Udp.beginPacket(broad_ip, _port);
  Udp.write(packetBuffer);
  Udp.endPacket();
  
  Udp_tick.begin(_tick_port);
  
  /////////////////////////////////////////////////////////////////////////////
  // Initialize time variables
  tickRevPeriod=millis();
  tickKeepAlive=millis();

  /////////////////////////////////////////////////////////////////////////////
  // Enable motor when initialization is done
  pinMode(pinEN, OUTPUT); digitalWrite(pinEN, LOW);
}


void updatePumpSettings()
{
  int val=0;
  /////////////////////////////////////////////////////////////////////////////
  // First, determine whether it is a remote or local control mode
  val=pumpSettings.remote?HIGH:LOW;
  digitalWrite(pinLED_REM, val); 
  
  /////////////////////////////////////////////////////////////////////////////
  // Manage settings depending on the control mode
  if (pumpSettings.remote) // REMOTE MODE
  {
    // get major flow direction and keep it
    curDir = pumpSettings.dirFwd;
    
    // update flow rate
    _ratePUMP = map(pumpSettings.flowRate,0,100,5,100*pumpSettings.scaleFactor);
    _rateLED = map(pumpSettings.flowRate,0,100,5,2000);
  }
  else // MANUAL MODE
  {
    // get major flow direction
    val=digitalRead(pinDIR_SW);
   
    manualSettings.dirFwd = (val==HIGH)?true:false;
    curDir = manualSettings.dirFwd;
    
    //  update flow rate
    int rd = analogRead(pinRate);
    manualSettings.flowRate=map(rd,0,1023,5,100);
    _ratePUMP=manualSettings.flowRate*pumpSettings.scaleFactor;
    _rateLED = map(rd,0,1023,5,2000);
    
  }
  /////////////////////////////////////////////////////////////////////////////
  // Implement intermittent reverse of the flow
  bool dir;
  if (pumpSettings.revActiveTime > pumpSettings.revPeriod) pumpSettings.revActiveTime=pumpSettings.revPeriod;
  if (millis()-tickRevPeriod > 1000*(pumpSettings.revPeriod-pumpSettings.revActiveTime)){
      //it is time to start reversing
      dir = !curDir;
  }
  if (millis()-tickRevPeriod > 1000*pumpSettings.revPeriod){
      //new period started, switch off reverse and reset tickRevPeriod
      tickRevPeriod=millis();
      dir = curDir;
  }
  if (_ratePUMP!=_oldRatePUMP)  
  {
    tone(pinSTP, _ratePUMP);
    _oldRatePUMP = _ratePUMP;
  }
  if (oldDir!=dir)  //
  {
    digitalWrite(pinDIR, dir?LOW:HIGH);
    digitalWrite(pinLED_REV, dir?LOW:HIGH);    
    oldDir=dir;
  }  
    
  // update indicator status
  if (++c> (2050-_rateLED)/100) // the numbers are picked so that the blinking rate would reasonably well indicate the rate (not taking scaling factor into account)
  {
    c=0; active=!active;
    digitalWrite(pinLED_RATE, active ? HIGH : LOW);  
  }
}


void checkServer()
{
//check response from the server
  char packetBuffer[255]; //buffer to hold incoming packet
   
  int packetSize = Udp.parsePacket();
  if (packetSize) {
      Serial.println(Udp.remotePort());
      if (Udp.remotePort()!=_port) return;
      
      // read the packet into packetBufffer
      int len = Udp.read(packetBuffer, 255); 
      //Udp.flush();  
      if (len > 0) {  packetBuffer[len] = 0;   }
      
      String str = String(packetBuffer);
      
      if (str.startsWith("Ack-reg")){ //we got registration acqnowlegment from server
          //save its IP
          server_ip = Udp.remoteIP();  
          
          Serial.print("Server IP: ");
          Serial.println(server_ip);
      }
      if (str.startsWith("New-server")){
          server_ip = Udp.remoteIP();  
          sprintf(packetBuffer,"New-client;%s;%u;%u;%u;%s;%s;%u;",PUMP_ID,pumpSettings.flowRate,pumpSettings.revActiveTime,pumpSettings.scaleFactor,pumpSettings.remote?"remote":"manual",pumpSettings.dirFwd?"fwd":"rev",pumpSettings.revPeriod);
          Udp.beginPacket(server_ip, _port);
          Udp.write(packetBuffer);
          Udp.endPacket();
          
      }
      if (str.startsWith("New-params")){
          //Serial.println(str);
          int ind1 = str.indexOf(';');  //finds location of first ;
          String command = str.substring(0, ind1);   //captures first data String
          
          int ind2 = str.indexOf(';', ind1+1 );   //finds location of second ,
          String sid  = str.substring(ind1+1, ind2);   //captures second data String
          
          //if (sid.equals(pumpSettings.strID)) //if settings were for our pump, continue...
          {
            int ind3 = str.indexOf(';', ind2+1 );
            pumpSettings.flowRate = str.substring(ind2+1, ind3).toInt(); 
            
            int ind4 = str.indexOf(';', ind3+1 );
            pumpSettings.revActiveTime = str.substring(ind3+1,ind4).toInt();  
            manualSettings.revActiveTime=pumpSettings.revActiveTime;
            
            int ind5 = str.indexOf(';', ind4+1 );
            pumpSettings.scaleFactor = str.substring(ind4+1,ind5).toFloat(); 
            manualSettings.scaleFactor=pumpSettings.scaleFactor;
            
            int ind6 = str.indexOf(';', ind5+1 );
            pumpSettings.remote = str.substring(ind5+1,ind6).equals("remote"); 
            manualSettings.remote = pumpSettings.remote;
            
            int ind7 = str.indexOf(';', ind6+1 );
            pumpSettings.dirFwd = str.substring(ind6+1,ind7).equals("fwd"); 

            int ind8 = str.indexOf(';', ind7+1 );
            pumpSettings.revPeriod = str.substring(ind7+1,ind8).toInt(); 
            manualSettings.revPeriod = pumpSettings.revPeriod;
                        
            EEPROM.put(sizeof(byte), pumpSettings); 
            EEPROM.put(sizeof(TPumpSettings), manualSettings); 

            Serial.println("------");
            Serial.println(pumpSettings.revActiveTime);
            Serial.println(pumpSettings.revPeriod);
            
            sprintf(packetBuffer,"Params-ack;%s;%u;%u;%u;%s;%s;%u;",PUMP_ID,pumpSettings.flowRate,pumpSettings.revActiveTime,pumpSettings.scaleFactor,pumpSettings.remote?"remote":"manual",pumpSettings.dirFwd?"fwd":"rev",pumpSettings.revPeriod);
            Udp.beginPacket(server_ip, _port);
            Udp.write(packetBuffer);
            Udp.endPacket();
            
          }
      }
  }
    
    
}

void updateConnectionState()
{
    if ((millis()-tickKeepAlive) < TICK_PERIOD) return;

    tickKeepAlive = millis();
    //send a tick
    if (server_ip!=IPAddress(0,0,0,0))
    {
      char  ReplyBuffer[255];
      if (pumpSettings.remote)
      sprintf(ReplyBuffer,"Client-tick;%s;%u;%u;%u;%s;%s;%u;",PUMP_ID,pumpSettings.flowRate,pumpSettings.revActiveTime,pumpSettings.scaleFactor,pumpSettings.remote?"remote":"manual",pumpSettings.dirFwd?"fwd":"rev",pumpSettings.revPeriod);
          else
      sprintf(ReplyBuffer,"Client-tick;%s;%u;%u;%u;%s;%s;%u;",PUMP_ID,manualSettings.flowRate,manualSettings.revActiveTime,manualSettings.scaleFactor,manualSettings.remote?"remote":"manual",manualSettings.dirFwd?"fwd":"rev",manualSettings.revPeriod);
      Udp_tick.beginPacket(server_ip, _tick_port);
      Udp_tick.write(ReplyBuffer);
      Udp_tick.endPacket();
    }
    
}


void loop(){

  checkServer();
  updateConnectionState();
  updatePumpSettings();
  delay(50);
}

#include <Xmpp.h>
#include <WiFlyHQ.h>
#include <SensorProtocol.h>
#include <TinkerKit.h>
#include <SPI.h>
#include <Ports.h>

char* recipient = "luke@arduino";
XMPP xmpp("arduino","arduino","sensor","arduino", recipient);

char* server = "54.22.34.73";
int serverPort = 5222;
WiFly wifly;
const char mySSID[] = "ut-public";
const char myPassword[] = "";

int sensorTypes[] = {1, // temperature. 
2, //hall sensor
3, //light sensor
4, //battery status
5, //cpu usage.
6,// accelerometerx
7,//accelerometery
8//bandWidth
//9, //x gro rate
//10,// y gro
//11//y gro rate
};
int locationID = 1;
int length = sizeof(sensorTypes) / sizeof(int);
SensorProtocol protocol(locationID);
 // a variable to store theaccelerometer's x value
int xAxisValue = 0;           
// a variable to store theaccelerometer's y value
int yAxisValue = 0; 
float bandW = 0.0; 
//Same with two leds
//TKLed accxLed(O0), accyLed(O1);

TKLed red(O0);
TKLed yellow(O4);
TKLed green(O2);
TKLed blue(O3);
TKThermistor thermistor(I0);
TKHallSensor hall(I1);
TKLightSensor light(I2);
//Create "accelerometer" object of
//TKAccelerometer class.
TKAccelerometer accelerometer(I3, I4);
// creating the object 'gyro' that belongs to the 'TKGyro' class 
// using the 4x amplified module, insert the TK_4X costant.
TKGyro gyro(I6, I7, TK_X4);

unsigned long reportStep = 30; // seconds
unsigned long currentTime;
bool sendData = false;

ISR(WDT_vect) { Sleepy::watchdogEvent(); }

void setup(){
  red.on();
  Serial.begin(9600);
  Serial1.begin(9600);
  Serial1.println("Serial1 started");
  xmpp.setSerial(&Serial1);
  xmpp.setClient(&wifly);
  protocol.setSensors(sensorTypes, length);
  getConnected();
  getConnectedWithServer();
}

void getConnected() {
   while(!wifly.begin(&Serial, &Serial1)) {
    Serial1.println("Failed to start wifly");
    delay(1000);
  }
  
  /* Join wifi network if not already associated */
  if (!wifly.isAssociated()) {
    /* Setup the WiFly to connect to a wifi network */
    Serial1.println("Joining network");
    wifly.setSSID(mySSID);
    wifly.setPassphrase(myPassword);
    wifly.enableDHCP();
    
    while(!wifly.join()) {
      Serial1.println("Join failed");
    } 
    Serial1.println("Joined wifi network");
  } 
  else {
    Serial1.println("Already joined network");
  }
  Serial1.println("WiFly ready");

  wifly.setDeviceID("Wifly-TCP");
  wifly.setIpProtocol(WIFLY_PROTOCOL_TCP);

  if (wifly.isConnected()) {
    Serial1.println("Old connection active. Closing");
    wifly.close();
  }
  wifly.save();
}

void getConnectedWithServer() {
  while(!wifly.open(server,serverPort, true)){
    Serial1.println("TCP Connection failed");
    delay(1000);
  }
  // let propely initialize the connection
  delay(2000);
  red.off();
  yellow.on();
  Serial1.println("connected");

  getConnectedWithXMPP();
}

void getConnectedWithXMPP(){
  while(!xmpp.connect()){
    Serial1.println("XMPP Stream negotiation failed");
    delay(1000);
    Serial1.println("Retrying..");
  }
  Serial1.println("Xmpp connection established");
  yellow.off();   
  green.on();
}

void loop(){
  if(!wifly.isConnected()) {
    sendData = false;
    xmpp.releaseConnection();
    green.off();
    yellow.off();
    red.on();
    // reconnect
    getConnectedWithServer();
  } 
  else if(!xmpp.getConnected()) {
    sendData = false;
    green.off();
    yellow.on();
    // reconnect
    getConnectedWithXMPP();  
  } 
  else {
    xmpp.handleIncoming(); 
    sendData = xmpp.getRecAvailable();
  }

  if(sendData) {
    if (currentTime == 0 || millis() - currentTime > (reportStep * 1000)) {
      //&& millis() - currentTime > (reportStep*1000)) {
      float tm = thermistor.readCelsius();
      float hs = hall.read();
      float ldr = light.read();
      
      flicker(&blue);
      currentTime = millis(); 
      // read the both joystick axis values:
  xAxisValue = accelerometer.readX();  
  yAxisValue = accelerometer.readY(); 
  
  protocol.addValue(1, tm);
  protocol.addValue(2, hs);
  protocol.addValue(3, ldr);
  protocol.addValue(4, readVoltage());
  protocol.addValue(5, 100.0);
  protocol.addValue(6, xAxisValue);
  protocol.addValue(7, yAxisValue);
  protocol.addValue(8, bandW);
      Message message = protocol.createMessage();
      xmpp.sendMessage(recipient, message.message, "chat");
      
    }
  } else {
    flicker(&yellow);
    
  }
}

void flicker(TKLed *led){
  led->on();
  delay(50);
  led->off(); 
}

float readVoltage(){
  float volts = (analogRead(I9)/4.092) / 10;
   delay(1000);
  return volts;
}












#include <Ports.h>
#include <WiFlyHQ.h>
#include <SensorProtocol.h>
#include <TinkerKit.h>
#include <Wire.h>
#include <aJSON.h>
#include <MemoryFree.h>
#define NUM_SAMPLES 10
//Timing 
ISR(WDT_vect) { Sleepy::watchdogEvent(); }


char* server = "54.82.220.45";
char* utping = "193.40.36.81";

int serverPort = 8080;


int sum = 0;                    // sum of samples taken
unsigned char sample_count = 0; // current sample number
float voltage = 0.0;  // calculated voltage
uint32_t bitrate = 0.0;
int avgWakeUpTime = 5;

WiFly wifly;

/* Change these to match your WiFi network */
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

void setup(){
  //analogReference(DEFAULT);
  red.on();
  Serial.begin(9600);
  Serial1.begin(9600);
  Serial1.println("Serial1 started");
  protocol.setSensors(sensorTypes, length);
  getConnected();
  getConnectedWithServer();
}

void getConnected() {
  while(!wifly.begin(&Serial, &Serial1)) {
    Serial1.println("Failed to start wifly");
    delay(1000);
  }
  
  wifly.setJoin(1);
  wifly.setWakeTimer(0);
  wifly.setRate(54000000);
  wifly.setFlushSize(1420);
  wifly.setTxPower(10);
  
  

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

  red.off();
  green.off();
  yellow.on();
  
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
    wifly.close(); 
    delay(1000);
  }
  
  
  green.on();
  yellow.off();
  red.off();
  Serial1.println("connected");
}

void loop(){
  if(!wifly.isConnected()) {
    green.off();
    yellow.off();
    red.on();
   
    getConnectedWithServer();
  }
  
  //Bandwidth Code;
  long start = millis();
  delay(1000);
  long duration = 0;
  
  if(wifly.ping(utping)){ 
  duration = millis() - start;
  bandW = 64.0f / (duration / 100.0f);
  }
  
  
  blue.on();
  postData();
  int idle = readResponse();
  blue.off();
  
  Serial1.print("Data sent at ");
  Serial1.println(millis());
  
  Serial1.print(">>> Available memory= ");
  Serial1.println(freeMemory());
  
  if (idle > 65) { // Max is 65 seconds for now..
    idle = 65;
  }
  
  idle = idle - avgWakeUpTime;  
  if (idle > 0) {
    wifly.close();
    delay(100);
    wifly.sleep();
    delay(100);
    Sleepy::loseSomeTime(idle * 1000);
  }
}

void postData() {
  
  
  float tm = thermistor.readCelsius();
  float hs = hall.read();
  float ldr = light.read();
  
  
  
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
 
  
  Message msg = protocol.createMessage();
  wifly.println("POST /data/ HTTP/1.1");
  wifly.print("Host: ");
  wifly.println(server);
  wifly.println("Connection: close");
  wifly.println("Content-Type: application/json");
  wifly.print("Content-Length: ");
  wifly.println(msg.length);
  wifly.println();
  wifly.println(msg.message);
}

float readVoltage(){
  float volts = (analogRead(I9)/4.092) / 10;
   delay(1000);
  return volts;
}

int readResponse() {
  Serial1.println("readResponse");
  long startMillis = millis();      
  boolean message = false;
  String line = "";
  long contentLength = 0;
  long read = 0;
  while (!(message && read >= contentLength) && (millis() - startMillis) < 5000) {
    while (wifly.available() > 0) {
      char ch = wifly.read();      
      line += ch;
      startMillis = millis(); 
      if (message) {
        read += 1;  
      } else if (line.endsWith("\r\n")) {
        if (line == "\r\n") {
          message = true;
        } else if (line.length() > 16 && line.startsWith("Content-Length: ")) {
          contentLength = line.substring(16, line.length() - 2).toInt();
        }
        line = "";
      }
    }
  }
  
  int idleTime = 0;
  if (line != "") {
    Serial1.print("Json: ");
    Serial1.println(line);
    char buf[line.length() + 1];
    line.toCharArray(buf, sizeof(buf));
    aJsonObject *root = aJson.parse(buf);
    aJsonObject *idle = aJson.getObjectItem(root, "idle");
    if (idle->type == aJson_Int) {
      idleTime = idle->valueint;
    }
    aJson.deleteItem(root);
  } else {
    Serial1.println("Invalid message received");
  }
  return idleTime; 
}

void flicker(TKLed *led) {
  led->on();
  delay(50);
  led->off(); 
}

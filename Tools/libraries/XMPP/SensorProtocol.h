#include <Stream.h>

class Message {
    public:
        char* message;
        int length;

    Message(char *msg, int len) {
        message = msg;
        length = len;
    }
};

class SensorProtocol {
public:
	SensorProtocol(int location);
	void addValue(int sensorType, float value);
	Message createMessage();
	void setSensors(int sensors[], int length);
	
private:
	int location;
	int *sensorTypes;
	float *values;	
	int sensorLength;
};

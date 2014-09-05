#include <Stream.h>
#include <HttpParser.h>
#include <aJSON.h>

int readResponse(Stream *s) {
  //Serial1.println("readResponse");
  long startMillis = millis();
  boolean messageRead = false;
  String msg = "";
  boolean json = false;
  String line = "";
  int state = LINE;
  while (!messageRead && (millis() - startMillis) < 5000) {
    while (s->available() > 0) {  
      char ch = s->read();
      switch (state) {
        case LINE:
          if (ch == '\r') {
            //Serial1.print("!!! LINE: ");
            //Serial1.println(line);
            state = CR;
          }
          break;
        case CR:
          if (ch = '\n') {
            line = "";
            state = NEWLINE;
          }  
          break;
        case NEWLINE:
          if (ch == '\n') {
            // Body starts
            state = BODY;
          }
          break;
        case CONTENT_LENGTH:
          break;
        case BODY:
          if (ch == '{') {
            json = true;
            //msg += ch;
          } else if (ch == '}') {
            messageRead = true;
            //msg += ch;
          } else if (json) {
            //msg += ch;
          }
        break;
      }
    }
  }
  
  if (json) {
    char buf[msg.length() + 1];
    msg.toCharArray(buf, sizeof(buf));
    aJsonObject *root = aJson.parse(buf);
    aJsonObject *idle = aJson.getObjectItem(root, "idle");
    if (idle->type == aJson_Int) {
      //Serial1.print("Idle time= ");
      //Serial1.println(idle->valueint); 
      return idle->valueint;
    }
  }
  //Serial1.println("Invalid message received");
  return 0; 
}

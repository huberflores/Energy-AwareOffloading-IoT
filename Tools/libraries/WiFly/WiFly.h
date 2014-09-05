#ifndef __WIFLY_H__
#define __WIFLY_H__

#include "SpiUart.h"

#include "WiFlyDevice.h"

#include "WiFlyClient.h"
#include "WiFlyServer.h"

// Join modes
#define WEP_MODE false
#define WPA_MODE true

// Configuration options
#define WIFLY_BAUD 1
#define WAKE_TIMER 2
#define SLEEP_TIMER 3
#define WLAN_JOIN 4
#define WLAN_AUTH 5
#define COMM_SIZE 6
#define WLAN_RATE 7
#define SLEEP 8

// TODO: Don't make this extern
// TODO: How/whether to allow sending of arbitrary data/commands over serial?
// NOTE: Only the WiFly object is intended for external use
extern SpiUartDevice SpiSerial;

extern WiFlyDevice WiFly;

#endif


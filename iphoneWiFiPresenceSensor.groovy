/**
 *  iPhone WiFi Presence Sensor v1.03
 *
 *  Copyright 2019 Joel Wetzel
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Release Notes:
 *  v1.01:  Fixed a bug that could happen if a user updated from an older version of the code, but didn't click "Save Preferences".
 */

/*Scott Barton added the ability to turn it on and off manually (switch capability) as well as auto-turn off (this acts as an auto-update if they are still connected).  
Use this to control a Virtual Presence Sensor using my Virtual Switch Universal Device Type uDTH and select Presence Sensor in Preferences.
https://github.com/sab0276/Hubitat/blob/main/virtualSwitchUDTH.groovy
*/

import groovy.json.*
	
metadata {
	definition (name: "WiFi Presence Sensor", namespace: "joelwetzel", author: "Joel Wetzel") {
		capability "Refresh"
		capability "Sensor"
        	capability "Presence Sensor"
        	capability "Switch"        
	}

	preferences {
		section {
			input (
				type: "string",
				name: "ipAddress",
				title: "iPhone IP Address",
				required: true				
			)
			input (
				type: "number",
				name: "timeoutMinutes",
				title: "Timeout Minutes",
				description: "Approximate number of minutes without a response before deciding the device is away/offline.",
				required: true,
				defaultValue: 3
			)
			input (
				type: "bool",
				name: "enableDebugLogging",
				title: "Enable Debug Logging?",
				required: true,
				defaultValue: true
			)
            		input (
				type: "bool",
				name: "enableDevice",
				title: "Enable Device?",
				required: true,
				defaultValue: true
			)
            		input (
             		   name: "autoOff", 
             		   type: "enum", 
             		   description: "", 
                	   title: "Enable auto off", 
                	   options: [[0:"Disabled"],[1:"1m"],[2:"2m"],[3:"3m"], [5:"5m"],[10:"10m"],[15:"15m"], [20:"20m"],[30:"30m"],[60:"1h"],[120:"2h"]],
               		   defaultValue: 0
            		)
		}
	}
}


def log(msg) {
	if (enableDebugLogging) {
		log.debug(msg)	
	}
}


def installed () {
	log.info "${device.displayName}.installed()"
    updated()
}


def updated () {
	log.info "${device.displayName}.updated()"
    
    state.tryCount = 0
    
	unschedule()
    
    if (enableDevice) {
        runEvery1Minute(refresh)		// Option 1: test it every minute.  Have a 10 second timeout on the requests.
        state.triesPerMinute = 1

	//schedule("*/15 * * * * ? *", refresh)    // Option 2: run every 15 seconds, but now we have a 10 second timeout on the requests.
        //state.triesPerMinute = 4
    }
    
    runIn(2, refresh)				// But test it once, right after we install or update it too.
}


def ensureStateVariables() {
    if (state.triesPerMinute == null) {
        state.triesPerMinute = 1
    }
}

def off() {
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "presence", value: "not present")
}

def on() {
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "presence", value: "present")
    if (autoOff.toInteger()>0){
        runIn(autoOff.toInteger()*60, off)
    }
}

def refresh() {
	log "${device.displayName}.refresh()"

	state.tryCount = state.tryCount + 1
    
    ensureStateVariables()
    
    if ((state.tryCount / state.triesPerMinute) > (timeoutMinutes < 1 ? 1 : timeoutMinutes) && device.currentValue('presence') != "not present") {
        def descriptionText = "${device.displayName} is OFFLINE";
        log descriptionText
        sendEvent(name: "presence", value: "not present", linkText: deviceName, descriptionText: descriptionText)
        sendEvent(name: "switch", value: "off")
    }
    
	if (ipAddress == null || ipAddress.size() == 0) {
		return
	}
	
	asynchttpGet("httpGetCallback", [
		uri: "http://${ipAddress}/",
        timeout: 10
	]);
}


def httpGetCallback(response, data) {
	log "${device.displayName}: httpGetCallback(${groovy.json.JsonOutput.toJson(response)}, data)"
	
	if (response != null && response.status == 408 && response.errorMessage.contains("Connection refused")) {
        log "${device.displayName}: httpGetCallback(The following 'connection refused' result means that the hub was SUCCESSFUL in discovering the phone on the network: ${groovy.json.JsonOutput.toJson(response)}, data)"
		state.tryCount = 0
		
		if (device.currentValue('presence') != "present") {
			def descriptionText = "${device.displayName} is ONLINE";
			log descriptionText
			sendEvent(name: "presence", value: "present", linkText: deviceName, descriptionText: descriptionText)
            sendEvent(name: "switch", value: "on")
            if (autoOff.toInteger()>0){
                runIn(autoOff.toInteger()*60, off)
            }
		}
	}
    else {
        log "${device.displayName}: httpGetCallback(The following result means that the hub was UNSUCCESSFUL in discovering the phone on the network: ${groovy.json.JsonOutput.toJson(response)}, data)"

    }
}

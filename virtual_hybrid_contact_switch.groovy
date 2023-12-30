/**
 *  Virtual Hybrid Contact Switch
 *
 *  MIT License
 *
 *  Copyright (c) 2023 This Old Smart Home
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 *
 */

public static String version()      {  return "v1.5.1"  }
public static String name()         {  return "Virtual Hybrid Contact Switch"  }
public static String driverInfo()   {  return "<p style=\"text-align:center\"></br><strong><a href='https://thisoldsmarthome.com' target='_blank'>This Old Smart Home</a></strong> (tosh)</br>${name()}<br/><em>${version()}</em></p>"  }

metadata {
    definition (name: name(), namespace: "tosh", author: "John Goughenour") {
        capability "Sensor"
        capability "Contact Sensor"
        capability "Switch"
    
        command "open"
        command "close"
        command "clearMqttBrokerSettings"
        command "sendMqttOn"
        command "sendMqttOff"
        command "setMqttBroker", [[name: "MQTT Broker*", type: "STRING", description: "Enter MQTT Broker IP and Port e.g. server_IP:1883"],
                                  [name: "MQTT User*", type: "STRING", description: "Enter MQTT broker username"]]
        command "setMessageTopic", [[name: "Message Topic", type: "STRING", description: "Enter MQTT Message Topic to publish to"]]
    }
    preferences {
        input(name: "mqttPassword", type: "password", title: "<b>MQTT Password</b>", description: "The password for your MQTT Broker.</br>Enter Password", required: false)
        input name: "autoOff", type: "number", title: "<b>Auto Off</b>", 
            description: "Automatically turn off device after x many seconds </br>Default: 0 (Disabled)",
            defaultValue: 0, required: false
        input(name: "debugLogging", type: "bool", title: "<b>Enable Debug Logging</b>", defaultValue: "false", description: "", required: false)
        input name:"about", type: "text", title: "<b>About Driver</b>", 
            description: "A hybrid contact sensor/swtich that can be used in Hubitat to trigger Alexa Routines. ${driverInfo()}"
    }
}

def installed(){
  initialize()
}

def updated(){
  initialize()
}

def initialize(){
  sendEvent(name: "switch", value: "off")
  sendEvent(name: "contact", value: "closed")  
}

def open(){
    sendEvent(name: "contact", value: "open")
    sendEvent(name: "switch", value: "on")
}

def close(){
    sendEvent(name: "contact", value: "closed")
    sendEvent(name: "switch", value: "off")
}

def on(){
    if(debugLogging) log.debug "${device.displayName} is turned on"
    sendEvent(name: "switch", value: "on")
    sendEvent(name: "contact", value: "open")
    sendMqttCommand("on")
    if( autoOff > 0 ) runIn (autoOff, off)
}

def off(){
    if(debugLogging) log.debug "${device.displayName} is turned off"
    sendEvent(name: "switch", value: "off")
    sendEvent(name: "contact", value: "closed")
    sendMqttCommand("off")
}

def sendMqttOn(){
  sendMqttCommand("on")
}

def sendMqttOff(){
  sendMqttCommand("off")
}

def setMqttBroker(broker, user) {
    if(debugLogging) log.debug "${device.displayName} is setting up mqtt broker variables"
    updateDataValue("MQTT_Broker", "${broker}")
    updateDataValue("MQTT_User", user)
}

def clearMqttBrokerSettings() {
    if(debugLogging) log.debug "${device.displayName} is clearing MQTT Broker data"
    removeDataValue("MQTT_Broker")
    removeDataValue("MQTT_User")      
}

def setMessageTopic(topic) {
    if(debugLogging) log.debug "${device.displayName} is setting up mqtt message topic variable"
    if(topic) updateDataValue("MQTT_Message_Topic", "${topic}") else removeDataValue("MQTT_Message_Topic")
}

def sendMqttCommand(cmnd) {
    if(getDataValue("MQTT_Broker") && getDataValue("MQTT_User")) {
        try {
            if(debugLogging) log.debug "${device.displayName} settting up MQTT Broker"
            interfaces.mqtt.connect("tcp://${getDataValue("MQTT_Broker")}", "hubitat_messages", getDataValue("MQTT_User"), mqttPassword)
            
            if(debugLogging) log.debug "${device.displayName} is sending message: ${message}"
            interfaces.mqtt.publish(getDataValue("MQTT_Message_Topic"), cmnd, 2, false)                      
        } catch(Exception e) {
            log.error "${device.displayName} unable to connect to the MQTT Broker ${e}"
        }
    } else log.error "${device.displayName} MQTT Broker and MQTT User are not set"
    interfaces.mqtt.disconnect()
}

// parse events and messages
def mqttClientStatus(message) {
    switch(message) {
        case ~/.*Connection succeeded.*/:
            if(debugLogging) 
                log.debug "MQTT Client Status: ${device.displayName} successfully connected to MQTT Broker"
            break
        case ~/.*Error.*/:
            if(debugLogging) 
                log.debug "MQTT Client Status: ${device.displayName} unable to connect to MQTT Broker - ${message}"
            break
        default:
            if(debugLogging) log.info "MQTT Client Status ${device.displayName}: unknown status - ${message}"
    }
}
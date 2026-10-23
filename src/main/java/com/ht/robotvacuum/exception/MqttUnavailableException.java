package com.ht.robotvacuum.exception;

public class MqttUnavailableException extends RuntimeException {

    public MqttUnavailableException() {
        super("MQTT service is unavailable");
    }

    public MqttUnavailableException(Throwable cause) {
        super("MQTT service is unavailable", cause);
    }
}

package com.ht.robotvacuum.mqtt;

import com.ht.robotvacuum.config.MqttProperties;
import com.ht.robotvacuum.dto.mqtt.MqttCommandDTO;
import com.ht.robotvacuum.exception.MqttUnavailableException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

@Component
public class MqttPublisher {

    private final ObjectProvider<MqttProperties> properties;
    private final ObjectProvider<MessageChannel> outputChannel;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public MqttPublisher(ObjectProvider<MqttProperties> properties,
            @Qualifier("mqttOutputChannel") ObjectProvider<MessageChannel> outputChannel,
            ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.outputChannel = outputChannel;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void publishCommand(long robotId, MqttCommandDTO command) {

        if (robotId <= 0 || command == null || command.commandId() == null || command.action() == null
                || command.issuedAt() == null || command.expiresAt() == null
                || !command.expiresAt().isAfter(command.issuedAt())) {
            throw new IllegalArgumentException("Invalid robot command");
        }

        MqttProperties config = properties.getIfAvailable();
        MessageChannel channel = outputChannel.getIfAvailable();
        if (config == null || !config.isEnabled() || channel == null) {
            throw new MqttUnavailableException();
        }

        String payload = objectMapper.writeValueAsString(command);
        if (!clock.instant().isBefore(command.expiresAt())) {
            throw new MqttUnavailableException();
        }

        var message = MessageBuilder.withPayload(payload)
                .setHeader(MqttHeaders.TOPIC, config.robotTopic(Long.toString(robotId), "commands"))
                .setHeader(MqttHeaders.QOS, 1)
                .setHeader(MqttHeaders.RETAINED, false)
                .build();
        try {
            if (!channel.send(message, config.getConnectionTimeoutSeconds() * 1000L)) {
                throw new MqttUnavailableException();
            }
        } catch (MessagingException ex) {
            // Keep broker/payload details out of the HTTP error response. A failed send may be ambiguous.
            throw new MqttUnavailableException(ex);
        }
    }
}

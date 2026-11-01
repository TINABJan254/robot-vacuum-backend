package com.ht.robotvacuum.mqtt;

import com.ht.robotvacuum.config.MqttProperties;
import com.ht.robotvacuum.dto.mqtt.MqttTelemetryDTO;
import com.ht.robotvacuum.dto.mqtt.MqttTaskEventDTO;
import com.ht.robotvacuum.exception.ResourceNotFoundException;
import com.ht.robotvacuum.service.RobotStateService;
import com.ht.robotvacuum.service.RobotTaskEventService;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.cfg.EnumFeature;

import java.util.regex.Pattern;

@Slf4j
public class MqttInboundHandler implements MessageHandler {

    private final RobotStateService states;
    private final RobotTaskEventService tasks;
    private final ObjectReader telemetryReader;
    private final ObjectReader taskEventReader;
    private final Pattern robotTopic;

    public MqttInboundHandler(MqttProperties properties, ObjectMapper mapper, RobotStateService states,
                              RobotTaskEventService tasks) {
        this.states = states;
        this.tasks = tasks;
        this.robotTopic = Pattern.compile(Pattern.quote(properties.getTopicPrefix() + "/robots/")
                + "([1-9][0-9]*)/(telemetry|availability|acknowledgements|alerts|task-events)");
        // Keep MQTT parsing strict without changing the shared HTTP mapper.
        var strictMapper = mapper.rebuild()
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        this.telemetryReader = strictMapper.readerFor(MqttTelemetryDTO.class);
        this.taskEventReader = strictMapper.readerFor(MqttTaskEventDTO.class);
    }

    @Override
    public void handleMessage(Message<?> message) {
        try {
            Object topic = message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
            if (!(topic instanceof String name)) {
                throw new IllegalArgumentException("Missing topic");
            }
            var match = robotTopic.matcher(name);
            if (!match.matches()) {
                throw new IllegalArgumentException("Invalid robot topic");
            }
            long robotId = Long.parseLong(match.group(1));
            String type = match.group(2);
            if (!type.equals("telemetry") && !type.equals("task-events")) {
                log.debug("MQTT event is not implemented yet (robotId={}, type={})", robotId, match.group(2));
                return;
            }
            if (Boolean.TRUE.equals(message.getHeaders().get(MqttHeaders.RECEIVED_RETAINED))) {
                throw new IllegalArgumentException("Robot events must not be retained");
            }
            ObjectReader reader = type.equals("telemetry") ? telemetryReader : taskEventReader;
            Object event;
            if (message.getPayload() instanceof String json) {
                event = reader.readValue(json);
            } else if (message.getPayload() instanceof byte[] json) {
                event = reader.readValue(json);
            } else {
                throw new IllegalArgumentException("Invalid payload type");
            }
            if (type.equals("telemetry")) {
                states.acceptTelemetry(robotId, (MqttTelemetryDTO) event);
            } else {
                tasks.accept(robotId, (MqttTaskEventDTO) event);
            }
        } catch (JacksonException | ConstraintViolationException | IllegalArgumentException
                 | ResourceNotFoundException ex) {
            // Do not expose raw payloads or device-supplied exception messages in logs.
            log.warn("Discarded invalid MQTT message ({})", ex.getClass().getSimpleName());
        }
        // Infrastructure failures propagate to the adapter's mqttErrorChannel.
    }
}

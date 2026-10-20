package com.ht.robotvacuum.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.util.Set;

@Validated
@Getter
@Setter
@ConfigurationProperties("app.mqtt")
public class MqttProperties {

    private boolean enabled = true;

    @NotBlank
    private String brokerUrl;

    @NotBlank
    private String clientId;

    @Positive
    private int connectionTimeoutSeconds = 10;

    @Positive
    @Max(65535)
    private int keepAliveSeconds = 30;

    @Positive
    private int recoveryIntervalMs = 5000;

    @NotBlank
    @Pattern(regexp = "[^/+#\\s\\x00]+(?:/[^/+#\\s\\x00]+)*")
    private String topicPrefix;

    @AssertTrue(message = "MQTT broker URL must use tcp or ssl with a host and port, without credentials or a path")
    public boolean isBrokerUrlValid() {
        if (brokerUrl == null) {
            return false;
        }
        try {
            URI uri = URI.create(brokerUrl);
            return uri.getScheme() != null && Set.of("tcp", "ssl").contains(uri.getScheme())
                    && uri.getHost() != null && uri.getPort() > 0 && uri.getPort() <= 65535
                    && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
                    && (uri.getPath() == null || uri.getPath().isEmpty());
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public String robotTopic(String robotId, String suffix) {
        return topicPrefix + "/robots/" + robotId + "/" + suffix;
    }
}

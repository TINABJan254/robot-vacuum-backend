package com.ht.robotvacuum.config;

import org.eclipse.paho.client.mqttv3.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(OutputCaptureExtension.class)
class MqttConfigTests {

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner().withPropertyValues(
                "app.mqtt.broker-url=tcp://localhost:1883",
                "app.mqtt.client-id=backend-test",
                "app.mqtt.topic-prefix=robot-vacuum/v1");
    }

    @Test
    void bindsExistingYamlKeysAndCreatesAnonymousConnectionOptions() {
        runner().withUserConfiguration(PropertiesConfig.class).run(context -> {
            assertNull(context.getStartupFailure());
            var properties = context.getBean(MqttProperties.class);
            var options = new MqttConfig().mqttConnectOptions(properties);
            assertArrayEquals(new String[]{"tcp://localhost:1883"}, options.getServerURIs());
            assertNull(options.getUserName());
            assertNull(options.getPassword());
            assertEquals(MqttConnectOptions.MQTT_VERSION_3_1_1, options.getMqttVersion());
            assertTrue(options.isCleanSession());
            assertTrue(options.isAutomaticReconnect());
            assertEquals(5000, options.getMaxReconnectDelay());
            assertEquals(10, options.getConnectionTimeout());
            assertEquals(30, options.getKeepAliveInterval());
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "broker-url=", "broker-url=http://localhost:1883", "broker-url=tcp://localhost",
            "broker-url=tcp://localhost:70000", "broker-url=tcp://localhost:1883/path",
            "broker-url=tcp://user:password@localhost:1883", "client-id=",
            "connection-timeout-seconds=0", "keep-alive-seconds=-1", "keep-alive-seconds=65536",
            "recovery-interval-ms=0", "topic-prefix=", "topic-prefix=robot-vacuum/#",
            "topic-prefix=robot-vacuum/+", "topic-prefix=/robot-vacuum", "topic-prefix=robot-vacuum/"
    })
    void rejectsInvalidConfigurationBeforeConnecting(String property) {
        runner().withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues("app.mqtt." + property)
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void mqttCanBeDisabledWithoutBrokerSettings() {
        new ApplicationContextRunner().withUserConfiguration(MqttConfig.class)
                .withPropertyValues("app.mqtt.enabled=false").run(context -> {
                    assertNull(context.getStartupFailure());
                    assertFalse(context.containsBean("mqttInboundAdapter"));
                    assertFalse(context.containsBean("mqttOutboundHandler"));
                    assertFalse(context.containsBean("mqttClientFactory"));
                });
    }

    @Test
    void channelsSubscribeReceiveAndPublishWithDistinctClientsWithoutABroker(CapturedOutput output)
            throws Exception {
        var properties = new MqttProperties();
        properties.setBrokerUrl("tcp://localhost:1883");
        properties.setClientId("backend-test");
        properties.setTopicPrefix("robot-vacuum/v1");
        var options = new MqttConfig().mqttConnectOptions(properties);
        var factory = mock(MqttPahoClientFactory.class);
        var inbound = mock(IMqttAsyncClient.class);
        var outbound = mock(IMqttAsyncClient.class);
        var token = mock(IMqttDeliveryToken.class);
        var callback = new AtomicReference<MqttCallbackExtended>();
        when(factory.getConnectionOptions()).thenReturn(options);
        when(factory.getAsyncClientInstance(isNull(), eq("backend-test-in"))).thenReturn(inbound);
        when(factory.getAsyncClientInstance(isNull(), eq("backend-test-out"))).thenReturn(outbound);
        doAnswer(call -> {
            callback.set(call.getArgument(0));
            return null;
        }).when(inbound).setCallback(any(MqttCallback.class));
        when(inbound.connect(any())).thenAnswer(call -> {
            callback.get().connectComplete(false, "tcp://localhost:1883");
            return token;
        });
        when(inbound.isConnected()).thenReturn(true);
        when(token.getGrantedQos()).thenReturn(new int[]{0, 1, 1, 1, 1});
        when(inbound.subscribe(any(String[].class), any(int[].class), any(IMqttMessageListener[].class)))
                .thenReturn(token);
        when(outbound.connect(any())).thenReturn(token);
        when(outbound.isConnected()).thenReturn(true);
        when(outbound.disconnect()).thenReturn(token);
        when(outbound.publish(anyString(), any(MqttMessage.class), isNull(), any(IMqttActionListener.class)))
                .thenReturn(token);

        runner().withUserConfiguration(MqttConfig.class)
                .withBean("testClientFactory", MqttPahoClientFactory.class, () -> factory,
                        definition -> definition.setPrimary(true))
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    var adapter = context.getBean(MqttPahoMessageDrivenChannelAdapter.class);
                    assertTrue(adapter.isRunning());
                    assertArrayEquals(new String[]{
                            "robot-vacuum/v1/robots/+/telemetry",
                            "robot-vacuum/v1/robots/+/availability",
                            "robot-vacuum/v1/robots/+/acknowledgements",
                            "robot-vacuum/v1/robots/+/alerts",
                            "robot-vacuum/v1/robots/+/task-events"
                    }, adapter.getTopic());
                    verify(inbound).subscribe(eq(adapter.getTopic()), eq(new int[]{0, 1, 1, 1, 1}),
                            any(IMqttMessageListener[].class));

                    String incomingTopic = "robot-vacuum/v1/robots/123/telemetry";
                    adapter.messageArrived(incomingTopic, new MqttMessage("private-payload".getBytes(StandardCharsets.UTF_8)));
                    assertTrue(output.getOut().contains("Received MQTT message on topic " + incomingTopic));
                    assertFalse(output.getOut().contains("private-payload"));

                    var channel = context.getBean("mqttOutputChannel", MessageChannel.class);
                    String topic = "robot-vacuum/v1/robots/123/commands";
                    assertTrue(channel.send(MessageBuilder.withPayload("{\"action\":\"STOP\"}")
                            .setHeader(MqttHeaders.TOPIC, topic).build()));
                    var message = ArgumentCaptor.forClass(MqttMessage.class);
                    verify(outbound).publish(eq(topic), message.capture(), isNull(), any(IMqttActionListener.class));
                    assertEquals(1, message.getValue().getQos());
                    assertFalse(message.getValue().isRetained());
                    assertEquals("{\"action\":\"STOP\"}",
                            new String(message.getValue().getPayload(), StandardCharsets.UTF_8));
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(MqttProperties.class)
    static class PropertiesConfig {
    }
}

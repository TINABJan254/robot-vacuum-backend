package com.ht.robotvacuum.config;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableIntegration
@EnableConfigurationProperties(MqttProperties.class)
@ConditionalOnProperty(prefix = "app.mqtt", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MqttConfig {

    @Bean
    public MqttConnectOptions mqttConnectOptions(MqttProperties properties) {
        var options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setServerURIs(new String[]{properties.getBrokerUrl()});
        options.setConnectionTimeout(properties.getConnectionTimeoutSeconds());
        options.setKeepAliveInterval(properties.getKeepAliveSeconds());
        options.setCleanSession(true);
        // Integration 7 uses Paho recovery. Delay grows from 1s up to this configured limit.
        options.setAutomaticReconnect(true);
        options.setMaxReconnectDelay(properties.getRecoveryIntervalMs());
        // The current broker allows anonymous access: leave username and password unset.
        return options;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory(MqttConnectOptions mqttConnectOptions) {
        var factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(mqttConnectOptions);
        return factory;
    }

    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel mqttOutputChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel mqttErrorChannel() {
        return new DirectChannel();
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter mqttInboundAdapter(
            MqttProperties properties, MqttPahoClientFactory mqttClientFactory,
            @Qualifier("mqttInputChannel") MessageChannel inputChannel,
            @Qualifier("mqttErrorChannel") MessageChannel errorChannel) {
        var adapter = new MqttPahoMessageDrivenChannelAdapter(
                properties.getClientId() + "-in", mqttClientFactory,
                properties.robotTopic("+", "telemetry"),
                properties.robotTopic("+", "availability"),
                properties.robotTopic("+", "acknowledgements"),
                properties.robotTopic("+", "alerts"),
                properties.robotTopic("+", "task-events"));
        adapter.setQos(0, 1, 1, 1, 1);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setCompletionTimeout(properties.getConnectionTimeoutSeconds() * 1000L);
        adapter.setOutputChannel(inputChannel);
        adapter.setErrorChannel(errorChannel);
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutputChannel")
    public MqttPahoMessageHandler mqttOutboundHandler(
            MqttProperties properties, MqttPahoClientFactory mqttClientFactory) {
        var handler = new MqttPahoMessageHandler(properties.getClientId() + "-out", mqttClientFactory);
        handler.setAsync(false);
        handler.setDefaultQos(1);
        handler.setDefaultRetained(false);
        handler.setCompletionTimeout(properties.getConnectionTimeoutSeconds() * 1000L);
        // The caller supplies the target robot topic using MqttHeaders.TOPIC.
        return handler;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public MessageHandler mqttInboundHandler() {
        // Connection setup only. Replace this subscriber with business routing in the next stage.
        return message -> log.info("Received MQTT message on topic {}",
                message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC));
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttErrorChannel")
    public MessageHandler mqttErrorHandler() {
        // Avoid logging payloads or connection credentials embedded in exceptions.
        return message -> log.warn("MQTT inbound processing failed ({})",
                message.getPayload().getClass().getSimpleName());
    }
}

package com.ht.robotvacuum.config;

import com.ht.robotvacuum.realtime.TelemetryHandshakeInterceptor;
import com.ht.robotvacuum.realtime.TelemetryWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final TelemetryWebSocketHandler telemetryHandler;
    private final TelemetryHandshakeInterceptor telemetryHandshake;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Default origin policy (same-origin only) is kept: native apps send no Origin header and are
        // accepted, while cross-site browser pages are rejected (no cross-site WebSocket hijacking).
        registry.addHandler(telemetryHandler, "/api/v1/robots/*/telemetry/ws")
                .addInterceptors(telemetryHandshake);
    }
}

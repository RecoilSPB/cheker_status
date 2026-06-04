package ru.spb.reshenie.chekerstatus.config.http;

import ru.spb.reshenie.chekerstatus.web.service.WebSocketLiveUpdatePublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class LiveWebSocketConfiguration implements WebSocketConfigurer {

    private final WebSocketLiveUpdatePublisher liveUpdatePublisher;

    public LiveWebSocketConfiguration(WebSocketLiveUpdatePublisher liveUpdatePublisher) {
        this.liveUpdatePublisher = liveUpdatePublisher;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveUpdatePublisher, "/ws/live");
    }
}

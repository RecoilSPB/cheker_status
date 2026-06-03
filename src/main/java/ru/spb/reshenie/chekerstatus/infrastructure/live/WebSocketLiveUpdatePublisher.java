package ru.spb.reshenie.chekerstatus.infrastructure.live;

import ru.spb.reshenie.chekerstatus.application.live.LiveUpdatePublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class WebSocketLiveUpdatePublisher extends TextWebSocketHandler implements LiveUpdatePublisher {

    private static final Logger log = LoggerFactory.getLogger(WebSocketLiveUpdatePublisher.class);

    private final ConcurrentMap<String, WebSocketSession> sessions = new ConcurrentHashMap<String, WebSocketSession>();
    private final ObjectMapper objectMapper;

    public WebSocketLiveUpdatePublisher(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        log.debug("Live update WebSocket transport error: session={}, error={}",
                session.getId(), exception.getMessage());
    }

    @Override
    public void publish(String type, Map<String, Object> payload) {
        if (sessions.isEmpty()) {
            return;
        }

        TextMessage message = new TextMessage(toJson(type, payload));
        for (WebSocketSession session : sessions.values()) {
            if (!session.isOpen()) {
                sessions.remove(session.getId());
                continue;
            }
            try {
                session.sendMessage(message);
            } catch (IOException e) {
                sessions.remove(session.getId());
                log.debug("Cannot send live update: session={}, type={}, error={}",
                        session.getId(), type, e.getMessage());
            }
        }
    }

    private String toJson(String type, Map<String, Object> payload) {
        Map<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("type", type);
        message.put("createdAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        if (payload != null) {
            message.putAll(payload);
        }
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize live update", e);
        }
    }
}

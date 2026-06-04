package ru.spb.reshenie.chekerstatus.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketLiveUpdatePublisherTest {

    @Test
    void publishesEventToConnectedWebSocketSession() throws Exception {
        WebSocketLiveUpdatePublisher publisher = new WebSocketLiveUpdatePublisher(new ObjectMapper());
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);

        publisher.afterConnectionEstablished(session);
        publisher.publish("dashboard.changed", Collections.<String, Object>emptyMap());

        ArgumentCaptor<TextMessage> message = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(message.capture());
        assertThat(message.getValue().getPayload()).contains("\"type\":\"dashboard.changed\"");
    }

    @Test
    void dropsSessionWhenWebSocketSendFailsWithRuntimeException() throws Exception {
        WebSocketLiveUpdatePublisher publisher = new WebSocketLiveUpdatePublisher(new ObjectMapper());
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);
        doThrow(new IllegalStateException("TEXT_PARTIAL_WRITING"))
                .when(session).sendMessage(any(TextMessage.class));

        publisher.afterConnectionEstablished(session);

        assertThatCode(() -> publisher.publish("dashboard.changed", Collections.<String, Object>emptyMap()))
                .doesNotThrowAnyException();
        publisher.publish("dashboard.changed", Collections.<String, Object>emptyMap());

        verify(session, times(1)).sendMessage(any(TextMessage.class));
    }
}

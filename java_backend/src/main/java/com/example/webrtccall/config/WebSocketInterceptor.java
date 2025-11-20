package com.example.webrtccall.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;

import java.security.Principal;

public class WebSocketInterceptor implements ChannelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketInterceptor.class);

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null) {
            StompCommand command = accessor.getCommand();
            String sessionId = accessor.getSessionId();
            
            if (StompCommand.CONNECT.equals(command)) {
                String userId = accessor.getFirstNativeHeader("userId");
                logger.info("WebSocket CONNECT request received - SessionId: {}, UserId: {}", sessionId, userId);
                
                if (userId != null) {
                    accessor.getSessionAttributes().put("userId", userId);
                    accessor.setUser(() -> userId);
                    logger.info("Successfully authenticated user {} with session {}", userId, sessionId);
                } else {
                    logger.warn("CONNECT request received without userId header for session {}", sessionId);
                }
            } else if (StompCommand.SUBSCRIBE.equals(command)) {
                String destination = accessor.getDestination();
                logger.info("WebSocket SUBSCRIBE request - SessionId: {}, Destination: {}", sessionId, destination);
            } else if (StompCommand.SEND.equals(command)) {
                String destination = accessor.getDestination();
                logger.debug("WebSocket SEND request - SessionId: {}, Destination: {}", sessionId, destination);
            } else if (StompCommand.DISCONNECT.equals(command)) {
                logger.info("WebSocket DISCONNECT request - SessionId: {}", sessionId);
            }
        }
        
        return message;
    }
}


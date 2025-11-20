package com.example.webrtccall.config;

import com.example.webrtccall.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
public class WebSocketEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);

    @Autowired
    private RoomService roomService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        // Check if session attributes exist before accessing them
        String userId = null;
        if (headerAccessor.getSessionAttributes() != null) {
            userId = (String) headerAccessor.getSessionAttributes().get("userId");
        }
        
        logger.info("WebSocket connection established - SessionId: {}, UserId: {}", sessionId, userId);
        
        if (userId != null) {
            logger.info("User {} successfully connected with session {}", userId, sessionId);
        } else {
            logger.warn("WebSocket connected but userId not found in session attributes for session {}", sessionId);
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        
        // Check if session attributes exist before accessing them
        String userId = null;
        if (headerAccessor.getSessionAttributes() != null) {
            userId = (String) headerAccessor.getSessionAttributes().get("userId");
        }
        
        logger.info("WebSocket disconnection event - SessionId: {}, UserId: {}", sessionId, userId);
        
        if (userId != null) {
            logger.info("User {} disconnecting, removing from rooms", userId);
            roomService.leaveRoom(userId);
            logger.info("Successfully removed user {} from all rooms", userId);
        } else {
            logger.warn("WebSocket disconnected but userId not found in session attributes for session {}", sessionId);
        }
    }
}


package com.example.webrtccall.controller;

import com.example.webrtccall.dto.SignalMessage;
import com.example.webrtccall.service.RoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.List;

@Controller
public class WebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketController.class);

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private RoomService roomService;

    @MessageMapping("/signal")
    public void handleSignal(@Payload SignalMessage message) {
        String type = message.getType();
        String userId = message.getUserId();
        String roomId = message.getRoomId();
        String targetUserId = message.getTargetUserId();

        logger.info("Received signal message - Type: {}, UserId: {}, RoomId: {}, TargetUserId: {}", 
                type, userId, roomId, targetUserId);

        switch (type) {
            case "join-room":
                logger.info("User {} joining room {}", userId, roomId);
                roomService.joinRoom(roomId, userId);
                // Notify other users in the room
                List<String> otherUsers = roomService.getOtherUsersInRoom(userId);
                logger.info("Notifying {} other users in room {} about user {} joining", 
                        otherUsers.size(), roomId, userId);
                for (String otherUser : otherUsers) {
                    SignalMessage notification = new SignalMessage();
                    notification.setType("user-joined");
                    notification.setRoomId(roomId);
                    notification.setUserId(userId);
                    messagingTemplate.convertAndSendToUser(otherUser, "/queue/signal", notification);
                    logger.debug("Sent 'user-joined' notification to user: {}", otherUser);
                }
                logger.info("Successfully processed join-room request for user {} in room {}", userId, roomId);
                break;

            case "leave-room":
                logger.info("User {} leaving room {}", userId, roomId);
                roomService.leaveRoom(userId);
                // Notify other users in the room
                List<String> remainingUsers = roomService.getOtherUsersInRoom(userId);
                logger.info("Notifying {} remaining users in room {} about user {} leaving", 
                        remainingUsers.size(), roomId, userId);
                for (String otherUser : remainingUsers) {
                    SignalMessage notification = new SignalMessage();
                    notification.setType("user-left");
                    notification.setRoomId(roomId);
                    notification.setUserId(userId);
                    messagingTemplate.convertAndSendToUser(otherUser, "/queue/signal", notification);
                    logger.debug("Sent 'user-left' notification to user: {}", otherUser);
                }
                logger.info("Successfully processed leave-room request for user {} in room {}", userId, roomId);
                break;

            case "offer":
                logger.info("Received offer from user {} to target user {} in room {}", 
                        userId, targetUserId, roomId);
                if (targetUserId != null) {
                    messagingTemplate.convertAndSendToUser(targetUserId, "/queue/signal", message);
                    logger.info("Successfully forwarded offer from user {} to target user {}", userId, targetUserId);
                } else {
                    logger.warn("Offer received without targetUserId from user {}", userId);
                }
                break;

            case "answer":
                logger.info("Received answer from user {} to target user {} in room {}", 
                        userId, targetUserId, roomId);
                if (targetUserId != null) {
                    messagingTemplate.convertAndSendToUser(targetUserId, "/queue/signal", message);
                    logger.info("Successfully forwarded answer from user {} to target user {}", userId, targetUserId);
                } else {
                    logger.warn("Answer received without targetUserId from user {}", userId);
                }
                break;

            case "ice-candidate":
                logger.debug("Received ICE candidate from user {} to target user {} in room {}", 
                        userId, targetUserId, roomId);
                if (targetUserId != null) {
                    messagingTemplate.convertAndSendToUser(targetUserId, "/queue/signal", message);
                    logger.debug("Successfully forwarded ICE candidate from user {} to target user {}", 
                            userId, targetUserId);
                } else {
                    logger.warn("ICE candidate received without targetUserId from user {}", userId);
                }
                break;

            default:
                logger.warn("Unknown signal message type: {} from user {}", type, userId);
                break;
        }
    }
}


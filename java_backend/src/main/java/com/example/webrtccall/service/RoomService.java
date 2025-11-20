package com.example.webrtccall.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoomService {
    
    private static final Logger logger = LoggerFactory.getLogger(RoomService.class);
    
    // Room ID -> Set of User IDs
    private final Map<String, Set<String>> rooms = new ConcurrentHashMap<>();
    
    // User ID -> Room ID
    private final Map<String, String> userRooms = new ConcurrentHashMap<>();
    
    public synchronized void joinRoom(String roomId, String userId) {
        logger.info("User {} joining room {}", userId, roomId);
        rooms.computeIfAbsent(roomId, k -> {
            logger.info("Creating new room: {}", roomId);
            return ConcurrentHashMap.newKeySet();
        }).add(userId);
        userRooms.put(userId, roomId);
        
        Set<String> usersInRoom = rooms.get(roomId);
        logger.info("User {} successfully joined room {}. Total users in room: {}", 
                userId, roomId, usersInRoom.size());
        logger.debug("Users in room {}: {}", roomId, usersInRoom);
    }
    
    public synchronized void leaveRoom(String userId) {
        String roomId = userRooms.remove(userId);
        if (roomId != null) {
            logger.info("User {} leaving room {}", userId, roomId);
            Set<String> users = rooms.get(roomId);
            if (users != null) {
                users.remove(userId);
                logger.info("User {} removed from room {}. Remaining users: {}", 
                        userId, roomId, users.size());
                if (users.isEmpty()) {
                    rooms.remove(roomId);
                    logger.info("Room {} is now empty and has been removed", roomId);
                }
            }
        } else {
            logger.warn("Attempted to remove user {} from room, but user was not in any room", userId);
        }
    }
    
    public Set<String> getUsersInRoom(String roomId) {
        Set<String> users = new HashSet<>(rooms.getOrDefault(roomId, Collections.emptySet()));
        logger.debug("Retrieved {} users from room {}", users.size(), roomId);
        return users;
    }
    
    public String getRoomForUser(String userId) {
        String roomId = userRooms.get(userId);
        logger.debug("User {} is in room: {}", userId, roomId);
        return roomId;
    }
    
    public List<String> getOtherUsersInRoom(String userId) {
        String roomId = userRooms.get(userId);
        if (roomId == null) {
            logger.debug("User {} is not in any room", userId);
            return Collections.emptyList();
        }
        Set<String> users = rooms.get(roomId);
        List<String> otherUsers = new ArrayList<>();
        for (String user : users) {
            if (!user.equals(userId)) {
                otherUsers.add(user);
            }
        }
        logger.debug("Found {} other users in room {} for user {}", otherUsers.size(), roomId, userId);
        return otherUsers;
    }
}


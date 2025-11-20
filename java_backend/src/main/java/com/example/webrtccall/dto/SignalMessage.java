package com.example.webrtccall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SignalMessage {
    private String type; // offer, answer, ice-candidate, join-room, leave-room
    private String roomId;
    private String userId;
    private String targetUserId;
    private Object data; // SDP or ICE candidate data
}


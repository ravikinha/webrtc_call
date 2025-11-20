package com.example.webrtccall.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IceCandidate {
    private String candidate;
    private String sdpMid;
    private Integer sdpMLineIndex;
}


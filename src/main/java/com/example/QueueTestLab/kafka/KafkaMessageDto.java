package com.example.QueueTestLab.kafka;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class KafkaMessageDto {

    private String queueType;
    private String userId;
}

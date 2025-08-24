package com.example.QueueTestLab.kafka;

import lombok.Getter;

@Getter
public class KafkaMessageDto {

    private String queueType;

    public KafkaMessageDto(String queueType) {
        this.queueType = queueType;
    }

}

package com.example.QueueTestLab.kafka;

import com.example.QueueTestLab.sse.SseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class KafkaConsumerService {

    private final ObjectMapper objectMapper;
    private final SseService sseService;

    @KafkaListener(topics = "test_queueing_system", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String message, ConsumerRecord<String, String> record) {

        try {
            KafkaMessageDto messageDto = objectMapper.readValue(message, KafkaMessageDto.class);
            String queueType = messageDto.getQueueType();
            String userId = messageDto.getUserId();

            sseService.broadcastRankOrConfirm(queueType);
            log.info("Kafka consume - queueType: {} , userId : {}", queueType, userId);
        } catch (Exception e) {
            log.error("Kafka 메시지 consume 실패", e);
        }
    }
}

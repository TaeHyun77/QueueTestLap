package com.example.QueueTestLab.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
@Service
public class KafkaProducerService {

    @Value("${queue.event.topic.name}")
    private String topicName;

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void sendMessage(String queueType, String userId) {
        try {
            KafkaMessageDto message = new KafkaMessageDto(queueType, userId);
            String json = objectMapper.writeValueAsString(message);

            CompletableFuture<?> future = kafkaTemplate.send(topicName, queueType, json);

            future.whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Kafka produce success");
                } else {
                    log.error("Kafka produce fail", ex);
                }
            });
        } catch (JsonProcessingException e) {
            log.error("직렬화 실패: {}", e.getMessage());
        }
    }
}

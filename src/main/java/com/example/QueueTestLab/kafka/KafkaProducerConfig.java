package com.example.QueueTestLab.kafka;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
@Configuration
public class KafkaProducerConfig {

    private final Environment env;

    private Map<String, Object> producerConfig() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                env.getProperty("spring.kafka.producer.bootstrap-servers"));
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);

        // 안정성 설정
        props.put(ProducerConfig.ACKS_CONFIG, "all"); // 모든 ISR 브로커 확인 후 ACK
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true); // 중복 방지 + 순서 보장
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1); // 순서 보장
        props.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE); // 무제한 재시도
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000); // 재시도 포함 전송 타임아웃 (기본 2분)
        props.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100L); // 재시도 간격

        return props;
    }

    private ProducerFactory<String, String> producerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfig());
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}

package com.example.QueueTestLab.sse;

import com.example.QueueTestLab.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
@Component
public class SseEmitters {

    private final QueueService queueService;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void addEmitter(String sseKey, SseEmitter emitter) {
        emitters.put(sseKey, emitter);
        log.info("{}님이 참가하였습니다.", sseKey.split(":")[1]);

        // 클라이언트가 SSE 연결을 정상적으로 종료했을 때 호출
        emitter.onCompletion(() -> emitters.remove(sseKey));

        // 서버에 설정된 타임아웃 시간( 기본 30초 ~ 60초 )이 지나도 클라이언트로 응답을 못 보낼 경우 호출
        emitter.onTimeout(emitter::complete);
    }

    public void sendTo(String sseKey, String eventName, Object data) {
        SseEmitter emitter = emitters.get(sseKey);

        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventName)
                        .data(data));
            } catch (IOException e) {
                emitters.remove(sseKey);
            }
        }
    }

    public void broadcastRankOrConfirm(String queueType) {
        Set<String> sseKeys = emitters.keySet();

        for (String sseKey : sseKeys) {

            String userId = sseKey.split(":")[1];

            boolean isAllow = queueService.isExistUserInWaitOrAllow(userId, queueType, "allow");

            // 참가열에 있다면 'confirm' 메세지 보냄
            if (isAllow) {

                this.sendTo(sseKey, "confirm", Map.of(
                        "event", "confirm",
                        "user_Id", userId
                ));

            // 대기열에 있다면 rank 값 반환
            } else {

                Long rank = queueService.searchUserRanking(userId, queueType, "wait");
                log.info("rank : {}", rank);

                if (rank != null && rank > 0) {
                    this.sendTo(sseKey, "update", Map.of(
                            "event", "update",
                            "rank", rank
                    ));
                    log.info("update 전송완료");
                }
            }
        }
    }
}

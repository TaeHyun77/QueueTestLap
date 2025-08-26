package com.example.QueueTestLab.sse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
@RestController
public class SseController { // SSE 연결 요청을 처리하는 컨트롤러

    private final SseService sseService;

    // "/connect" endpoint를 통해 클라이언트가 연결을 시도 - 기본 타임아웃 : 30초 ~ 1분 ( 원하는 경우 생성자에 타임아웃(ms)을 명시 가능 )
    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> connect(@RequestParam String userId, @RequestParam String queueType) {

        // 새로운 SSE 세션( Emitter ) 생성
        SseEmitter emitter = new SseEmitter(60 * 60 * 1000L); // 1시간

        String sseKey = queueType + ":" + userId;
        sseService.addEmitter(sseKey, emitter);

        // sse 세션이 연결되면 'connected!' 메세지 전송
        sseService.sendTo(queueType, "connect", "connected !");

        return ResponseEntity.ok(emitter);
    }
}

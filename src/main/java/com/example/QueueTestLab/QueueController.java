package com.example.QueueTestLab;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/user")
@RestController
public class QueueController {

    private final QueueService queueService;

    // 대기열 등록
    @PostMapping("/enter")
    public Long registerUser(@RequestParam(name = "user_id") String userId,
                                @RequestParam(name = "queueType", defaultValue = "reserve") String queueType){

        Instant now = Instant.now();
        long enterTimestamp = now.getEpochSecond() * 1000000000L + now.getNano();

        return queueService.registerUserToWaitQueue(userId, queueType, enterTimestamp);
    }

    // 대기열 or 참가열에서 사용자 존재 유무 확인
    @GetMapping("/isExist")
    public boolean isExistUserInQueue(@RequestParam(name = "user_id") String userId,
                                            @RequestParam(name = "queueType", defaultValue = "reserve") String queueType,
                                            @RequestParam(name = "queueCategory") String queueCategory) {
        return queueService.isExistUserInWaitOrAllow(userId, queueType, queueCategory);
    }

    // 대기열 or 참가열에서 사용자 순위 조회
    @GetMapping("/search/ranking")
    public Long searchUserRanking(@RequestParam(name = "user_id") String userId,
                                        @RequestParam(name = "queueType", defaultValue = "reserve") String queueType,
                                        @RequestParam(name = "queueCategory") String queueCategory) {
        return queueService.searchUserRanking(userId, queueType, queueCategory);
    }

    // 대기열 or 참가열에서 사용자 제거
    @DeleteMapping("/cancel")
    public void cancelUser(@RequestParam(name = "user_id") String userId,
                                 @RequestParam(name = "queueType", defaultValue = "reserve") String queueType,
                                 @RequestParam(name = "queueCategory") String queueCategory) {
        queueService.cancelWaitUser(userId, queueType, queueCategory);
    }

    // 새로고침 시 대기열 후순위 재등록
    @PostMapping("/reEnter")
    public void reEnterQueue(@RequestParam(name = "user_id") String user_id,
                                   @RequestParam(name = "queueType", defaultValue = "reserve") String queueType) {

        log.info("새로고침 호출");
        queueService.reEnterWaitQueue(user_id, queueType);
    }

    // 대기열 상위 count명을 참가열 이동
    @PostMapping("/allow")
    public Long allowUser(@RequestParam(name = "queueType", defaultValue = "reserve") String queueType,
                             @RequestParam(name = "count") Long count) {
        return queueService.allowUser(queueType, count);
    }

    // 토큰 유효성 확인
    @GetMapping("/isValidateToken")
    public boolean isAccessTokenValid(@RequestParam(name = "user_id") String userId,
                                            @RequestParam(name = "queueType", defaultValue = "reserve") String queueType,
                                            @RequestParam(name = "token") String token) {
        return queueService.isAccessTokenValid(userId, queueType, token);
    }

    // 쿠키 토큰 저장
    @GetMapping("/createCookie")
    public ResponseEntity<String> sendCookie(@RequestParam(name = "user_id") String userId,
                                              @RequestParam(defaultValue = "reserve") String queueType,
                                              HttpServletResponse response) {
        return queueService.sendCookie(userId, queueType, response);
    }
}

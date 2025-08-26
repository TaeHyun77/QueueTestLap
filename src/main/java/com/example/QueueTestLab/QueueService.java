package com.example.QueueTestLab;

import com.example.QueueTestLab.exception.ErrorCode;
import com.example.QueueTestLab.exception.ReserveException;
import com.example.QueueTestLab.kafka.KafkaProducerService;
import com.example.QueueTestLab.user.User;
import com.example.QueueTestLab.user.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
@Service
public class QueueService {

    private final KafkaProducerService kafkaProducerService;
    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;

    public static final String WAIT_QUEUE = ":user-queue:wait";
    public static final String ALLOW_QUEUE = ":user-queue:allow";
    public static final String ACCESS_TOKEN = ":user-access:";

    /**
     * 대기열 등록
     * 참가하려는 사용자가 이미 대기열 혹은 참가열에 있다면 예외 발생
     * 등록을 진행할 후 rank를 반환할 때, null이 반환되면 -1을 반환하도록 함
     */
    public Long registerUserToWaitQueue(String userId, String queueType, long enterTimestamp) {
        // 대기열에 및 참가열 사용자 존재 여부
        boolean existsInWaitQueue = isExistUserInWaitOrAllow(userId, queueType, "wait");
        boolean existsInAllowQueue = isExistUserInWaitOrAllow(userId, queueType, "allow");

        // 대기열이나 참가열에 동일한 사용자가 있다면 대기열 등록 x, 중복 등록을 막기 위함
        if (existsInWaitQueue || existsInAllowQueue) {
            throw new ReserveException(HttpStatus.BAD_REQUEST, ErrorCode.ALREADY_REGISTERED_USER);
        }

        // 중복 없으면 등록 진행
        // add의 결과는 Boolean 으로 반환됨 (true: 추가됨, false: 이미 존재)
        Boolean added = redisTemplate.opsForZSet()
                .add(queueType + WAIT_QUEUE, userId, enterTimestamp);

        if (Boolean.FALSE.equals(added)) {
            throw new ReserveException(HttpStatus.BAD_REQUEST, ErrorCode.ALREADY_REGISTERED_USER);
        }

        Long rank = redisTemplate.opsForZSet()
                .rank(queueType + WAIT_QUEUE, userId);

        Long result = (rank != null) ? rank + 1 : -1L;

        changeUserStatus(queueType, userId, "wait");
        kafkaProducerService.sendMessage(queueType, userId);

        log.info("{}님 {}번째로 사용자 대기열 등록 성공", userId, result);
        return result; // rank 값 or -1 반환
    }

    /**
     * 대기열 or 참가열에서 사용자 존재 여부 확인
     */
    public boolean isExistUserInWaitOrAllow(String userId, String queueType, String queueCategory) {

        String keyType = queueCategory.equals("wait") ? WAIT_QUEUE : ALLOW_QUEUE;

        Long rank = redisTemplate.opsForZSet()
                .rank(queueType + keyType, userId);

        boolean exists = (rank != null && rank >= 0);

        log.info("{}님 {} 존재 여부 : {}", userId, queueCategory.equals("wait") ? "대기열" : "참가열", exists);
        return exists;
    }

    /**
     * 대기열 or 참가열에서 사용자 순위 조회
     */
    public Long searchUserRanking(String userId, String queueType, String queueCategory) {

        String keyType = queueCategory.equals("wait") ? WAIT_QUEUE : ALLOW_QUEUE;

        Long rank = redisTemplate.opsForZSet().rank(queueType + keyType, userId);
        Long resultRank = (rank != null) ? rank + 1 : -1L; // 사용자가 없으면 -1 반환, 순위는 0부터 시작하므로 +1

        if (resultRank <= 0) {
            log.warn("[{}] {}님이 존재하지 않습니다. 순위: {}", queueCategory, userId, resultRank);
        } else {
            log.info("[{}] {}님의 현재 순위는 {}번입니다.", queueCategory, userId, resultRank);
        }
        return resultRank; // rank 값 or -1 반환
    }

    /**
     * 대기열 or 참가열에 등록된 사용자 제거
     */
    public void cancelWaitUser(String userId, String queueType, String queueCategory) {

        // 대기열에서 삭제
        if (queueCategory.equals("wait")) {
            Long removedCount = redisTemplate.opsForZSet().remove(queueType + WAIT_QUEUE, userId);

            // 삭제 안됨
            if (removedCount == null || removedCount == 0) {
                throw new ReserveException(HttpStatus.BAD_REQUEST, ErrorCode.USER_NOT_FOUND_IN_THE_QUEUE);
            }

            kafkaProducerService.sendMessage(queueType, userId);
            log.info("{}님 대기열에서 취소 완료", userId);

        // 참가열에서 삭제
        } else {
            Long removedCount = redisTemplate.opsForZSet()
                    .remove(queueType + ALLOW_QUEUE, userId);

            // 삭제 안됨
            if (removedCount == null || removedCount == 0) {
                throw new ReserveException(HttpStatus.BAD_REQUEST, ErrorCode.USER_NOT_FOUND_IN_THE_QUEUE);
            }

            String tokenTtlKey = "token:" + userId + ":TTL";
            try {
                redisTemplate.delete(tokenTtlKey);
                log.info("{}님의 TTL 키 삭제 완료", userId);
            } catch (Exception e) {
                log.error("{}님의 TTL 키 삭제 중 오류 발생: {}", userId, e.getMessage());
            }

            log.info("{}님 참가열에서 취소 완료", userId);
        }

        changeUserStatus(queueType, userId, "canceled");
    }

    /**
     * 유효성 검사를 위한 토큰 생성 코드
     */
    public static String generateAccessToken(String userId, String queueType) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = queueType + ACCESS_TOKEN + userId;
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Token 생성 실패", e);
        }
    }

    /**
     * 쿠키에 생성한 토큰을 저장
     */
    public ResponseEntity<String> sendCookie(String userId, String queueType, HttpServletResponse response) {
        log.info("userId : {}", userId);
        String encodedName = URLEncoder.encode(userId, StandardCharsets.UTF_8);

        String token = generateAccessToken(userId, queueType); // 동기 호출
        Cookie cookie = new Cookie(queueType + "_user-access-cookie_" + encodedName, token);
        cookie.setPath("/");
        cookie.setMaxAge(300);
        response.addCookie(cookie);
        return ResponseEntity.ok("쿠키 발급 완료");
    }

    /**
     * 토큰 유효성 검사
     */
    public boolean isAccessTokenValid(String userId, String queueType, String token) {
        String tokenKey = "token:" + userId + ":TTL";

        String storedToken = redisTemplate.opsForValue().get(tokenKey);

        if (storedToken == null) return false;

        // TTL이 만료되지 않았다면
        String generatedToken = generateAccessToken(userId, queueType);
        return generatedToken.equals(token);
    }

    /**
     * 새로고침 시 대기열 후순위 재등록 로직
     */
    public void reEnterWaitQueue(String userId, String queueType) {
        long newTimestamp = Instant.now().toEpochMilli();

        redisTemplate.opsForZSet().add(queueType + WAIT_QUEUE, userId, newTimestamp);

        changeUserStatus(queueType, userId, "wait");
        kafkaProducerService.sendMessage(queueType, userId);
    }

    /**
     * 대기열에 있는 상위 count 명을 참가열로 옮기고, 토큰을 생성하여 redis에 저장 ( 유효 기간 10분 )
     */
    public Long allowUser(String queueType, Long count) {

        Set<String> membersToAllow = redisTemplate.opsForZSet()
                .range(queueType + WAIT_QUEUE, 0, count - 1);

        if (membersToAllow == null || membersToAllow.isEmpty()) return 0L;

        long allowedCount = 0;

        for (String userId : membersToAllow) {
            log.info("참가열 이동 사용자 : {}", userId);

            long timestamp = Instant.now().toEpochMilli();
            String tokenKey = "token:" + userId + ":TTL";

            // 참가열 추가
            redisTemplate.opsForZSet()
                    .add(queueType + ALLOW_QUEUE, userId, timestamp);

            // 토큰 저장 (TTL 10분)
            redisTemplate.opsForValue()
                    .set(tokenKey, "allowed", Duration.ofMinutes(10));

            // 대기열에서 제거
            redisTemplate.opsForZSet()
                    .remove(queueType + WAIT_QUEUE, userId);

            kafkaProducerService.sendMessage(queueType, userId);
            changeUserStatus(queueType, userId, "allow");
            allowedCount++;
        }

        log.info("참가열로 이동된 사용자 수: {}", allowedCount);
        return allowedCount; // 참가열로 이동된 사용자 수
    }

    /**
     * 대기열의 사용자를 참가열로 maxAllowedUsers 명 옮기는 scheduling 코드
     */
    @Scheduled(fixedDelay = 3000, initialDelay = 40000) // 시작 40초 후 실행, 매 실행 종료 후 3초 간격
    public void moveUserToAllowQ() {
        Long maxAllowedUsers = 3L;

        // 여러 종류의 대기 큐가 있다고 가정
        List<String> queueTypes = List.of("reserve"); // 추후 확장 가능하게

        queueTypes.forEach(queueType -> {
            Long movedCount = allowUser(queueType, maxAllowedUsers); // 동기 호출

            if (movedCount > 0) {
                log.info("{}에서 {}명의 사용자가 참가열로 이동되었습니다.", queueType, movedCount);
            } else {
                log.info("참가열로 이동된 사용자가 없습니다");
            }
        });
    }

    @Transactional
    private void changeUserStatus(String queueType, String userId, String status) {
        User user = userRepository.findByUserId(userId)
                .map(u -> {
                    u.updateStatus(status);
                    return u;
                })
                .orElseGet(() -> User.builder()
                        .userId(userId)
                        .queueType(queueType)
                        .status(status)
                        .build());

        userRepository.save(user);
    }
}

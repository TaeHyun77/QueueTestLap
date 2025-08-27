JMeter를 활용해 부하 테스트를 수행하고, Prometheus와 Grafana를 통해 애플리케이션, MySQL DB, Kafka의 부하 상황을 모니터링했습니다

### 대기열 시스템 구현 과정
---

1. MVC 기반 대기열 구현<br><br>
   대기열 참여 → Kafka에 대기열 이름 발행 → Kafka consume으로 SSE Sink에 이벤트 전달<br><br>



- DB-이벤트 정합성 문제<br><br>
  이벤트 발행과 DB 상태의 불일치가 발생하여 Debezium 기반 CDC 방식으로 문제를 해결하였습니다.<br><br>



3. 동기적 성능의 한계<br><br>
   WebFlux 전환<br><br>
   기존 동기 방식 성능 부족에 따라 비동기 방식으로 전환<br><br>
   → WebFlux 기반 비동기 처리로 전환 ( 이때까진 CDC 유지 )<br><br>



5. CDC 한계<br><br>
   DB 의존성이 매우 높음<br><br>
   → 대량 트래픽 상황에서 DB가 병목 지점으로 작용할 수 있으며 SPOF가 될 수 있음<br><br>

   DB 제거하고 Kafka로 직접 이벤트를 발행하는 방식으로 변경<br><br>



5. 멱등성 처리 개선<br><br>
   대기열 로직에 멱등성 로직을 적용하는 과정에서 멱등키 값을 저장하는 동기적인 DB 접근이 발생<br><br>

   이 방식은 비동기 시스템에 맞지 않기에 R2DBC로 변경하여 비동기 DB 접근으로 변경<br><br>


7. 확장성 확보<br><br>
   단일 서버에서 분산 서버 아키텍처로 전환<br><br>


### 분기별 테스트 항목
---

1. MVC + CDC 기반 테스트<br><br>

   DB와 CDC 동작 검증 및 이벤트 정합성을 확인 <br><br>


3. WebFlux + CDC 기반 테스트<br><br>

   비동기로 변경함에 따라 처리 성능 측정<br><br>


3. WebFlux + Kafka 직발행 (CDC 제거) 테스트<br><br>
   DB 미사용 시 성능 개선 효과 검증<br><br>


5. 코루틴 기반 테스트<br><br>
   Kotlin Coroutine 적용 시 성능/코드 가독성 비교<br><br>

   Reactor 대비 장단점 평가

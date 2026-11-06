package com.bioengine.coach.service;

import com.bioengine.coach.grpc.CoachServiceGrpc;
import com.bioengine.coach.grpc.RecommendationRequest;
import com.bioengine.coach.grpc.RecommendationResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

/*
 * реализация grpc-сервиса для выдачи ежедневных рекомендаций
 * основная задача класса обеспчить скорость ответа и защитить бд
 * при помощи Cache-Aside с распределенной блокировкой
 */

@GrpcService
public class CoachServiceImpl extends CoachServiceGrpc.CoachServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(CoachServiceImpl.class);

    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    private static final Duration LOCK_TTL  = Duration.ofSeconds(10);

    private static final int WAIT_ATTEMPTS = 8;
    private static final long WAIT_BASE_MS = 150;   

    private static final int MIN_STRESS = 0;
    private static final int MAX_STRESS = 10;

    private final StringRedisTemplate redis;
    private final RedisLockService lockService;

    public CoachServiceImpl(StringRedisTemplate redis, RedisLockService lockService) {
        this.redis = redis;
        this.lockService = lockService;
    }

    @Override
    public void getDailyRecommendation(RecommendationRequest request,
                                       StreamObserver<RecommendationResponse> responseObserver) {
        long start = System.currentTimeMillis();

        if (request.getUserId() == null || request.getUserId().isBlank()
                || request.getUserId().length() > 64) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("userId must be non-blank and <= 64 chars")
                    .asRuntimeException());
            return;
        }
        int stressLevel = request.getStressLevel();
        if (stressLevel < MIN_STRESS || stressLevel > MAX_STRESS) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("stressLevel must be in [" + MIN_STRESS + ".." + MAX_STRESS + "]")
                    .asRuntimeException());
            return;
        }

        String userId = request.getUserId();
        String cacheKey = "coach:insight:" + userId + ":" + stressLevel;
        String lockKey  = "lock:" + cacheKey;

        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null) {
                log.info("CACHE HIT for key: {}", cacheKey);
                respondOk(responseObserver, userId, cached, true, start);
                return;
            }

            log.info("CACHE MISS for key: {}. Trying to acquire lock...", cacheKey);
            String token = lockService.tryLock(lockKey, LOCK_TTL);

            if (token != null) {
                try {
                    cached = redis.opsForValue().get(cacheKey);
                    if (cached != null) {
                        log.info("CACHE HIT after lock for key: {}", cacheKey);
                        respondOk(responseObserver, userId, cached, true, start);
                        return;
                    }

                    log.info("Lock acquired. Querying 'slow database'...");
                    String recommendation = simulateSlowDatabaseQuery(userId, stressLevel);
                    redis.opsForValue().set(cacheKey, recommendation, CACHE_TTL);

                    respondOk(responseObserver, userId, recommendation, false, start);
                } finally {
                    lockService.unlock(lockKey, token);
                }
                return;
            }

            log.info("Lock is busy. Waiting for cache to be filled by another thread...");
            String result = awaitCache(cacheKey);
            if (result == null) {
                log.warn("Cache still empty for key {}. Returning fallback.", cacheKey);
                result = "Fallback recommendation (cache fill failed, please retry)";
            }
            respondOk(responseObserver, userId, result, true, start);

        } catch (StatusRuntimeException e) {
            responseObserver.onError(e);
        } catch (Exception e) {
            log.error("Unexpected error in getDailyRecommendation", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Internal error: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    private String awaitCache(String cacheKey) throws InterruptedException {
        long sleep = WAIT_BASE_MS;
        for (int i = 0; i < WAIT_ATTEMPTS; i++) {
            Thread.sleep(sleep + ThreadLocalRandom.current().nextLong(sleep / 2 + 1));
            String v = redis.opsForValue().get(cacheKey);
            if (v != null) return v;
            sleep = Math.min(sleep * 2, 2_000);
        }
        return null;
    }

    private String simulateSlowDatabaseQuery(String userId, int stressLevel) throws InterruptedException {
        Thread.sleep(2_000); 
        if (stressLevel > 7) {
            return "High stress detected. Take Magnesium L-Threonate and do breathing exercises.";
        }
        return "Normal stress. Take your regular vitamins and go for a walk.";
    }

    private void respondOk(StreamObserver<RecommendationResponse> obs,
                           String userId, String recommendation,
                           boolean fromCache, long startMs) {
        long timeTaken = System.currentTimeMillis() - startMs;
        obs.onNext(RecommendationResponse.newBuilder()
                .setUserId(userId)
                .setRecommendation(recommendation)
                .setFromCache(fromCache)
                .setGenerationTimeMs(timeTaken)
                .build());
        obs.onCompleted();
    }
}
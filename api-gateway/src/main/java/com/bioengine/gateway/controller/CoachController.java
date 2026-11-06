//принимает обычные HTTP-запросы и превращает их в grpc-вызовы

package com.bioengine.gateway.controller;

import com.bioengine.coach.grpc.CoachServiceGrpc;
import com.bioengine.coach.grpc.RecommendationRequest;
import com.bioengine.coach.grpc.RecommendationResponse;
import com.bioengine.gateway.dto.RecommendationResponseDto;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/coach")
@Validated
public class CoachController {

    private static final Logger log = LoggerFactory.getLogger(CoachController.class);

    @GrpcClient("coach-service")
    private CoachServiceGrpc.CoachServiceBlockingStub coachClient;

    @GetMapping("/recommendation")
    public ResponseEntity<?> getRecommendation(
            @RequestParam @NotBlank @Size(max = 64) String userId,
            @RequestParam @Min(0) @Max(10) int stressLevel) {

        RecommendationRequest request = RecommendationRequest.newBuilder()
                .setUserId(userId)
                .setStressLevel(stressLevel)
                .build();

        try {
            RecommendationResponse grpcResponse = coachClient.getDailyRecommendation(request);
            return ResponseEntity.ok(new RecommendationResponseDto(
                    grpcResponse.getUserId(),
                    grpcResponse.getRecommendation(),
                    grpcResponse.getFromCache(),
                    grpcResponse.getGenerationTimeMs()
            ));
        } catch (StatusRuntimeException e) {
            //маппинг grpc статусов в HTTP, чтобы клиент получал понятныый код
            log.warn("gRPC call failed: {} - {}", e.getStatus().getCode(), e.getStatus().getDescription());
            HttpStatus http = switch (e.getStatus().getCode()) {
                case INVALID_ARGUMENT      -> HttpStatus.BAD_REQUEST;
                case NOT_FOUND             -> HttpStatus.NOT_FOUND;
                case DEADLINE_EXCEEDED     -> HttpStatus.GATEWAY_TIMEOUT;
                case UNAVAILABLE           -> HttpStatus.SERVICE_UNAVAILABLE;
                default                    -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
            return ResponseEntity.status(http)
                    .body(new ErrorDto(e.getStatus().getCode().name(),
                            e.getStatus().getDescription()));
        }
    }

    public record ErrorDto(String code, String message) {}
}
//стоит перед CoachServiceImpl и перехватывает все входящие grpc-запросы

package com.bioengine.coach.interceptor;
import io.grpc.ForwardingServerCallListener;
import io.grpc.ForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@GrpcGlobalServerInterceptor
public class LoggingInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LoggingInterceptor.class);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {

        long start = System.currentTimeMillis();
        String method = call.getMethodDescriptor().getFullMethodName();
        log.info("gRPC Request started: {}", method);

        
        ServerCall<ReqT, RespT> wrappedCall = new ForwardingServerCall.SimpleForwardingServerCall<>(call) {
            @Override
            public void close(Status status, Metadata trailers) {
                long duration = System.currentTimeMillis() - start;
                if (status.isOk()) {
                    log.info("gRPC Request {} completed OK in {} ms", method, duration);
                } else {
                    log.warn("gRPC Request {} closed with {} in {} ms: {}",
                            method, status.getCode(), duration, status.getDescription());
                }
                super.close(status, trailers);
            }
        };

        ServerCall.Listener<ReqT> listener = next.startCall(wrappedCall, headers);

        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(listener) {
            @Override
            public void onCancel() {
                log.warn("gRPC Request {} cancelled by client", method);
                super.onCancel();
            }

            @Override
            public void onHalfClose() {
                try {
                    super.onHalfClose();
                } catch (Exception e) {
                    log.error("gRPC Request {} failed in handler", method, e);
                    throw e;
                }
            }
        };
    }
}
package distsys.smartstudentaccomodationresourcemanagementsystem;

import WaterConsumptionService.WaterSummaryRequest;
import WaterConsumptionService.WaterSummaryResponse;
import WaterConsumptionService.WaterUploadSummary;
import WaterConsumptionService.WaterUsageReading;
import WaterConsumptionService.WaterConsumptionServiceGrpc;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WaterConsumptionService extends WaterConsumptionServiceGrpc.WaterConsumptionServiceImplBase {

    private static final int PORT = 50053;
    private static final Metadata.Key<String> AUTH_TOKEN_KEY = Metadata.Key.of("auth-token", Metadata.ASCII_STRING_MARSHALLER);
    private static final String VALID_TOKEN = "nci-student-token";
    private final Map<String, Double> apartmentUsageMap = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        WaterConsumptionService waterServer = new WaterConsumptionService();

        try {
            Server server = ServerBuilder.forPort(PORT)
                    .addService(waterServer)
                    .intercept(new AuthInterceptor())
                    .build()
                    .start();

            System.out.println("WaterConsumptionService started, listening on port " + PORT);

            ServiceRegistration registration = ServiceRegistration.getInstance();
            registration.registerService("_grpc._tcp.local.", "water-consumption-service", PORT,
                    "Smart water consumption monitoring service");

            server.awaitTermination();

        } catch (IOException e) {
            System.out.println("WaterConsumptionService could not start: " + e.getMessage());
        } catch (InterruptedException e) {
            System.out.println("WaterConsumptionService interrupted: " + e.getMessage());
        }
    }

        @Override
        public StreamObserver<WaterUsageReading> uploadWaterUsage(StreamObserver<WaterUploadSummary> responseObserver) {
            return new StreamObserver<WaterUsageReading>() {
                double currentStreamTotal = 0.0;
                String activeDeviceId = "UNKNOWN";

        @Override
        public void onNext(WaterUsageReading reading) {
       // Save the device ID and add to the current session total
                    activeDeviceId = reading.getDeviceID();
                    currentStreamTotal += reading.getWaterUsed();
                }

        @Override
        public void onError(Throwable t) {
                    System.out.println("Stream Error: " + t.getMessage());
                }

        @Override
        public void onCompleted() {
        //Add the new water usage readings to the existing total for that device id 
            double existingTotal = apartmentUsageMap.getOrDefault(activeDeviceId, 0.0);
            double newTotal = existingTotal + currentStreamTotal;
            apartmentUsageMap.put(activeDeviceId, newTotal);

            // determine advice based on the new cumulative total consumption litres
            String advice = newTotal > 200 
                ? "Water usage is high. Reduce consumption." 
                : "Water usage is within normal range.";

            WaterUploadSummary summary = WaterUploadSummary.newBuilder()
                    .setTotalDailyUsage(newTotal)
                    .setAdvice(advice)
                    .build();

            responseObserver.onNext(summary);
            responseObserver.onCompleted();
        }
    };
}

    @Override
    public void getDailyWaterSummary(WaterSummaryRequest request, StreamObserver<WaterSummaryResponse> responseObserver) {
        try {
            if (request.getApartmentID().trim().isEmpty()) {
                throw new IllegalArgumentException("Apartment ID is required.");
            }

            double totalUsage = apartmentUsageMap.getOrDefault(request.getApartmentID(), 0.0);
            String usageStatus = totalUsage > 200
                    ? "Water consumption is high. Reduce consumption."
                    : "Water consumption is within normal range.";

            WaterSummaryResponse response = WaterSummaryResponse.newBuilder()
                    .setTotalUsage(totalUsage)
                    .setUsageStatus(usageStatus)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription("Error while getting water summary.").asRuntimeException());
        }
    }

    static class AuthInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call,
                Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {

            String token = headers.get(AUTH_TOKEN_KEY);
            if (!VALID_TOKEN.equals(token)) {
                call.close(Status.UNAUTHENTICATED.withDescription("Invalid auth token."), headers);
                return new ServerCall.Listener<ReqT>() {
                };
            }
            return next.startCall(call, headers);
        }
    }
}

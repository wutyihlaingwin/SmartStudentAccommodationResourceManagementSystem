package distsys.smartstudentaccomodationresourcemanagementsystem;

import ElectricityUsageService.ElectricityReading;
import ElectricityUsageService.ElectricityUsageRequest;
import ElectricityUsageService.ElectricityUsageResponse;
import ElectricityUsageService.ElectricityUsageServiceGrpc;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;

import java.io.IOException;

public class ElectricityUsageService extends ElectricityUsageServiceGrpc.ElectricityUsageServiceImplBase {

    public static void main(String[] args) {
        int port = 50051;

        try {
            Server server = ServerBuilder.forPort(port)
                    .addService(new ElectricityUsageService())
                    .build()
                    .start();

            System.out.println("Electricity Usage Service started, listening on port " + port);

            ServiceRegistration.getInstance().registerService(
                    "_grpc._tcp.local.",
                    "electricity-usage-service",
                    port,
                    "Electricity usage monitoring service"
            );

            System.out.println("Electricity Usage Service registered successfully.");

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Shutting down Electricity Usage Service...");
                server.shutdown();
            }));

            server.awaitTermination();

        } catch (IOException e) {
            System.out.println("Failed to start Electricity Usage Service: " + e.getMessage());
        } catch (InterruptedException e) {
            System.out.println("Server interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void getCurrentUsage(ElectricityUsageRequest request,
                                StreamObserver<ElectricityUsageResponse> responseObserver) {

        String apartmentId = request.getApartmentID().trim();

        if (apartmentId.isEmpty()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Apartment ID cannot be empty.")
                            .asRuntimeException()
            );
            return;
        }

        double currentUsage = getBaseUsageForApartment(apartmentId);
        String statusMessage = getUsageStatus(currentUsage);

        ElectricityUsageResponse response = ElectricityUsageResponse.newBuilder()
                .setCurrentUsage(currentUsage)
                .setStatusMessage(statusMessage)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void streamUsage(ElectricityUsageRequest request,
                            StreamObserver<ElectricityReading> responseObserver) {

        String apartmentId = request.getApartmentID().trim();

        if (apartmentId.isEmpty()) {
            responseObserver.onError(
                    Status.INVALID_ARGUMENT
                            .withDescription("Apartment ID cannot be empty.")
                            .asRuntimeException()
            );
            return;
        }

        try {
            double baseUsage = getBaseUsageForApartment(apartmentId);

            // 3-hour interval timestamps as per project proposal
            String[] timestamps = {
                    "09:00",
                    "12:00",
                    "15:00",
                    "18:00",
                    "21:00"
            };

            // Realistic gradual pattern for a student apartment
            double[] increments;

            if (apartmentId.equalsIgnoreCase("APT01") || apartmentId.equalsIgnoreCase("A01")) {
                increments = new double[]{0.0, 0.2, 0.4, 0.5, 0.3};
            } else if (apartmentId.equalsIgnoreCase("APT02")) {
                increments = new double[]{0.0, 0.1, 0.3, 0.4, 0.2};
            } else if (apartmentId.equalsIgnoreCase("APT03")) {
                increments = new double[]{0.0, 0.3, 0.5, 0.7, 0.4};
            } else {
                increments = new double[]{0.0, 0.2, 0.3, 0.5, 0.2};
            }

            for (int i = 0; i < timestamps.length; i++) {
                double usageValue = roundToTwoDecimals(baseUsage + increments[i]);

                ElectricityReading reading = ElectricityReading.newBuilder()
                        .setUsageValue(usageValue)
                        .setTimestamp(timestamps[i])
                        .build();

                System.out.println("Sending reading: " + timestamps[i] + " = " + usageValue + " kWh");
                responseObserver.onNext(reading);

                // Short delay for demo only
                Thread.sleep(1500);
            }

            responseObserver.onCompleted();

        } catch (InterruptedException e) {
            responseObserver.onError(
                    Status.CANCELLED
                            .withDescription("Electricity streaming was interrupted or cancelled.")
                            .asRuntimeException()
            );
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            responseObserver.onError(
                    Status.INTERNAL
                            .withDescription("Error while streaming electricity usage: " + e.getMessage())
                            .asRuntimeException()
            );
        }
    }

    private double getBaseUsageForApartment(String apartmentId) {
        if (apartmentId.equalsIgnoreCase("APT01") || apartmentId.equalsIgnoreCase("A01")) {
            return 3.4;
        } else if (apartmentId.equalsIgnoreCase("APT02")) {
            return 2.9;
        } else if (apartmentId.equalsIgnoreCase("APT03")) {
            return 4.0;
        } else {
            return 3.1;
        }
    }

    private String getUsageStatus(double usage) {
        if (usage <= 3.5) {
            return "Normal electricity usage.";
        } else if (usage <= 4.5) {
            return "Moderate electricity usage.";
        } else {
            return "High electricity usage. Please reduce unnecessary appliance use.";
        }
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
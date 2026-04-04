/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package distsys.smartstudentaccomodationresourcemanagementsystem;

/**
 *
 * @author wutyihlaingwin
 */


import GroceryManagementService.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;

public class AuthenticationTestClient {

    public static void main(String[] args) {

        ManagedChannel channel =
                ManagedChannelBuilder
                        .forAddress("localhost",50052)
                        .usePlaintext()
                        .build();

        // Wrong token replaced instead of nci-student-token
        Metadata header = new Metadata();

        Metadata.Key<String> authKey =
                Metadata.Key.of("auth-token",
                Metadata.ASCII_STRING_MARSHALLER);

        header.put(authKey,"wrong-token");

        GroceryManagementServiceGrpc
                .GroceryManagementServiceBlockingStub stub =
                GroceryManagementServiceGrpc
                .newBlockingStub(channel)
                .withInterceptors(
                        MetadataUtils
                        .newAttachHeadersInterceptor(header));

        try{

            GroceryItemRequest request =
                    GroceryItemRequest.newBuilder()
                    .setItemName("Milk")
                    .setQuantity(1)
                    .setAddedBy("Test")
                    .build();

            stub.addGroceryItem(request);

        }
        catch(Exception e){

            System.out.println("Authentication Error:");
            System.out.println(e.getMessage());

        }

        channel.shutdown();
    }
}
package distsys.smartstudentaccomodationresourcemanagementsystem;

import GroceryManagementService.GroceryItemRequest;
import GroceryManagementService.GroceryItemResponse;
import GroceryManagementService.GroceryListResponse;
import GroceryManagementService.GroceryUpdateRequest;
import GroceryManagementService.GroceryManagementServiceGrpc;
import io.grpc.Context;
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

public class GroceryManagementService extends GroceryManagementServiceGrpc.GroceryManagementServiceImplBase {

    private static final int PORT = 50052;
    private static final Metadata.Key<String> AUTH_TOKEN_KEY = Metadata.Key.of("auth-token", Metadata.ASCII_STRING_MARSHALLER);
    private static final String VALID_TOKEN = "nci-student-token";
    private final Map<String, Integer> groceryMap = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        GroceryManagementService groceryServer = new GroceryManagementService();

        try {
            Server server = ServerBuilder.forPort(PORT)
                    .addService(groceryServer)
                    .intercept(new AuthInterceptor())
                    .build()
                    .start();

            System.out.println("GroceryManagementService started, listening on port " + PORT);

            ServiceRegistration registration = ServiceRegistration.getInstance();
            registration.registerService("_grpc._tcp.local.", "grocery-management-service", PORT,
                    "Shared grocery management service");

            server.awaitTermination();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            System.out.println("GroceryManagementService could not start: " + e.getMessage());
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            System.out.println("GroceryManagementService interrupted: " + e.getMessage());
        }
    }
/* UNARY RPC
	 * client sends grocery item name, the quantity and the name of the person who is adding this grocery item in the request. The server method also gets input of  a responseObsever
	 * 
	 * the server sends its response with true (if succesfully added)or else false.
	 * Server replies with the message and returns it 
	 * rpc addGroceryItem(GrocveryItemRequest) returns (GroceryItemResponse) {}
	 */
    @Override
    public void addGroceryItem(GroceryItemRequest request, StreamObserver<GroceryItemResponse> responseObserver) {
        //validate the item add request with the itema name and quantity
        try {
            validateAddRequest(request);

            groceryMap.merge(request.getItemName().trim().toLowerCase(), request.getQuantity(), Integer::sum);

        // create the response object using its builder method and the set methods for each of its attributes
            GroceryItemResponse response = GroceryItemResponse.newBuilder()
                    .setSuccess(true)
                    .setMessage(request.getItemName() + " added successfully by " + request.getAddedBy())
                    .build();
        // respond to the client using onNext() on the responseObserver
            responseObserver.onNext(response);
         // tell the client we are done using onComplete() on the responseOberver
            responseObserver.onCompleted();

        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription("Error while adding grocery item.").asRuntimeException());
        }
    }

    
    // Bi-directional streaming operation
	// client sends a stream of messages with action such as adding or removing along with the grocery item name
	// server processes each message and returns a response of the live grocery updated list
	
	
	// rpc (GroceryUpdateRequest) returns (GroceryListResponse ) {}

    @Override
    public StreamObserver<GroceryUpdateRequest> liveGroceryList(StreamObserver<GroceryListResponse> responseObserver) {
       // server sets up a new observer that processes each client request when it sees onNext() 
        return new StreamObserver<GroceryUpdateRequest>() {
            @Override
            public void onNext(GroceryUpdateRequest request) {
                try {
                    if (Context.current().isCancelled()) {
                        System.out.println("Grocery live stream cancelled by client.");
                        return;
                    }

                    validateLiveRequest(request);
                    String itemKey = request.getItemName().trim().toLowerCase();
                    String action = request.getActionType().trim().toUpperCase();

                    if ("ADD".equals(action)) {
                        groceryMap.merge(itemKey, request.getQuantity(), Integer::sum);
                    } else if ("REMOVE".equals(action)) {
                        int currentQty = groceryMap.getOrDefault(itemKey, 0);
                        int newQty = currentQty - request.getQuantity();
                        if (newQty > 0) {
                            groceryMap.put(itemKey, newQty);
                        } else {
                            groceryMap.remove(itemKey);
                        }
                    } else {
                        throw new IllegalArgumentException("Action type must be ADD or REMOVE.");
                    }

                    GroceryListResponse response = GroceryListResponse.newBuilder()
                            .addAllUpdatedGroceryList(groceryMap.keySet())
                            .setItemName(request.getItemName())
                            .setQuantity(groceryMap.getOrDefault(itemKey, 0))
                            .build();

                    responseObserver.onNext(response);

                } catch (IllegalArgumentException e) {
                    responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
                } catch (Exception e) {
                    responseObserver.onError(Status.INTERNAL.withDescription("Error while updating live grocery list.").asRuntimeException());
                }
            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println("Client error in live grocery list: " + throwable.getMessage());
            }
            //completed too
            @Override
            public void onCompleted() {
                responseObserver.onCompleted();
            }
        };
    }

    private void validateAddRequest(GroceryItemRequest request) {
        if (request.getItemName().trim().isEmpty()) {
            throw new IllegalArgumentException("Item name is required.");
        }
        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
        }
        if (request.getAddedBy().trim().isEmpty()) {
            throw new IllegalArgumentException("AddedBy is required.");
        }
    }

    private void validateLiveRequest(GroceryUpdateRequest request) {
        if (request.getItemName().trim().isEmpty()) {
            throw new IllegalArgumentException("Item name is required.");
        }
        if (request.getQuantity() <= 0) {
            throw new IllegalArgumentException("Quantity must be greater than zero.");
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

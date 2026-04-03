package distsys.smartstudentaccomodationresourcemanagementsystem;

import ElectricityUsageService.*;
import GroceryManagementService.*;
import WaterConsumptionService.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.stream.Collectors;

public class SmartStudentAccomodationClient extends JFrame {

    // gRPC Channels
    private ManagedChannel electricityChannel;
    private ManagedChannel groceryChannel;
    private ManagedChannel waterChannel;

    // gRPC Stubs
    private ElectricityUsageServiceGrpc.ElectricityUsageServiceBlockingStub elecBlockingStub;
    private ElectricityUsageServiceGrpc.ElectricityUsageServiceStub elecAsyncStub;
    private GroceryManagementServiceGrpc.GroceryManagementServiceBlockingStub groceryBlockingStub;
    private GroceryManagementServiceGrpc.GroceryManagementServiceStub groceryAsyncStub;
    private WaterConsumptionServiceGrpc.WaterConsumptionServiceBlockingStub waterBlockingStub;
    private WaterConsumptionServiceGrpc.WaterConsumptionServiceStub waterAsyncStub;

    // Persistent Observers for Streaming Services
    private StreamObserver<GroceryUpdateRequest> groceryBidiObserver;
    private StreamObserver<WaterUsageReading> waterStreamObserver;

    // UI Components
    private JTextArea outputArea;
     /**
     * set up the channel and the stubs to allow for both blocking and
     * non-blocking services
     */
    public SmartStudentAccomodationClient() {
        //Initializing Channels (Matching the ports in the Server files)
        electricityChannel = ManagedChannelBuilder.forAddress("localhost", 50051).usePlaintext().build();
        groceryChannel = ManagedChannelBuilder.forAddress("localhost", 50052).usePlaintext().build();
        waterChannel = ManagedChannelBuilder.forAddress("localhost", 50053).usePlaintext().build();

        //Setting up Security Headers (Matching VALID_TOKEN in the Server files)
        Metadata header = new Metadata();
        Metadata.Key<String> authKey = Metadata.Key.of("auth-token", Metadata.ASCII_STRING_MARSHALLER);
        header.put(authKey, "nci-student-token");

        //Initializing Stubs with Interceptors
        elecBlockingStub = ElectricityUsageServiceGrpc.newBlockingStub(electricityChannel);
        elecAsyncStub = ElectricityUsageServiceGrpc.newStub(electricityChannel);

        groceryBlockingStub = GroceryManagementServiceGrpc.newBlockingStub(groceryChannel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header));
        groceryAsyncStub = GroceryManagementServiceGrpc.newStub(groceryChannel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header));

        waterBlockingStub = WaterConsumptionServiceGrpc.newBlockingStub(waterChannel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header));
        waterAsyncStub = WaterConsumptionServiceGrpc.newStub(waterChannel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(header));

        //Setting up GUI
        setTitle("Smart Student Accommodation Resource Management System");
        setSize(900, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Electricity Usage", createElectricityPanel());
        tabbedPane.addTab("Grocery Management", createGroceryPanel());
        tabbedPane.addTab("Water Consumption", createWaterPanel());

        outputArea = new JTextArea(15, 50);
        outputArea.setBackground(Color.BLACK);
        outputArea.setForeground(Color.GREEN);
        outputArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        outputArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(outputArea);
        scrollPane.setBorder(new TitledBorder(BorderFactory.createLineBorder(Color.GRAY), "System Output Console"));

        add(tabbedPane, BorderLayout.CENTER);
        add(scrollPane, BorderLayout.SOUTH);
    }

    // Server 1: Electricity Usage Service
    //Electricity panel logic
    private JPanel createElectricityPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JTextField aptIdField = new JTextField("A01", 10);
        JPanel inputPnl = new JPanel(); 
        inputPnl.add(new JLabel("Apt ID:")); 
        inputPnl.add(aptIdField);

        JButton btnUnary = new JButton("Get Current Usage (Unary)");
        JButton btnStream = new JButton("Stream Usage (Server Stream)");

        //Unary Call uses getCurrentUsage() instead of getUsageValue()
        btnUnary.addActionListener(e -> {
            ElectricityUsageRequest req = ElectricityUsageRequest.newBuilder()
                    .setApartmentID(aptIdField.getText()).build();
            ElectricityUsageResponse res = elecBlockingStub.getCurrentUsage(req);
            appendOutput("currentUsage = " + res.getCurrentUsage() + " kWh"); 
            appendOutput("statusMessage = \"" + res.getStatusMessage() + "\"");
        });

        // Stream Observer must use <ElectricityReading> instead of <ElectricityUsageResponse>
        btnStream.addActionListener(e -> {
            ElectricityUsageRequest req = ElectricityUsageRequest.newBuilder()
                    .setApartmentID(aptIdField.getText()).build();
            appendOutput("--- Starting Real-Time Electricity Stream ---");

            // Observer type to ElectricityReading
            elecAsyncStub.streamUsage(req, new StreamObserver<ElectricityReading>() {
                @Override 
                public void onNext(ElectricityReading res) {
            //Output showing timestamp  and usage value in ElectricityReading
                    appendOutput(res.getTimestamp() + " hrs = " + res.getUsageValue() + " kWh");
                }
                @Override 
                public void onError(Throwable t) { 
                    appendOutput("Elec Error: " + t.getMessage()); 
                }
                @Override 
                public void onCompleted() { 
                    appendOutput("Electricity stream closed."); 
                }
            });
        });

        panel.add(new JLabel("Monitor Power Consumption (Sustainability Goal 12)", SwingConstants.CENTER));
        panel.add(inputPnl);
        JPanel btnPnl = new JPanel(); btnPnl.add(btnUnary); btnPnl.add(btnStream);
        panel.add(btnPnl);
        return panel;
    }

    //Server 2: Grocery Managment Service
    private JPanel createGroceryPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Unary method to add grocery item name, quantity and the name of person added
        JPanel addPnl = new JPanel(new FlowLayout());
        addPnl.setBorder(new TitledBorder("Add Item (Unary RPC)"));
        JTextField itemF = new JTextField(8); JTextField qtyF = new JTextField(3); JTextField byF = new JTextField(8);
        JButton btnAdd = new JButton("Add Item");
        addPnl.add(new JLabel("Item:")); addPnl.add(itemF); addPnl.add(new JLabel("Qty:")); addPnl.add(qtyF);
        addPnl.add(new JLabel("By:")); addPnl.add(byF); addPnl.add(btnAdd);

        btnAdd.addActionListener(e -> {
            GroceryItemRequest req = GroceryItemRequest.newBuilder()
                    .setItemName(itemF.getText()).setQuantity(Integer.parseInt(qtyF.getText())).setAddedBy(byF.getText()).build();
            GroceryItemResponse res = groceryBlockingStub.addGroceryItem(req);
            // output response showing sucess or not along with the message of item name
            appendOutput("success = " + res.getSuccess());
            appendOutput("message = \"" + res.getMessage() + "\"");
        });

        // Bi-directional method to show updated grocery list
        JPanel bidiPnl = new JPanel(new FlowLayout());
        bidiPnl.setBorder(new TitledBorder("Shared Shopping List (Bidi RPC)"));
        JComboBox<String> actionBox = new JComboBox<>(new String[]{"ADD", "REMOVE"});
        JTextField liveItemF = new JTextField(10); JButton btnUpdate = new JButton("Update List");
        bidiPnl.add(actionBox); bidiPnl.add(liveItemF); bidiPnl.add(btnUpdate);

        btnUpdate.addActionListener(e -> {
            String action = actionBox.getSelectedItem().toString();
            String item = liveItemF.getText();
            if (groceryBidiObserver == null) {
                groceryBidiObserver = groceryAsyncStub.liveGroceryList(new StreamObserver<GroceryListResponse>() {
                    @Override public void onNext(GroceryListResponse res) {
                        // after adding or removing item, show the update list in the fridge
                        String list = res.getUpdatedGroceryListList().stream().collect(Collectors.joining("\", \"", "[\"", "\"]"));
                        appendOutput("Current Grocery List: " + list);
                    }
                    @Override public void onError(Throwable t) { appendOutput("Grocery Bidi Error: " + t.getMessage()); groceryBidiObserver = null; }
                    @Override public void onCompleted() { appendOutput("Bidi list closed."); groceryBidiObserver = null; }
                });
            }
            groceryBidiObserver.onNext(GroceryUpdateRequest.newBuilder().setActionType(action).setItemName(item).setQuantity(1).build());
            appendOutput("Requesting: " + action + " " + item + "...");
        });

        panel.add(addPnl); panel.add(bidiPnl);
        return panel;
    }

    // Server 3: Water Consumption Service 
    private JPanel createWaterPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        //Client Streaming mehtod to upload water usage stream by deviceID with timestamps
        JPanel streamPanel = new JPanel(new FlowLayout());
        streamPanel.setBorder(BorderFactory.createTitledBorder("Track Hourly Usage (Client Stream)"));
        JTextField deviceIdField = new JTextField("w05", 6);
        JTextField amountField = new JTextField(5);
        JButton btnUpload = new JButton("Send Reading");
        JButton btnFinish = new JButton("Finish & Get Advice");

        streamPanel.add(new JLabel("Device ID:")); streamPanel.add(deviceIdField);
        streamPanel.add(new JLabel("Litres:")); streamPanel.add(amountField);
        streamPanel.add(btnUpload); streamPanel.add(btnFinish);

        btnUpload.addActionListener(e -> {
           try {
            if (waterStreamObserver == null) {
                waterStreamObserver = waterAsyncStub.uploadWaterUsage(new StreamObserver<WaterUploadSummary>() {
                   @Override
                    public void onNext(WaterUploadSummary res) {
                        appendOutput("\nOutput");
                        appendOutput("totalDailyUsage = " + res.getTotalDailyUsage() + " litres");
                        appendOutput("advice = \"" + res.getAdvice() + "\"");
                    }
                    @Override public void onError(Throwable t) { waterStreamObserver = null; }
                    @Override public void onCompleted() { appendOutput("\nAnalysis Complete."); waterStreamObserver = null; }
                });
            }

                String id = deviceIdField.getText().trim();
            double val = Double.parseDouble(amountField.getText().trim());
            String time = java.time.LocalTime.now().toString().substring(0, 5);

                // Displaying the Input details as requested
                appendOutput("deviceID = \"" + id + "\", waterUsed = " + val + ", time = \"" + time + "\"");

            waterStreamObserver.onNext(WaterUsageReading.newBuilder()
                    .setDeviceID(id).setWaterUsed(val).setTime(time).build());

        } catch (Exception ex) { appendOutput("Error: " + ex.getMessage()); }
        });

        btnFinish.addActionListener(e -> { if (waterStreamObserver != null) waterStreamObserver.onCompleted(); });

        // Unary Method for daily water usage summary
        JPanel summaryPanel = new JPanel(new FlowLayout());
        summaryPanel.setBorder(BorderFactory.createTitledBorder("Daily Summary (Unary RPC)"));
        JTextField summaryIdField = new JTextField("w05", 8);
        JButton btnSummary = new JButton("Request Summary");

        summaryPanel.add(new JLabel("Device ID:")); summaryPanel.add(summaryIdField);
        summaryPanel.add(btnSummary);

        btnSummary.addActionListener(e -> {
            try {
                String id = summaryIdField.getText().trim();
                // Displaying the Input data by client
                appendOutput("\nDaily Summary (Unary RPC)");
                appendOutput("input apartmentID = \"" + id + "\"");

                WaterSummaryResponse res = waterBlockingStub.getDailyWaterSummary(
                    WaterSummaryRequest.newBuilder().setApartmentID(id).build());
                
         // Output 
                
            appendOutput("\nOutput");
            appendOutput("totalUsage = " + res.getTotalUsage() + " litres");
            appendOutput("usageStatus = \"" + res.getUsageStatus() + "\"");
            } catch (Exception ex) { appendOutput("Error: " + ex.getMessage()); }
        });

            panel.add(streamPanel); panel.add(summaryPanel);
            return panel;
        }

    private void appendOutput(String text) {
        SwingUtilities.invokeLater(() -> {
            outputArea.append(text + "\n");
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception e) {}
        SwingUtilities.invokeLater(() -> new SmartStudentAccomodationClient().setVisible(true));
    }
}
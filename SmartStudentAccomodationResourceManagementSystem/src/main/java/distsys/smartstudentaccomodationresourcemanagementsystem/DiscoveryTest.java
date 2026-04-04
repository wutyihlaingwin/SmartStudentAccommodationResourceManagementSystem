/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package distsys.smartstudentaccomodationresourcemanagementsystem;

/**
 *
 * @author wutyihlaingwin
 */

import javax.jmdns.ServiceInfo;

public class DiscoveryTest {
    public static void main(String[] args) {
        ServiceDiscovery discovery = new ServiceDiscovery("_grpc._tcp.local.", "electricity-usage-service");

        try {
            ServiceInfo serviceInfo = discovery.discoverService(5000);

            if (serviceInfo != null) {
                System.out.println("Discovered service successfully:");
                System.out.println("Service name: " + serviceInfo.getName());
                System.out.println("Host: " + serviceInfo.getHostAddresses()[0]);
                System.out.println("Port: " + serviceInfo.getPort());
            } else {
                System.out.println("Service could not be discovered.");
            }

            discovery.close();
        } catch (Exception e) {
            System.out.println("Discovery error: " + e.getMessage());
        }
    }
}
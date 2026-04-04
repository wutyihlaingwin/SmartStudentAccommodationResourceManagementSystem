package distsys.smartstudentaccomodationresourcemanagementsystem;
/**
 *
 * @author Wut Yi Hlaing Win
 */
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceEvent;
import javax.jmdns.ServiceInfo;
import javax.jmdns.ServiceListener;

public class ServiceDiscovery {

    private final String requiredServiceType;
    private final String requiredServiceName;
    private ServiceInfo foundService;
    private JmDNS jmdns;

    public ServiceDiscovery(String requiredServiceType, String requiredServiceName) {
        this.requiredServiceType = requiredServiceType;
        this.requiredServiceName = requiredServiceName;
    }

    public ServiceInfo discoverService(long timeoutMilliseconds) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);

        try {
            InetAddress localAddress = getLocalAddress();
            // Create a JmDNS instance
            jmdns = JmDNS.create(localAddress);
            System.out.println("Client discovery address: " + localAddress.getHostAddress());
            // Add a service listener that listens for the required service type on localhost
            jmdns.addServiceListener(requiredServiceType, new ServiceListener() {
                @Override
                public void serviceAdded(ServiceEvent event) {
                    System.out.println("Service added: " + event.getName());
                    jmdns.requestServiceInfo(event.getType(), event.getName(), 1000);
                }

                @Override
                public void serviceRemoved(ServiceEvent event) {
                    System.out.println("Service removed: " + event.getName());
                }

                @Override
                public void serviceResolved(ServiceEvent event) {
                    ServiceInfo serviceInfo = event.getInfo();
                    System.out.println("Service resolved: " + serviceInfo.getName());
                 // check if the name of the service is the one we are looking for - if not we
                    // just ignore it.
                    if (serviceInfo.getName().equalsIgnoreCase(requiredServiceName)) {
                        foundService = serviceInfo;
                        // the event we were waiting for has happened. Release the latch. 
                        latch.countDown();
                    }
                }
            });

        } catch (IOException e) {
            System.out.println("Discovery error: " + e.getMessage());
        }
         // if there was no service resolved of the required type latch will timeoout
        latch.await(timeoutMilliseconds, TimeUnit.MILLISECONDS);
        return foundService;
    }

    public void close() throws IOException {
        if (jmdns != null) {
            jmdns.close();
        }
    }

    private static InetAddress getLocalAddress() throws IOException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();

            if (!networkInterface.isUp() || networkInterface.isLoopback() || networkInterface.isVirtual()) {
                continue;
            }

            Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress address = addresses.nextElement();

                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    return address;
                }
            }
        }

        return InetAddress.getLocalHost();
    }
}
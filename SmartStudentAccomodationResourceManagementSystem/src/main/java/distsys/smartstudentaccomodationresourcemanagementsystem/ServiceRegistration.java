
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
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

public class ServiceRegistration {

    private static JmDNS jmdns;
    private static ServiceRegistration serviceRegistration;
     /**
     * ServiceRegistration uses the Singleton pattern. Only one instance
     * of it can exist. The constructor is private. New instances are created by
     * calling the getInstance method. Services can register themselves by
     * invoking registerService. The constructor creates the DNS register object
     */
    private ServiceRegistration() throws IOException {
        InetAddress localAddress = getLocalAddress();
        jmdns = JmDNS.create(localAddress);
        System.out.println("Service registration address: " + localAddress.getHostAddress());
    }
     /**
     * Services call getInstance() to get the singleton instance of the register
     *
     * @return
     * @throws IOException
     */
    public static ServiceRegistration getInstance() throws IOException {
        if (serviceRegistration == null) {
            serviceRegistration = new ServiceRegistration();
        }
        return serviceRegistration;
    }
     /**
     * Services call registerService to register themselves so that clients can
     * discover the service
     *
     * @param type
     * @param name
     * @param port
     * @param text
     * @throws IOException
     */
    public void registerService(String type, String name, int port, String text) throws IOException {
        //Construct a service description for registering with JmDNS
        //Parameters:
        //type - fully qualified service type name, such as _http._tcp.local..
        //name - unqualified service instance name, such as foobar
        // port - the local port on which the service runs
        // text - string describing the service
        //Returns:
        //new service info
        ServiceInfo serviceInfo = ServiceInfo.create(type, name, port, text);
        // register the service
        jmdns.registerService(serviceInfo);
        System.out.println("Registered service: " + name + " on port " + port);
    }

    public void unregisterAllServices() throws IOException {
        if (jmdns != null) {
            jmdns.unregisterAllServices();
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
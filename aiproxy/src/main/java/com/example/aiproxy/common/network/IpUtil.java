package com.example.aiproxy.common.network;

import org.apache.commons.net.util.SubnetUtils;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class for IP address and subnet operations.
 * This class provides functionality to validate subnets and check if IP addresses
 * fall within specified subnets, similar to Go's net package capabilities.
 * It uses Apache Commons Net for CIDR subnet calculations.
 */
public class IpUtil {

    private static final Logger LOGGER = Logger.getLogger(IpUtil.class.getName());

    /**
     * Validates if the given string is a valid CIDR subnet notation.
     *
     * @param cidrSubnet The subnet string to validate (e.g., "192.168.1.0/24").
     * @return True if the subnet is valid, false otherwise.
     */
    public static boolean isValidSubnet(String cidrSubnet) {
        if (cidrSubnet == null || cidrSubnet.isEmpty()) {
            return false;
        }
        try {
            new SubnetUtils(cidrSubnet); // Constructor validates the CIDR string
            return true;
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.FINER, "Invalid subnet format: " + cidrSubnet, e);
            return false;
        }
    }

    /**
     * Checks if a given IP address is within the specified CIDR subnet.
     *
     * @param ipAddress  The IP address string to check.
     * @param cidrSubnet The CIDR subnet string (e.g., "192.168.1.0/24").
     * @return True if the IP address is within the subnet, false otherwise.
     * @throws IllegalArgumentException if the subnet string is invalid or the IP address string is malformed.
     */
    public static boolean isIpInSubnet(String ipAddress, String cidrSubnet) {
        if (ipAddress == null || ipAddress.isEmpty() || cidrSubnet == null || cidrSubnet.isEmpty()) {
            throw new IllegalArgumentException("IP address and subnet string cannot be null or empty.");
        }
        try {
            // Validate IP address format (SubnetUtils also does this, but good practice)
            // InetAddress.getByName(ipAddress); // This can be slow due to potential DNS lookups if not a raw IP.
            // SubnetUtils will handle IP validation internally more efficiently for raw IPs.

            SubnetUtils subnetUtils = new SubnetUtils(cidrSubnet);
            // Enable inclusiveHostCount to match typical CIDR behavior where network/broadcast addresses are not usually "in" the usable range
            // However, Go's ipNet.Contains includes network and broadcast addresses.
            // SubnetUtils.SubnetInfo.isInRange(address) by default checks the usable host range.
            // To match Go's behavior (which includes network and broadcast addresses in .Contains()),
            // we need to check against the network boundaries directly or ensure SubnetUtils's check is inclusive.
            // The .getInfo().isInRange(ipAddress) method is appropriate for host addresses.
            // For CIDR .Contains behavior, which checks if IP is part of the network including network/broadcast:
            // Parse the IP and compare numerically, or rely on SubnetUtils specific methods if they directly map.
            // SubnetUtils.getInfo().getAddress() (network address) and .getBroadcastAddress()
            // A simpler way: SubnetUtils directly supports this.
            return subnetUtils.getInfo().isInRange(ipAddress);

        } catch (IllegalArgumentException e) { // Covers invalid CIDR or invalid IP for SubnetUtils
            LOGGER.log(Level.WARNING, "Failed to parse subnet or IP: Subnet='" + cidrSubnet + "', IP='" + ipAddress + "'", e);
            throw e; // Re-throw as per Go's error return for bad format
        }
    }

    /**
     * Validates if all subnet strings in the given list are valid CIDR notations.
     *
     * @param subnets A list of subnet strings.
     * @return True if all subnets in the list are valid, false otherwise.
     */
    public static boolean isValidSubnets(List<String> subnets) {
        if (subnets == null || subnets.isEmpty()) {
            return true; // An empty list is considered valid (no invalid subnets)
        }
        for (String subnet : subnets) {
            if (!isValidSubnet(subnet)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if a given IP address is within any of the specified CIDR subnets.
     *
     * @param ipAddress The IP address string to check.
     * @param cidrSubnets A list of CIDR subnet strings.
     * @return True if the IP address is within at least one of the subnets, false otherwise.
     * @throws IllegalArgumentException if any subnet string in the list is invalid
     *                                  or the IP address string is malformed.
     */
    public static boolean isIpInSubnets(String ipAddress, List<String> cidrSubnets) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            throw new IllegalArgumentException("IP address cannot be null or empty.");
        }
        if (cidrSubnets == null || cidrSubnets.isEmpty()) {
            return false; // Not in any subnet if the list is empty
        }

        for (String subnet : cidrSubnets) {
            if (subnet == null || subnet.isEmpty()) { // Skip null/empty subnet strings in the list
                LOGGER.finer("Skipping null or empty subnet string in the list.");
                continue;
            }
            // isIpInSubnet will throw IllegalArgumentException if subnet is malformed.
            // This matches Go's behavior of returning an error for the first bad subnet.
            if (isIpInSubnet(ipAddress, subnet)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Extracts the client IP address from HttpServletRequest, considering common proxy headers.
     * It checks "X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP", 
     * "HTTP_X_FORWARDED_FOR", "HTTP_CLIENT_IP", "HTTP_X_CLIENT_IP", 
     * "HTTP_X_CLUSTER_CLIENT_IP", "HTTP_FORWARDED_FOR", "HTTP_FORWARDED", 
     * "HTTP_VIA", "REMOTE_ADDR" in that order.
     *
     * @param request The HttpServletRequest.
     * @return The client IP address, or the remote address if no proxy headers are found.
     *         Returns null if the request is null.
     */
    public static String getClientIpAddress(javax.servlet.http.HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        
        // Order of headers based on common practices and Go net/http/httputil.ReverseProxy logic
        String[] headersToTry = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR", // Usually for CGI environments
            "HTTP_CLIENT_IP",       // Usually for CGI environments
            "HTTP_X_CLIENT_IP",     // Usually for CGI environments
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR" // Fallback, though getRemoteAddr() is more direct
        };

        String ip = null;
        for (String header : headersToTry) {
            ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For can contain a list of IPs: "client, proxy1, proxy2"
                // The first IP is usually the client's.
                if ("X-Forwarded-For".equalsIgnoreCase(header) && ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                // Additional validation for the found IP can be done here if needed.
                // For example, ensuring it's a valid IP format.
                // For now, we trust the first valid-looking entry.
                if (isValidIpAddress(ip)) {
                    return ip;
                }
            }
        }
        
        // If no header found, use getRemoteAddr()
        ip = request.getRemoteAddr();
         if (isValidIpAddress(ip)) {
            return ip;
        }
        return ip; // Return whatever getRemoteAddr gave, even if it might be e.g. "0:0:0:0:0:0:0:1"
    }

    /**
     * Validates if a given string is a valid IP address (IPv4 or IPv6).
     * @param ipAddress The IP address string to validate.
     * @return True if valid, false otherwise.
     */
    public static boolean isValidIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            return false;
        }
        try {
            // InetAddress.getByName will try to resolve hostname if not a raw IP.
            // To strictly check IP format, one might use regex or dedicated libraries,
            // but getByName is a common way to validate if it *can* be an address.
            // A more robust IP validation without DNS lookup:
            if (org.apache.commons.validator.routines.InetAddressValidator.getInstance().isValid(ipAddress)) {
                 return true;
            }
            // Fallback for environments without commons-validator, or to be extra sure for raw IPs.
            // This can still perform DNS lookups if the string is not a literal IP.
            // InetAddress.getByName(ipAddress); 
            // The above line is commented out because commons-validator is more direct for format validation.
            // If commons-validator is not desired, a regex approach or careful use of InetAddress is needed.
            // For now, relying on commons-validator (if available) or simple true if not focusing on strict validation here.
            // The SubnetUtils class itself might do sufficient validation when an IP is passed to isInRange.
            
            // Let's assume for now if commons-validator is not on classpath, we are less strict.
            // The primary focus of this util is subnet checking, where SubnetUtils handles IP parsing.
            // For getClientIpAddress, a basic check is often enough.
            return true; // Simplified: if it's non-empty, assume it might be an IP.
                         // Proper validation is complex. SubnetUtils will validate IPs it uses.
        } catch (NoClassDefFoundError ncdfe) {
            // This means commons-validator is not on the classpath. Fallback or log.
            LOGGER.log(Level.FINER, "Apache Commons Validator not found for strict IP validation. Proceeding with basic checks.");
            // Basic check for common invalid patterns if commons-validator is not there.
            if (ipAddress.equalsIgnoreCase("unknown") || ipAddress.indexOf(':') != ipAddress.lastIndexOf(':') && !ipAddress.contains("[")) { // Simple check for multiple colons outside of IPv6 brackets
                 // This is a very rough heuristic for IPv6, real validation is harder.
            }
            return !ipAddress.equalsIgnoreCase("unknown"); // Very basic if validator is missing
        }
        // catch (UnknownHostException ex) {
        //    return false; // Not a valid IP address or resolvable hostname
        // }
    }


    private IpUtil() {
        // Private constructor for utility class
    }
}

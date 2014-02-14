/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2006-2012 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2012 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.poller.monitors;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.regexp.RE;
import org.apache.regexp.RESyntaxException;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.core.utils.ParameterMap;
import org.opennms.core.utils.TimeoutTracker;
import org.opennms.netmgt.model.PollStatus;
import org.opennms.netmgt.poller.Distributable;
import org.opennms.netmgt.poller.MonitoredService;
import org.opennms.netmgt.poller.NetworkInterface;
import org.opennms.netmgt.poller.NetworkInterfaceNotSupportedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <P>
 * This class is designed to be used by the service poller framework to test the
 * availability of the SMTP service on remote interfaces. The class implements
 * the ServiceMonitor interface that allows it to be used along with other
 * plug-ins by the service poller framework.
 * </P>
 *
 * @author <A HREF="mailto:tarus@opennms.org">Tarus Balog </A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS </A>
 */

@Distributable
public final class SmtpMonitor extends AbstractServiceMonitor {
    
    public static final Logger LOG = LoggerFactory.getLogger(SmtpMonitor.class);
    

    /**
     * Default SMTP port.
     */
    private static final int DEFAULT_PORT = 25;

    /**
     * Default retries.
     */
    private static final int DEFAULT_RETRY = 0;

    /**
     * Default timeout. Specifies how long (in milliseconds) to block waiting
     * for data from the monitored interface.
     */
    private static final int DEFAULT_TIMEOUT = 3000;

    /**
     * The name of the local host.
     */
    private static final String LOCALHOST_NAME = InetAddressUtils.getLocalHostName();

    /**
     * Used to check for a multiline response. A multiline response begins with
     * the same 3 digit response code, but has a hyphen after the last number
     * instead of a space.
     */
    private static final RE MULTILINE;

    /**
     * Init MULTILINE
     */
    static {
        try {
            MULTILINE = new RE("^[0-9]{3}-");
        } catch (RESyntaxException ex) {
            throw new java.lang.reflect.UndeclaredThrowableException(ex);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <P>
     * Poll the specified address for SMTP service availability.
     * </P>
     *
     * <P>
     * During the poll an attempt is made to connect on the specified port (by
     * default TCP port 25). If the connection request is successful, the banner
     * line generated by the interface is parsed and if the extracted return
     * code indicates that we are talking to an SMTP server we continue. Next,
     * an SMTP 'HELO' command is sent to the interface. Again the response is
     * parsed and a return code extracted and verified. Finally, an SMTP 'QUIT'
     * command is sent. Provided that the interface's response is valid we set
     * the service status to SERVICE_AVAILABLE and return.
     * </P>
     */
    @Override
    public PollStatus poll(MonitoredService svc, Map<String, Object> parameters) {
        NetworkInterface<InetAddress> iface = svc.getNetInterface();

        // Get interface address from NetworkInterface
        //
        if (iface.getType() != NetworkInterface.TYPE_INET) {
            throw new NetworkInterfaceNotSupportedException("Unsupported interface type, only TYPE_INET currently supported");
        }
        
        TimeoutTracker tracker = new TimeoutTracker(parameters, DEFAULT_RETRY, DEFAULT_TIMEOUT);

        int port = ParameterMap.getKeyedInteger(parameters, "port", DEFAULT_PORT);

        // Get interface address from NetworkInterface
        //
        InetAddress ipAddr = iface.getAddress();

        final String hostAddress = InetAddressUtils.str(ipAddr);
        LOG.debug("poll: address = {}, port = {}, {}", hostAddress, port, tracker);

        PollStatus serviceStatus = PollStatus.unavailable();

        for (tracker.reset(); tracker.shouldRetry() && !serviceStatus.isAvailable(); tracker.nextAttempt()) {
            Socket socket = null;
            try {
                // create a connected socket
                //
                tracker.startAttempt();

                socket = new Socket();
                socket.connect(new InetSocketAddress(ipAddr, port), tracker.getConnectionTimeout());
                socket.setSoTimeout(tracker.getSoTimeout());

                LOG.debug("SmtpMonitor: connected to host: {} on port: {}", ipAddr, port);

                // We're connected, so upgrade status to unresponsive
                serviceStatus = PollStatus.unresponsive();

                BufferedReader rdr = new BufferedReader(new InputStreamReader(socket.getInputStream(), "ASCII"));

                //
                // Tokenize the Banner Line, and check the first
                // line for a valid return.
                //
                String banner = rdr.readLine();

                if (banner == null) {
                    continue;
                }

                if (MULTILINE.match(banner)) {
                    // Ok we have a multi-line response...first three
                    // chars of the response line are the return code.
                    // The last line of the response will start with
                    // return code followed by a space.
                    String multiLineRC = new String(banner.getBytes("ASCII"), 0, 3, "ASCII");

                    // Create new regExp to look for last line
                    // of this multi line response
                    RE endMultiline = null;
                    try {
                        endMultiline = new RE(multiLineRC);
                    } catch (RESyntaxException ex) {
                        throw new java.lang.reflect.UndeclaredThrowableException(ex);
                    }

                    // read until we hit the last line of the multi-line
                    // response
                    do {
                        banner = rdr.readLine();
                    } while (banner != null && !endMultiline.match(banner));
                    if (banner == null) {
                        continue;
                    }
                }

                LOG.debug("poll: banner = {}", banner);

                StringTokenizer t = new StringTokenizer(banner);
                int rc = Integer.parseInt(t.nextToken());
                if (rc == 220) {
                    //
                    // Send the HELO command
                    //
                    String cmd = "HELO " + LOCALHOST_NAME + "\r\n";
                    socket.getOutputStream().write(cmd.getBytes());

                    //
                    // get the returned string, tokenize, and
                    // verify the correct output.
                    //
                    String response = rdr.readLine();
                    double responseTime = tracker.elapsedTimeInMillis();

                    if (response == null) {
                        continue;
                    }

                    if (MULTILINE.match(response)) {
                        // Ok we have a multi-line response...first three
                        // chars of the response line are the return code.
                        // The last line of the response will start with
                        // return code followed by a space.
                        String multiLineRC = new String(response.getBytes("ASCII"), 0, 3, "ASCII");

                        // Create new regExp to look for last line
                        // of this multi line response
                        RE endMultiline = null;
                        try {
                            endMultiline = new RE(multiLineRC);
                        } catch (RESyntaxException ex) {
                            throw new java.lang.reflect.UndeclaredThrowableException(ex);
                        }

                        // read until we hit the last line of the multi-line
                        // response
                        do {
                            response = rdr.readLine();
                        } while (response != null && !endMultiline.match(response));
                        if (response == null) {
                            continue;
                        }
                    }

                    t = new StringTokenizer(response);
                    rc = Integer.parseInt(t.nextToken());
                    if (rc == 250) {
                        cmd = "QUIT\r\n";
                        socket.getOutputStream().write(cmd.getBytes("ASCII"));

                        //
                        // get the returned string, tokenize, and
                        // verify the correct output.
                        //
                        response = rdr.readLine();
                        if (response == null) {
                            continue;
                        }
                        if (MULTILINE.match(response)) {
                            // Ok we have a multi-line response...first three
                            // chars of the response line are the return code.
                            // The last line of the response will start with
                            // return code followed by a space.
                            String multiLineRC = new String(response.getBytes("ASCII"), 0, 3, "ASCII");

                            // Create new regExp to look for last line
                            // of this multi line response
                            RE endMultiline = null;
                            try {
                                endMultiline = new RE(multiLineRC);
                            } catch (RESyntaxException ex) {
                                throw new java.lang.reflect.UndeclaredThrowableException(ex);
                            }

                            // read until we hit the last line of the multi-line
                            // response
                            do {
                                response = rdr.readLine();
                            } while (response != null && !endMultiline.match(response));
                            if (response == null) {
                                continue;
                            }
                        }

                        t = new StringTokenizer(response);
                        rc = Integer.parseInt(t.nextToken());

                        if (rc == 221) {
                            serviceStatus = PollStatus.available(responseTime);
                        }
                    }
                }

                // If we get this far and the status has not been set
                // to available, then something didn't verify during
                // the banner checking or HELO/QUIT comand process.
                if (!serviceStatus.isAvailable()) {
                    serviceStatus = PollStatus.unavailable();
                }
            } catch (NumberFormatException e) {
            	String reason = "NumberFormatException while polling address " + hostAddress;
                LOG.debug(reason, e);
                serviceStatus = PollStatus.unavailable(reason);
            } catch (NoRouteToHostException e) {
            	String reason = "No route to host exception for address " + hostAddress;
                LOG.debug(reason, e);
                serviceStatus = PollStatus.unavailable(reason);
                break; // Break out of for(;;)
            } catch (InterruptedIOException e) {
            	String reason = "Did not receive expected response within timeout " + tracker;
                LOG.debug(reason);
                serviceStatus = PollStatus.unavailable(reason);
            } catch (ConnectException e) {
            	String reason = "Unable to connect to address " + hostAddress;
                LOG.debug(reason, e);
                serviceStatus = PollStatus.unavailable(reason);
            } catch (IOException e) {
            	String reason = "IOException while polling address " + hostAddress;
                LOG.debug(reason, e);
                serviceStatus = PollStatus.unavailable(reason);
            } finally {
                try {
                    // Close the socket
                    if (socket != null) {
                        socket.close();
                    }
                } catch (IOException e) {
                    e.fillInStackTrace();
                    LOG.debug("poll: Error closing socket.", e);
                }
            }
        }

        //
        // return the status of the service
        //
        return serviceStatus;
    }

}

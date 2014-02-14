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

package org.opennms.netmgt.linkd;

import static org.opennms.core.utils.InetAddressUtils.str;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.opennms.netmgt.linkd.scheduler.ReadyRunnable;
import org.opennms.netmgt.linkd.scheduler.Scheduler;
import org.opennms.netmgt.model.DataLinkInterface.DiscoveryProtocol;
import org.opennms.netmgt.model.events.EventBuilder;
import org.opennms.netmgt.model.OnmsStpInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is designed to discover link among nodes using the collected and
 * the necessary SNMP information. When the class is initially constructed no
 * information is used.
 * 
 * @author <a href="mailto:antonio@opennms.it">Antonio Russo </a>
 */
public final class DiscoveryLink implements ReadyRunnable {

    private static final Logger LOG = LoggerFactory.getLogger(DiscoveryLink.class);

    private String packageName;
    
    private List<NodeToNodeLink> m_links = new ArrayList<NodeToNodeLink>();

    private Map<Integer, LinkableNode> m_bridgeNodes = new HashMap<Integer, LinkableNode>();

    private List<LinkableNode> m_routerNodes = new ArrayList<LinkableNode>();

    private List<LinkableNode> m_lldpNodes = new ArrayList<LinkableNode>();

    private List<LinkableNode> m_ospfNodes = new ArrayList<LinkableNode>();

    private List<LinkableNode> m_isisNodes = new ArrayList<LinkableNode>();

    private Map<String,LinkableNode> m_cdpNodes = new HashMap<String,LinkableNode>();

    private List<LinkableNode> m_wifiNodes = new ArrayList<LinkableNode>();

    // this is the list of MAC address just parsed by discovery process
    private List<String> m_macsParsed = new ArrayList<String>();

    // this is the list of MAC address excluded by discovery process
    private List<String> macsExcluded = new ArrayList<String>();

    private boolean discoveryUsingRoutes = true;

    private boolean discoveryUsingCdp = true;

    private boolean discoveryUsingBridge = true;

    private boolean discoveryUsingLldp = true;

    private boolean discoveryUsingOspf = true;

    private boolean discoveryUsingIsis = true;

    private boolean discoveryUsingWifi = true;

    private boolean suspendCollection = false;

    private boolean runned = false;

    /**
     * The scheduler object
     */
    private Scheduler m_scheduler;

    /**
     * The interval default value 30 min
     */
    private long m_interval = 1800000;

    /**
     * The interval default value 5 min It is the time in ms after snmp
     * collection is started
     */
    private long discovery_delay = 300000;

    /**
     * The initial sleep time default value 10 min
     */
    private long m_initial_sleep_time = 600000;

    private Linkd m_linkd;

    /**
     * @param linkd
     *            the linkd to set
     */
    public void setLinkd(Linkd linkd) {
        this.m_linkd = linkd;
    }

    public Linkd getLinkd() {
        return m_linkd;
    }

    /**
     * Constructs a new DiscoveryLink object . The discovery does not occur
     * until the <code>run</code> method is invoked.
     */
    public DiscoveryLink() {
        super();
    }

    private void sendSuspendedEvent() {
        sendEvent(new EventBuilder("uei.opennms.org/internal/linkd/linkDiscoverySuspended",
                                   "Linkd"));
    }

    private void sendStartedEvent() {
        sendEvent(new EventBuilder("uei.opennms.org/internal/linkd/linkDiscoveryStarted",
                                                "Linkd"));
    }

    private void sendCompletedEvent() {
        sendEvent(new EventBuilder("uei.opennms.org/internal/linkd/linkDiscoveryCompleted",
                                           "Linkd"));
    }

    private void sendEvent(EventBuilder builder) {
        builder.addParam("runnable", "discoveryLink/"+getPackageName());
        m_linkd.getEventForwarder().sendNow(builder.getEvent());
    }

    /**
     * <p>
     * Performs link discovery for the Nodes and save info in
     * DatalinkInterface table on DataBase
     * <p>
     * No synchronization is performed, so if this is used in a separate
     * thread context synchronization must be added.
     * </p>
     */
    @Override
    public void run() {
        runned=true;
        if (suspendCollection) {
            sendSuspendedEvent();
            LOG.warn("run: linkd collections are suspended!");
        } else {
            sendStartedEvent();
            discoverLinks();
            sendCompletedEvent();
        }
    }
        
    private void discoverLinks() {   
        Collection<LinkableNode> linkableNodes = m_linkd.getLinkableNodesOnPackage(getPackageName());

        LOG.info("run: Found {} LinkableNodes  on package \"{}\"", linkableNodes.size(), getPackageName());
        LOG.info("run: discoveryUsingBridge={} on package \"{}\"",discoveryUsingBridge,getPackageName());
        LOG.info("run: discoveryUsingCdp={} on package \"{}\"",discoveryUsingCdp,getPackageName());
        LOG.info("run: discoveryUsingRoutes={} on package \"{}\"",discoveryUsingRoutes,getPackageName());
        LOG.info("run: discoveryUsingLldp={} on package \"{}\"",discoveryUsingLldp,getPackageName());
        LOG.info("run: discoveryUsingOspf={} on package \"{}\"",discoveryUsingOspf,getPackageName());
        LOG.info("run: discoveryUsingIsis={} on package \"{}\"",discoveryUsingIsis,getPackageName());
        LOG.info("run: discoveryUsingWifi={} on package \"{}\"",discoveryUsingWifi,getPackageName());
        
        for (final LinkableNode linkableNode : linkableNodes) {
            LOG.debug("run: Iterating on LinkableNode's found node with nodeid/sysoid/ipaddress {}/{}/{}", linkableNode.getNodeId(),linkableNode.getSysoid(),str(linkableNode.getSnmpPrimaryIpAddr()));
            if (discoveryUsingOspf && linkableNode.getOspfRouterId() != null
                    && linkableNode.getOspfinterfaces() != null ) {
                LOG.debug("run: adding to ospf node list: node with nodeid/ospfrouterid/#ospfinterface {}/{}/#{}", linkableNode.getNodeId(),str(linkableNode.getOspfRouterId()),linkableNode.getOspfinterfaces().size());
                m_ospfNodes.add(linkableNode);
            }   
            if (discoveryUsingIsis && linkableNode.getIsisSysId() != null) {
                LOG.debug("run: adding to isis node list: node with nodeid/isisSysId/#isisinterface {}/{}/#{}", linkableNode.getNodeId(),linkableNode.getIsisSysId(),linkableNode.getIsisInterfaces().size());
                m_isisNodes.add(linkableNode);
            }
            if (discoveryUsingLldp && linkableNode.getLldpChassisId() != null
                    && linkableNode.getLldpChassisIdSubtype() != null) {
                LOG.debug("run: adding to lldp node list: node with nodeid/sysname/chassisid {}/{}/{}", linkableNode.getNodeId(),linkableNode.getLldpSysname(),linkableNode.getLldpChassisId());
                m_lldpNodes.add(linkableNode);
            }
            if (discoveryUsingBridge && linkableNode.isBridgeNode()) {
                LOG.debug("run: adding to bridge node list: node with nodeid/bridgeidentifier {}/{}", linkableNode.getNodeId(),linkableNode.getBridgeIdentifiers().get(0));
                m_bridgeNodes.put(Integer.valueOf(linkableNode.getNodeId()),
                                  linkableNode);
            }
            if (discoveryUsingCdp && linkableNode.hasCdpInterfaces()) {
                LOG.debug("run: adding to CDP node list: node with nodeid/#cdpinterfaces {}/#{}", linkableNode.getNodeId(),linkableNode.getCdpInterfaces().size());
                m_cdpNodes.put(linkableNode.getCdpDeviceId(),linkableNode);
            }
            if (discoveryUsingRoutes && linkableNode.hasRouteInterfaces()) {
                LOG.debug("run: adding to router node list: node with nodeid/#iprouteinterface {}/#{}", linkableNode.getNodeId(),linkableNode.getRouteInterfaces().size());
                m_routerNodes.add(linkableNode);
            }
            if (discoveryUsingWifi && !linkableNode.getWifiMacIfIndexMap().isEmpty()) {
                LOG.debug("run: adding to wifi node list: node with nodeid/#wificonnection {}/#{}", linkableNode.getNodeId(),linkableNode.getWifiMacIfIndexMap().size());
                m_wifiNodes.add(linkableNode);
            }
        }

        // This will found all mac address on
        // current package and their association
        // with ip addresses.
        if (discoveryUsingBridge || discoveryUsingWifi)
            populateMacToAtInterface();

        // this part could have several special function to get inter-router
        // links, but at the moment we worked much on switches.
        // In future we can try to extend this part.
        getLinksFromRouteTable();

        getLinksFromOspf();
        getLinksFromIsis();

        getLinksFromWifi();
        // Try Link Layer Discovery Protocol to found link among all nodes
        getLinksFromLldp();
        // try get backbone links between switches using STP info
        // and store information in Bridge class
        // finding links using MAC address on ports
        getBackBoneLinksFromBridges();

        // getting links on remaining bridge ports
        getLinksFromBridges();

        // Try Cisco Discovery Protocol to found link among all nodes
        getLinksFromCdp();

        m_bridgeNodes.clear();
        m_routerNodes.clear();
        m_cdpNodes.clear();
        m_macsParsed.clear();
        macsExcluded.clear();
        m_lldpNodes.clear();
        m_ospfNodes.clear();
        m_isisNodes.clear();
        m_wifiNodes.clear();

        getLinkd().clearPackageSavedData(getPackageName());

        m_linkd.updateDiscoveryLinkCollection(this);

        m_links.clear();
        runned = true;
    }

    protected void populateMacToAtInterface() {
        LOG.debug("populateMacToAtInterface: using atNodes to populate macToAtinterface");
        final Set<String> macAddresses = getLinkd().getMacAddressesOnPackage(getPackageName());
        if (macAddresses == null || macAddresses.isEmpty()) return;
        for (final String macAddress : macAddresses) {
            final List<AtInterface> atInterfaces = getLinkd().getAtInterfaces(getPackageName(), macAddress);
            LOG.debug("populateMacToAtInterface: MAC {} now has atinterface reference: {}", macAddress, atInterfaces.size());
            for (final AtInterface at : atInterfaces) {
                final int nodeid = at.getNodeid();
                LOG.debug("populateMacToAtInterface: Parsing AtInterface nodeid/ipaddr/macaddr: {}/{}/{}", nodeid, at.getIpAddress(), macAddress);
                if ((macAddress.indexOf("00000c07ac") == 0) || (macAddress.indexOf("00000c9ff") == 0)) {
                    LOG.debug("populateMacToAtInterface: AtInterface {} is Cisco HSRP address! Not adding to discoverable atinterface.", macAddress);
                    macsExcluded.add(macAddress);
                    continue;
                }
            }
        }
        LOG.debug("populateMacToAtInterface: end populateMacToAtinterface");
    }
    private void getLinksFromWifi() {
        for (final LinkableNode curNode: m_wifiNodes) {
            final int curNodeId = curNode.getNodeId();
            LOG.info("getLinksFromWifi: parsing wifi node with ID {} and {} wifi interfaces ", curNodeId, curNode.getWifiMacIfIndexMap().size());
            for (Entry<Integer, Set<String>> wifi: curNode.getWifiMacIfIndexMap().entrySet()) {
                LOG.debug("getLinksFromWifi: parsing wifi node with ID {} wifi interface {} macs {} ", curNodeId, wifi.getKey(), wifi.getValue());
                addLinks(wifi.getValue(), curNodeId, wifi.getKey().intValue(),DiscoveryProtocol.wifi);
            }
            
        }
    }
    
    private void getLinksFromBridges() {
        if (m_bridgeNodes.size() > 0) {
            LOG.info("getLinksFromBridges: trying to find links using MAC Address Forwarding Table");
        }

        for (final LinkableNode curNode : m_bridgeNodes.values()) {
            final int curNodeId = curNode.getNodeId();
            LOG.info("getLinksFromBridges: parsing bridge node with ID {}", curNodeId);

            for (final Integer curBridgePort : curNode.getPortMacs().keySet()) {
                LOG.debug("getLinksFromBridges: parsing bridge port {} with MAC address {}", curBridgePort, curNode.getMacAddressesOnBridgePort(curBridgePort).toString());

                if (curNode.isBackBoneBridgePort(curBridgePort)) {
                    LOG.debug("getLinksFromBridges: Port {} is a backbone bridge port. Skipping.", curBridgePort);
                    continue;
                }

                final int curIfIndex = curNode.getIfindex(curBridgePort);
                
                // TT2295 save also ifindex -1 links
                if (curIfIndex == -1) {
                    LOG.warn("getLinksFromBridges: got invalid ifIndex on bridge port {}", curBridgePort);
                //    continue;
                }
                // First get the MAC addresses on bridge port

                final Set<String> macs = curNode.getMacAddressesOnBridgePort(curBridgePort);

                // Then find the bridges whose MAC addresses are learned on
                // bridge port
                final List<LinkableNode> bridgesOnPort = getBridgesFromMacs(macs);

                if (bridgesOnPort.isEmpty()) {
                    LOG.debug("getLinksFromBridges: no bridges macs found on port {}. Saving MACs.", curBridgePort);
                    addLinks(macs, curNodeId, curIfIndex,DiscoveryProtocol.bridge);
                } else {
                    // a bridge MAC address was found on port so you should
                    // analyze what happens
                    LOG.debug("getLinksFromBridges: bridges macs found on port {}. Searching nearest.", curBridgePort);

                    // one among these bridges should be the node more close
                    // to the curnode, curport
                    for (final LinkableNode endNode : bridgesOnPort) {
                        final int endNodeid = endNode.getNodeId();
                        if (curNodeId == endNodeid) {
                            LOG.debug("getLinksFromBridges: curnode and target node are the same. Skipping.");
                            continue;
                        }
                        final int endBridgePort = getBridgePortOnEndBridge(curNode,
                                                                           endNode);
                        // The bridge port should be valid! This control is
                        // not properly done
                        if (endBridgePort == -1) {
                            LOG.warn("getLinksFromBridges: no valid port found on bridge nodeid {} for node bridge identifiers nodeid {}. Skipping.", endNodeid, curNodeId);
                            continue;
                        }

                        // Try to found a new
                        final boolean isTargetNode = isNearestBridgeLink(curNode,
                                                                         curBridgePort,
                                                                         endNode,
                                                                         endBridgePort);
                        if (!isTargetNode)
                            continue;

                        final int endIfindex = endNode.getIfindex(endBridgePort);
                        if (endIfindex == -1) {
                            LOG.warn("getLinksFromBridges: got invalid ifindex on designated bridge port {}", endBridgePort);
                            continue;
                        }

                        LOG.debug("getLinksFromBridges: backbone port found for node {}. Adding backbone bridge port {}", curNodeId, curBridgePort);

                        curNode.addBackBoneBridgePorts(curBridgePort);
                        m_bridgeNodes.put(curNodeId, curNode);

                        LOG.debug("getLinksFromBridges: backbone port found for node {}. Adding to backbone bridge port {}", endNodeid, endBridgePort);

                        endNode.addBackBoneBridgePorts(endBridgePort);
                        m_bridgeNodes.put(endNodeid, endNode);

                        // finding links between two backbone ports
                        // FIXME no criteria....check the rules
                        addLinks(getMacsOnBridgeLink(curNode, curBridgePort,
                                                     endNode, endBridgePort),
                                                     curNodeId, curIfIndex,DiscoveryProtocol.bridge);

                        final NodeToNodeLink lk = new NodeToNodeLink(
                                                                     curNodeId,
                                                                     curIfIndex, DiscoveryProtocol.bridge);
                        lk.setNodeparentid(endNodeid);
                        lk.setParentifindex(endIfindex);
                        LOG.info("getLinksFromBridges: saving bridge link: {}", lk.toString());
                        addNodetoNodeLink(lk);
                    }
                }
            }
            LOG.info("getLinksFromBridges: done parsing bridge node with ID {}", curNodeId);
        }

        if (m_bridgeNodes.size() > 0) {
            LOG.info("getLinksFromBridges: done finding links using MAC Address Forwarding Table");
        }

    }

    private void getBackBoneLinksFromBridges() {
        if (m_bridgeNodes != null && m_bridgeNodes.size() > 0) {
            LOG.info("getBackBoneLinksFromBridges: trying to find backbone ethernet links among bridge nodes using Spanning Tree Protocol");
        }

        for (final LinkableNode curNode : m_bridgeNodes.values()) {
            final int curNodeId = curNode.getNodeId();
            final InetAddress cupIpAddr = curNode.getSnmpPrimaryIpAddr();

            LOG.info("getBackBoneLinksFromBridges: parsing bridge nodeid {} IP address {} with {} VLANs", curNodeId, str(cupIpAddr), curNode.getStpInterfaces().size());

            for (final Map.Entry<Integer, List<OnmsStpInterface>> me : curNode.getStpInterfaces().entrySet()) {
                final Integer vlan = me.getKey();
                final String curBaseBridgeAddress = curNode.getBridgeIdentifier(vlan);

                LOG.debug("getBackBoneLinksFromBridges: found bridge identifier {}", curBaseBridgeAddress);

                String designatedRoot = null;

                if (curNode.hasStpRoot(vlan)) {
                    designatedRoot = curNode.getStpRoot(vlan);
                } else {
                    LOG.debug("getBackBoneLinksFromBridges: stp designated root bridge identifier not found. Skipping {}", curBaseBridgeAddress);
                    continue;
                }

                if (designatedRoot == null
                        || designatedRoot.equals("0000000000000000")) {
                    LOG.warn("getBackBoneLinksFromBridges: stp designated root is invalid, skipping: {}", designatedRoot);
                    continue;
                }
                // check if designated
                // bridge is itself
                // if bridge is STP root bridge itself exiting
                // searching on linkablesnmpnodes

                if (curNode.isBridgeIdentifier(designatedRoot.substring(4))) {
                    LOG.debug("getBackBoneLinksFromBridges: stp designated root is the bridge itself. Skipping.");
                    continue;
                }

                // Now parse STP bridge port info to get designated bridge
                LOG.debug("getBackBoneLinksFromBridges: stp designated root is another bridge. {} Parsing stp interfaces", designatedRoot);

                for (final OnmsStpInterface stpIface : me.getValue()) {
                    // the bridge port number
                    final int stpbridgeport = stpIface.getBridgePort();
                    // if port is a backbone port continue
                    if (curNode.isBackBoneBridgePort(stpbridgeport)) {
                        LOG.debug("getBackBoneLinksFromBridges: bridge port {} already found. Skipping.", stpbridgeport);
                        continue;
                    }

                    final String stpPortDesignatedPort = stpIface.getStpPortDesignatedPort();
                    final String stpPortDesignatedBridge = stpIface.getStpPortDesignatedBridge();

                    LOG.debug("getBackBoneLinksFromBridges: parsing bridge port {} with stp designated bridge {} and stp designated port {}", stpbridgeport, stpPortDesignatedBridge, stpPortDesignatedPort);

                    if (stpPortDesignatedBridge == null
                            || stpPortDesignatedBridge.equals("0000000000000000")
                            || stpPortDesignatedBridge.equals("")) {
                        LOG.warn("getBackBoneLinksFromBridges: designated bridge is invalid, skipping: {}", stpPortDesignatedBridge);
                        continue;
                    }

                    if (curNode.isBridgeIdentifier(stpPortDesignatedBridge.substring(4))) {
                        LOG.debug("getBackBoneLinksFromBridges: designated bridge for port {} is bridge itself, skipping", stpbridgeport);
                        continue;
                    }

                    if (stpPortDesignatedPort == null
                            || stpPortDesignatedPort.equals("0000")) {
                        LOG.warn("getBackBoneLinksFromBridges: designated port is invalid: {}. skipping", stpPortDesignatedPort);
                        continue;
                    }

                    // A Port Identifier shall be encoded as two octets,
                    // taken to represent an unsigned binary number. If
                    // two Port Identifiers are numerically compared, the
                    // lesser number denotes the Port of better priority.
                    // The more significant octet of a Port Identifier is
                    // a settable priority component that permits the
                    // relative priority of Ports on the same Bridge to be
                    // managed (17.13.7 and Clause 14). The less
                    // significant twelve bits is the Port Number
                    // expressed as an unsigned binary number. The value 0
                    // is not used as a Port Number. NOTE -- The number of
                    // bits that are considered to be part of the Port
                    // Number (12 bits) differs from the 1998 and prior
                    // versions of this standard (formerly, the priority
                    // component was 8 bits and the Port Number component
                    // also 8 bits). This change acknowledged that modern
                    // switched LAN infrastructures call for increasingly
                    // large numbers of Ports to be supported in a single
                    // Bridge. To maintain management compatibility with
                    // older implementations, the priority component is
                    // still considered, for management purposes, to be an
                    // 8-bit value, but the values that it can be set to
                    // are restricted to those where the least significant
                    // 4 bits are zero (i.e., only the most significant 4
                    // bits are settable).
                    int designatedbridgeport = 8191 & Integer.parseInt(stpPortDesignatedPort,
                                                                16);

                    // try to see if designated bridge is linkable SNMP node

                    final LinkableNode designatedNode = getNodeFromMacIdentifierOfBridgeNode(stpPortDesignatedBridge.substring(4));

                    if (designatedNode == null) {
                        LOG.debug("getBackBoneLinksFromBridges: no nodeid found for stp bridge address {}. Nothing to save.", stpPortDesignatedBridge);
                        continue; // no saving info if no nodeid
                    }

                    final int designatednodeid = designatedNode.getNodeId();

                    LOG.debug("getBackBoneLinksFromBridges: found designated nodeid {}", designatednodeid);

                    if (!isNearestBridgeLink(curNode, stpbridgeport,
                                             designatedNode,
                                             designatedbridgeport)) {
                        continue; // no saving info if no nodeid
                    }

                    // this is a backbone port so try adding to Bridge class
                    // get the ifindex on node

                    final int curIfIndex = curNode.getIfindex(stpbridgeport);

                    if (curIfIndex == -1) {
                        LOG.warn("getBackBoneLinksFromBridges: got invalid ifindex on node: {}", curNode.toString());
                        continue;
                    }

                    final int designatedifindex = designatedNode.getIfindex(designatedbridgeport);

                    if (designatedifindex == -1) {
                        LOG.warn("getBackBoneLinksFromBridges: got invalid ifindex on designated node: {}", designatedNode.toString());
                        continue;
                    }

                    LOG.debug("getBackBoneLinksFromBridges: backbone bridge port {} found for node {}", stpbridgeport, curNodeId);

                    curNode.addBackBoneBridgePorts(stpbridgeport);
                    m_bridgeNodes.put(Integer.valueOf(curNodeId), curNode);

                    LOG.debug("getBackBoneLinksFromBridges: backbone bridge port {} found for node {}", designatedbridgeport, designatednodeid);

                    designatedNode.addBackBoneBridgePorts(designatedbridgeport);
                    m_bridgeNodes.put(Integer.valueOf(designatednodeid),
                                      designatedNode);

                    LOG.debug("getBackBoneLinksFromBridges: adding links on backbone found link");

                    addLinks(getMacsOnBridgeLink(curNode, stpbridgeport,
                                                 designatedNode,
                                                 designatedbridgeport),
                                                 curNodeId, curIfIndex,DiscoveryProtocol.bridge);

                    final NodeToNodeLink lk = new NodeToNodeLink(curNodeId,
                                                                 curIfIndex,DiscoveryProtocol.bridge);
                    lk.setNodeparentid(designatednodeid);
                    lk.setParentifindex(designatedifindex);
                    LOG.info("getBackBoneLinksFromBridges: saving stp bridge link: {}", lk.toString());
                    addNodetoNodeLink(lk);

                }
            }
            LOG.info("getBackBoneLinksFromBridges: done parsing bridge with nodeid {} and ip address {} with {} VLANs", curNodeId, str(cupIpAddr), curNode.getStpInterfaces().size());
        }

        if (m_bridgeNodes.size() > 0) {
            LOG.info("getBackBoneLinksFromBridges: done finding backbone ethernet links among bridge nodes using Spanning Tree Protocol");
        }

    }

    private void getLinksFromRouteTable() {
        if (m_routerNodes.size() > 0) {
            LOG.info("getLinksFromRouteTable: finding non-ethernet links on Router nodes");
        }

        for (final LinkableNode curNode : m_routerNodes) {
            final int curNodeId = curNode.getNodeId();
            InetAddress curIpAddr = curNode.getSnmpPrimaryIpAddr();
            LOG.info("getLinksFromRouteTable: parsing router node with ID {} IP address {} and {} router interfaces", curNodeId, str(curIpAddr), curNode.getRouteInterfaces().size());

            for (final RouterInterface routeIface : curNode.getRouteInterfaces()) {
                LOG.debug("getLinksFromRouteTable: parsing RouterInterface: {}", routeIface.toString());

                final NodeToNodeLink lk = new NodeToNodeLink(curNodeId,routeIface.getIfindex(),DiscoveryProtocol.iproute);
                lk.setNodeparentid(routeIface.getNextHopNodeid());
                lk.setParentifindex(routeIface.getNextHopIfindex());
                LOG.info("getLinksFromRouteTable: saving route link: {}", lk.toString());
                addNodetoNodeLink(lk);
            }
            LOG.info("getLinksFromRouteTable: done parsing router node with ID {} IP address {} and {} router interfaces", curNodeId, str(curIpAddr), curNode.getRouteInterfaces().size());
        }

        if (m_routerNodes.size() > 0) {
            LOG.info("getLinksFromRouteTable: done finding non-ethernet links on Router nodes");
        }

    }
    
    private void getLinksFromCdp() {
        LOG.info("getLinksFromCdp: adding links using Cisco Discovery Protocol");

        LOG.info("getLinksFromCdp: found # {} nodes using Cisco Discovery Protocol", m_cdpNodes.size());

        LOG.info("getLinksFromCdp: founding Cisco Discovery Protocol links between Cdp nodes");
        for (LinkableNode linknode1: m_cdpNodes.values()) {
            LOG.info("getLinksFromCdp: parsing cdp device {} with cdpDeviceId {} using Cisco Discovery Protocol",linknode1.getNodeId(), linknode1.getCdpDeviceId());
            for (CdpInterface cdpiface1: linknode1.getCdpInterfaces()) {
                if (cdpiface1 == null) {
                    LOG.warn("getLinksFromCdp: cdp interface null found on target device node {} for cdpTargetDeviceId {} ", linknode1.getNodeId());
                    continue;
                }
                LOG.info("getLinksFromCdp: parsing cdpInterface {} ", cdpiface1);
                if (cdpiface1.getCdpTargetDeviceId() != null) {
                    LinkableNode linknode2 = m_cdpNodes.get(cdpiface1.getCdpTargetDeviceId());
                    if (linknode2 == null) {
                        LOG.info("getLinksFromCdp: no cdpdevice found for cdpDeviceId {} ", cdpiface1.getCdpTargetDeviceId());
                        continue;
                    }
                    if (linknode1.getNodeId() >= linknode2.getNodeId())
                        continue;
                    LOG.info("getLinksFromCdp: found node {} for cdpTargetDeviceId {} ", linknode2.getNodeId(), cdpiface1.getCdpTargetDeviceId());
                    
                    for (CdpInterface cdpiface2: linknode2.getCdpInterfaces()) {
                        if (cdpiface2 == null) {
                            LOG.warn("getLinksFromCdp: cdp interface null found on target device node {} for cdpTargetDeviceId {} ", linknode2.getNodeId(), cdpiface1.getCdpTargetDeviceId());
                            continue;
                        }
                        LOG.info("getLinksFromCdp: parsing target cdpInterface {} ", cdpiface2);
                        if ( cdpiface2.getCdpTargetDeviceId() != null 
                                && cdpiface2.getCdpTargetDeviceId().equals(linknode1.getCdpDeviceId())) {
                                if(    (cdpiface1.getCdpIfName() != null && cdpiface1.getCdpIfName().equals(cdpiface2.getCdpTargetIfName()))
                                    || (cdpiface2.getCdpIfName() != null && cdpiface2.getCdpIfName().equals(cdpiface1.getCdpTargetIfName()))
                                    ) {
                            
                                    NodeToNodeLink cdpLink = new NodeToNodeLink(linknode2.getNodeId(), cdpiface2.getCdpIfIndex(),DiscoveryProtocol.cdp);
                                    cdpLink.setNodeparentid(linknode1.getNodeId());
                                    cdpLink.setParentifindex(cdpiface1.getCdpIfIndex());
                                    addNodetoNodeLink(cdpLink);
                                }
                        }
                    }
                } else if (cdpiface1.getCdpTargetNodeId() != null) {
                    LOG.info("getLinksFromCdp: cdpdevice found no snmp target node {} for cdpTargetDeviceId {} ", cdpiface1.getCdpTargetNodeId(), cdpiface1.getCdpTargetDeviceId());
                    NodeToNodeLink link = new NodeToNodeLink(cdpiface1.getCdpTargetNodeId(), -1,DiscoveryProtocol.cdp);
                    link.setNodeparentid(linknode1.getNodeId());
                    link.setParentifindex(cdpiface1.getCdpIfIndex());
                    addNodetoNodeLink(link);
                }
            }
        }
                
    }

    // We use a simple algoritm
    // to find links.
    // If node1 has a isis IS adj entry for node2
    // then node2 mast have an ospf nbr entry for node1
    // the parent node is that with nodeid1 < nodeid2
    private void getLinksFromIsis() {
        LOG.info("getLinksFromIsis: adding links using ISO IS-IS Routing Protocol");
        int i = 0;
        for (LinkableNode linknode1 : m_isisNodes) {
            for (LinkableNode linknode2 : m_isisNodes) {
                if (linknode1.getNodeId() >= linknode2.getNodeId())
                    continue;
                for (NodeToNodeLink isisLink : getIsisLink(linknode1,
                                                           linknode2)) {
                    addNodetoNodeLink(isisLink);
                    i++;
                }
            }
        }
        LOG.info("getLinksFromIsis: done IS-IS. Found links # {}.", i);
    }

    private List<NodeToNodeLink> getIsisLink(LinkableNode linknode1,
                                             LinkableNode linknode2) {
        
        LOG.info("getIsisLink: finding IS-IS links between node with id {} and node with id {}.", linknode1.getNodeId(), linknode2.getNodeId());
        List<NodeToNodeLink> links = new ArrayList<NodeToNodeLink>();
        for (IsisISAdjInterface isis1: linknode1.getIsisInterfaces()) {
            for (IsisISAdjInterface isis2: linknode2.getIsisInterfaces()) {
                LOG.debug("getIsisLink: first IS-IS element: isisSysId {} isisISAdj {}.", linknode1.getIsisSysId(), isis1);
                LOG.debug("getIsisLink: second IS-IS element: isisSysId {} isisISAdj {}.", linknode2.getIsisSysId(), isis2);
                if (isis1.getIsisISAdjNeighSysId().equals(linknode2.getIsisSysId()) && isis2.getIsisISAdjNeighSysId().equals(linknode1.getIsisSysId())
                        && isis1.getIsisISAdjIndex().intValue() == isis2.getIsisISAdjIndex().intValue()) {
                    NodeToNodeLink link = new NodeToNodeLink(linknode1.getNodeId(), isis1.getIsisLocalIfIndex(),DiscoveryProtocol.isis);
                    link.setNodeparentid(linknode2.getNodeId());
                    link.setParentifindex(isis2.getIsisLocalIfIndex());
                    links.add(link);
                }
            }
        }
        return links;
    }

    // We use a simple algoritm
    // to find links.
    // If node1 has a ospf nbr entry for node2
    // then node2 mast have an ospf nbr entry for node1
    // the parent node is that with nodeid1 < nodeid2
    private void getLinksFromOspf() {
        LOG.info("getLinksFromOspf: adding links using Open Short Path First Protocol");
        int i = 0;
        for (LinkableNode linknode1 : m_ospfNodes) {
            for (LinkableNode linknode2 : m_ospfNodes) {
                if (linknode1.getNodeId() >= linknode2.getNodeId())
                    continue;
                for (NodeToNodeLink ospfLink : getOspfLink(linknode1,
                                                           linknode2)) {
                    addNodetoNodeLink(ospfLink);
                    i++;
                }
            }
        }
        LOG.info("getLinksFromOspf: done OSPF. Found links # {}.", i);
    }

    private List<NodeToNodeLink> getOspfLink(LinkableNode linknode1,
                                             LinkableNode linknode2) {
        LOG.info("getLinksFromOspf: finding OSPF links between node with id {} and node with id {}.", linknode1.getNodeId(), linknode2.getNodeId());
        List<NodeToNodeLink> links = new ArrayList<NodeToNodeLink>();
        for (OspfNbrInterface ospf: linknode1.getOspfinterfaces()) {
            for (OspfNbrInterface ospf2: linknode2.getOspfinterfaces()) {
                if (ospf.getOspfNbrRouterId().equals(linknode2.getOspfRouterId()) && ospf.getOspfNbrNodeId() == linknode2.getNodeId() 
                        && ospf2.getOspfNbrRouterId().equals(linknode1.getOspfRouterId()) && ospf2.getOspfNbrNodeId() == linknode1.getNodeId()) {
                    if (getSubnetAddress(ospf).equals(getSubnetAddress(ospf2))) {
                        NodeToNodeLink link = new NodeToNodeLink(ospf.getOspfNbrNodeId(), ospf.getOspfNbrIfIndex(),DiscoveryProtocol.ospf);
                        link.setNodeparentid(ospf2.getOspfNbrNodeId());
                        link.setParentifindex(ospf2.getOspfNbrIfIndex());
                        links.add(link);
                    }
                }                
            }
        }
        return links;
    }


    protected InetAddress getSubnetAddress(OspfNbrInterface ospfinterface) {
        byte[] ip = ospfinterface.getOspfNbrIpAddr().getAddress();
        byte[] nm = ospfinterface.getOspfNbrNetMask().getAddress();
        try {
            return InetAddress.getByAddress(new byte[]{ 
                    (byte) (ip[0] & nm[0]), (byte) (ip[1] & nm[1]),(byte) (ip[2] & nm[2]), (byte) (ip[3] & nm[3])
            });
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        return null;
    }

    // We use a simple algoritm
    // to find links.
    // If node1 has a lldp rem entry for node2
    // then node2 mast have an lldp rem entry for node1
    // the parent node is that with nodeid1 < nodeid2
    private void getLinksFromLldp() {
        LOG.info("getLinkdFromLldp: adding links using Layer Link Discovery Protocol");
        int i = 0;
        for (LinkableNode linknode1 : m_lldpNodes) {
            for (LldpRemInterface lldpremiface : linknode1.getLldpRemInterfaces()) {
                LOG.debug("run: found LLDP interface {}", lldpremiface.toString());
                NodeToNodeLink link = new NodeToNodeLink(
                                                         lldpremiface.getLldpRemNodeid(),
                                                         lldpremiface.getLldpRemIfIndex(),DiscoveryProtocol.lldp);
                link.setNodeparentid(linknode1.getNodeId());
                link.setParentifindex(lldpremiface.getLldpLocIfIndex());
                addNodetoNodeLink(link);
                i++;
            }
        }

        LOG.info("getLinkdFromLldp: done LLDP. Found links # {}.", i);

    }

    /**
     * @param nodeid
     * @return LinkableSnmpNode or null if not found
     */
    boolean isBridgeNode(int nodeid) {
        for (final LinkableNode curNode : m_bridgeNodes.values()) {
            if (nodeid == curNode.getNodeId())
                return true;
        }
        return false;
    }

    /**
     * @param nodeid
     * @return true if found
     */
    boolean isRouterNode(int nodeid) {
        for (final LinkableNode curNode : m_routerNodes) {
            if (nodeid == curNode.getNodeId())
                return true;
        }
        return false;
    }

    private boolean isNearestBridgeLink(LinkableNode bridge1, int bp1,
            LinkableNode bridge2, int bp2) {

        LOG.debug("isNearestBridgeLink: bridge1/port1 {}/{} bridge2/port2 {}/{}", bridge1.getNodeId(), bp1, bridge2.getNodeId(), bp2);

        Set<String> macsOnBridge2 = bridge2.getMacAddressesOnBridgePort(bp2);

        Set<String> macsOnBridge1 = bridge1.getMacAddressesOnBridgePort(bp1);

        if (macsOnBridge2 == null || macsOnBridge1 == null
                || macsOnBridge2.isEmpty() || macsOnBridge1.isEmpty()) {
            LOG.debug("isNearestBridgeLink: no macs found on at least one bridge port, nearest bridges found. Return true.");
            return true;
        }

        for (final String curMacOnBridge1 : macsOnBridge1) {
            LOG.debug("isNearestBridgeLink: parsing mac address {} on bridge1", curMacOnBridge1);

            // if MAC address is bridge identifier of bridge 2 continue
            if (bridge2.isBridgeIdentifier(curMacOnBridge1)) {
                LOG.debug("isNearestBridgeLink: mac address {} is bridge identifier on bridge2. Continue", curMacOnBridge1);
                continue;
            }
            // if MAC address is itself identifier of bridge1 continue
            if (bridge1.isBridgeIdentifier(curMacOnBridge1)) {
                LOG.debug("isNearestBridgeLink: mac address {} is bridge identifier on bridge1. Continue", curMacOnBridge1);
                continue;
            }
            // then no identifier of bridge one no identifier of bridge 2
            // bridge 2 contains
            if (macsOnBridge2.contains(curMacOnBridge1)
                    && isMacIdentifierOfBridgeNode(curMacOnBridge1)) {
                LOG.debug("isNearestBridgeLink: mac address {} is bridge identifier. Other bridge found. Return false", curMacOnBridge1);
                return false;
            }
        }

        return true;
    }

    private Set<String> getMacsOnBridgeLink(LinkableNode bridge1, int bp1,
                                            LinkableNode bridge2, int bp2) {

        Set<String> macsOnLink = new HashSet<String>();

        Set<String> macsOnBridge1 = bridge1.getMacAddressesOnBridgePort(bp1);

        Set<String> macsOnBridge2 = bridge2.getMacAddressesOnBridgePort(bp2);

        if (macsOnBridge2 == null || macsOnBridge1 == null)
            return null;

        if (macsOnBridge2.isEmpty() || macsOnBridge1.isEmpty())
            return null;

        for (final String curMacOnBridge1 : macsOnBridge1) {
            if (bridge2.isBridgeIdentifier(curMacOnBridge1))
                continue;
            if (macsOnBridge2.contains(curMacOnBridge1))
                macsOnLink.add(curMacOnBridge1);
        }
        return macsOnLink;
    }

    private boolean isMacIdentifierOfBridgeNode(String macAddress) {
        for (final LinkableNode curNode : m_bridgeNodes.values()) {
            if (curNode.isBridgeIdentifier(macAddress))
                return true;
        }
        return false;
    }

    private LinkableNode getNodeFromMacIdentifierOfBridgeNode(
            final String macAddress) {
        for (final LinkableNode curNode : m_bridgeNodes.values()) {
            if (curNode.isBridgeIdentifier(macAddress))
                return curNode;
        }
        return null;
    }

    private List<LinkableNode> getBridgesFromMacs(final Set<String> macs) {
        List<LinkableNode> bridges = new ArrayList<LinkableNode>();
        for (final LinkableNode curNode : m_bridgeNodes.values()) {
            for (final String curBridgeIdentifier : curNode.getBridgeIdentifiers()) {
                if (macs.contains((curBridgeIdentifier)))
                    bridges.add(curNode);
            }
        }
        return bridges;
    }

    private int getBridgePortOnEndBridge(final LinkableNode startBridge,
            final LinkableNode endBridge) {

        int port = -1;
        for (final String curBridgeIdentifier : startBridge.getBridgeIdentifiers()) {
            LOG.debug("getBridgePortOnEndBridge: parsing bridge identifier {}", curBridgeIdentifier);

            if (endBridge.hasMacAddress(curBridgeIdentifier)) {
                for (final Integer p : endBridge.getBridgePortsFromMac(curBridgeIdentifier)) {
                    port = p;
                    if (endBridge.isBackBoneBridgePort(port)) {
                        LOG.debug("getBridgePortOnEndBridge: found backbone bridge port {} .... Skipping.", port);
                        continue;
                    }
                    if (port == -1) {
                        LOG.debug("getBridgePortOnEndBridge: no port found on bridge nodeid {} for node bridge identifiers nodeid {} . .....Skipping.", endBridge.getNodeId(), startBridge.getNodeId());
                        continue;
                    }
                    LOG.debug("getBridgePortOnEndBridge: using MAC address table found bridge port {} on node {}", port, endBridge.getNodeId());
                    return port;
                }

            } else {
                LOG.debug("getBridgePortOnEndBridge: bridge identifier not found on node {}", endBridge.getNodeId());
            }
        }
        return -1;
    }

    /**
     * Return the Scheduler
     * 
     * @return a {@link org.opennms.netmgt.linkd.scheduler.Scheduler} object.
     */
    public Scheduler getScheduler() {
        return m_scheduler;
    }

    /**
     * Set the Scheduler
     * 
     * @param scheduler
     *            a {@link org.opennms.netmgt.linkd.scheduler.Scheduler}
     *            object.
     */
    public void setScheduler(Scheduler scheduler) {
        m_scheduler = scheduler;
    }

    /**
     * This Method is called when DiscoveryLink is initialized
     */
    @Override
    public void schedule() {
        if (m_scheduler == null)
            throw new IllegalStateException(
                    "schedule: Cannot schedule a service whose scheduler is set to null");

        if (runned)
            m_scheduler.schedule(m_interval, this);
        else
            m_scheduler.schedule(discovery_delay + m_initial_sleep_time, this);
    }

    /**
     * <p>
     * getInitialSleepTime
     * </p>
     * 
     * @return Returns the initial_sleep_time.
     */
    public long getInitialSleepTime() {
        return m_initial_sleep_time;
    }

    /**
     * <p>
     * setInitialSleepTime
     * </p>
     * 
     * @param initial_sleep_time
     *            The initial_sleep_timeto set.
     */
    public void setInitialSleepTime(long initial_sleep_time) {
        m_initial_sleep_time = initial_sleep_time;
    }

    /**
     * <p>
     * isReady
     * </p>
     * 
     * @return a boolean.
     */
    @Override
    public boolean isReady() {
        return true;
    }

    /**
     * <p>
     * getDiscoveryInterval
     * </p>
     * 
     * @return Returns the discovery_link_interval.
     */
    public long getDiscoveryDelay() {
        return discovery_delay;
    }

    /**
     * <p>
     * setSnmpPollInterval
     * </p>
     * 
     * @param interval
     *            The discovery_link_interval to set.
     */
    public void setInterval(long interval) {
        m_interval = interval;
    }

    /**
     * <p>
     * getSnmpPollInterval
     * </p>
     * 
     * @return Returns the discovery_link_interval.
     */
    public long getInterval() {
        return m_interval;
    }

    /**
     * <p>
     * setDiscoveryInterval
     * </p>
     * 
     * @param interval
     *            The discovery_link_interval to set.
     */
    public void setDiscoveryInterval(long interval) {
        this.discovery_delay = interval;
    }

    /**
     * <p>
     * Getter for the field <code>links</code>.
     * </p>
     * 
     * @return an array of {@link org.opennms.netmgt.linkd.NodeToNodeLink}
     *         objects.
     */
    public NodeToNodeLink[] getLinks() {
        return m_links.toArray(new NodeToNodeLink[0]);
    }

    /**
     * <p>
     * isSuspended
     * </p>
     * 
     * @return Returns the suspendCollection.
     */
    @Override
    public boolean isSuspended() {
        return suspendCollection;
    }

    /**
     * <p>
     * suspend
     * </p>
     */
    @Override
    public void suspend() {
        this.suspendCollection = true;
    }

    /**
     * <p>
     * wakeUp
     * </p>
     */
    @Override
    public void wakeUp() {
        this.suspendCollection = false;
    }

    /**
     * <p>
     * unschedule
     * </p>
     */
    @Override
    public void unschedule() {
        if (m_scheduler == null)
            throw new IllegalStateException(
                    "unschedule: Cannot schedule a service whose scheduler is set to null");
        if (runned) {
            m_scheduler.unschedule(this, m_interval);
        } else {
            m_scheduler.unschedule(this, m_initial_sleep_time + discovery_delay);
        }
    }

    private void addNodetoNodeLink(NodeToNodeLink nnlink) {
        if (nnlink == null) {
            LOG.warn("addNodetoNodeLink: node link is null.");
            return;
        }
        for (NodeToNodeLink curNnLink : m_links) {
            if (curNnLink.equals(nnlink)) {
                LOG.info("addNodetoNodeLink: link {} exists, not adding", nnlink.toString());
                return;
            }
        }
        if (nnlink.getNodeId() == nnlink.getNodeparentid()) {
            LOG.info("addNodetoNodeLink: link {} is on the same node, not adding", nnlink.toString());
            return;
        }
        LOG.debug("addNodetoNodeLink: adding link {}", nnlink.toString());
        m_links.add(nnlink);
    }

    private void addLinks(Set<String> macs, int nodeid, int ifindex, DiscoveryProtocol proto) {
        if (macs == null || macs.isEmpty()) {
            LOG.debug("addLinks: MAC address list on link is empty.");
        } else {
            for (String curMacAddress : macs) {
                if (m_macsParsed.contains(curMacAddress)) {
                    LOG.warn("addLinks: MAC address {} just found on other bridge port! Skipping...", curMacAddress);
                    continue;
                }

                if (macsExcluded.contains(curMacAddress)) {
                    LOG.warn("addLinks: MAC address {} is excluded from discovery package! Skipping...", curMacAddress);
                    continue;
                }
                final List<AtInterface> ats = m_linkd.getAtInterfaces(getPackageName(), curMacAddress);
                if (!ats.isEmpty()) {
                    for (final AtInterface at : ats) {
                        final NodeToNodeLink lNode = new NodeToNodeLink(at.getNodeid(), at.getIfIndex(),proto);
                        lNode.setNodeparentid(nodeid);
                        lNode.setParentifindex(ifindex);
                        addNodetoNodeLink(lNode);
                    }
                }
                m_macsParsed.add(curMacAddress);
            }
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object r) {
        return (r instanceof DiscoveryLink && this.getPackageName().equals(((DiscoveryLink)r).getPackageName()));
    }

    /**
     * <p>
     * getInfo
     * </p>
     * 
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String getInfo() {
        return " Ready Runnable DiscoveryLink " 
                + " package=" + getPackageName() 
                + " sleep=" + getInitialSleepTime() 
                + " discovery=" + getDiscoveryDelay() 
                + " interval=" + getInterval() 
                + " discoveryUsingBridge=" + discoveryUsingBridge() 
                + " discoveryUsingCdp=" + discoveryUsingCdp()    
                + " discoveryUsingRoutes=" + discoveryUsingRoutes() 
                + " discoveryUsingLldp=" + discoveryUsingLldp()   
                + " discoveryUsingOspf=" + discoveryUsingOspf()   
                + " discoveryUsingIsis=" + discoveryUsingIsis()
                + " discoveryUsingWifi=" + discoveryUsingWifi();
    }

    /**
     * <p>
     * discoveryUsingBridge
     * </p>
     * 
     * @return a boolean.
     */
    public boolean discoveryUsingBridge() {
        return discoveryUsingBridge;
    }

    /**
     * <p>
     * Setter for the field <code>discoveryUsingBridge</code>.
     * </p>
     * 
     * @param discoveryUsingBridge
     *            a boolean.
     */
    public void setDiscoveryUsingBridge(boolean discoveryUsingBridge) {
        this.discoveryUsingBridge = discoveryUsingBridge;
    }

    /**
     * <p>
     * discoveryUsingLldp
     * </p>
     * 
     * @return a boolean.
     */
    public boolean discoveryUsingOspf() {
        return discoveryUsingOspf;
    }

    /**
     * <p>
     * Setter for the field <code>discoveryUsingOspf</code>.
     * </p>
     * 
     * @param discoveryUsingOspf
     *            a boolean.
     */
    public void setDiscoveryUsingOspf(boolean discoveryUsingOspf) {
        this.discoveryUsingOspf = discoveryUsingOspf;
    }

    /**
     * <p>
     * discoveryUsingIsIs
     * </p>
     * 
     * @return a boolean.
     */
    public boolean discoveryUsingIsis() {
        return discoveryUsingIsis;
    }

    /**
     * <p>
     * Setter for the field <code>discoveryUsingIsIs</code>.
     * </p>
     * 
     * @param discoveryUsingIsIs
     *            a boolean.
     */
    public void setDiscoveryUsingIsIs(boolean discoveryUsingIsIs) {
        this.discoveryUsingIsis = discoveryUsingIsIs;
    }

    /**
     * <p>
     * discoveryUsingLldp
     * </p>
     * 
     * @return a boolean.
     */
    public boolean discoveryUsingLldp() {
        return discoveryUsingLldp;
    }

    /**
     * <p>
     * Setter for the field <code>discoveryUsingLldp</code>.
     * </p>
     * 
     * @param discoveryUsingLldp
     *            a boolean.
     */
    public void setDiscoveryUsingLldp(boolean discoveryUsingLldp) {
        this.discoveryUsingLldp = discoveryUsingLldp;
    }

    /**
     * <p>
     * discoveryUsingCdp
     * </p>
     * 
     * @return a boolean.
     */
    public boolean discoveryUsingCdp() {
        return discoveryUsingCdp;
    }

    /**
     * <p>
     * Setter for the field <code>discoveryUsingCdp</code>.
     * </p>
     * 
     * @param discoveryUsingCdp
     *            a boolean.
     */
    public void setDiscoveryUsingCdp(boolean discoveryUsingCdp) {
        this.discoveryUsingCdp = discoveryUsingCdp;
    }

    /**
     * <p>
     * discoveryUsingWifi
     * </p>
     * 
     * @return a boolean.
     */
    public boolean discoveryUsingWifi() {
        return discoveryUsingWifi;
    }

    /**
     * <p>
     * Setter for the field <code>discoveryUsingWifi</code>.
     * </p>
     * 
     * @param discoveryUsingCdp
     *            a boolean.
     */
    public void setDiscoveryUsingWifi(boolean discoveryUsingWifi) {
        this.discoveryUsingWifi = discoveryUsingWifi;
    }

    /**
     * <p>
     * discoveryUsingRoutes
     * </p>
     * 
     * @return a boolean.
     */
    public boolean discoveryUsingRoutes() {
        return discoveryUsingRoutes;
    }

    /**
     * <p>
     * Setter for the field <code>discoveryUsingRoutes</code>.
     * </p>
     * 
     * @param discoveryUsingRoutes
     *            a boolean.
     */
    public void setDiscoveryUsingRoutes(boolean discoveryUsingRoutes) {
        this.discoveryUsingRoutes = discoveryUsingRoutes;
    }

    /**
     * <p>
     * Getter for the field <code>packageName</code>.
     * </p>
     * 
     * @return a {@link java.lang.String} object.
     */
    @Override
    public String getPackageName() {
        return packageName;
    }

    /** {@inheritDoc} */
    @Override
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

}

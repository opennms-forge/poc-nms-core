/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2011-2012 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.snmp;

import java.io.Serializable;
import java.net.InetAddress;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author (various previous authors not documented)
 * @author <a href="mailto:david@opennms.org">David Hustace</a>
 */
@XmlRootElement(name = "snmpAgentConfig")
public class SnmpAgentConfig extends SnmpConfiguration implements Serializable {

	private static final long serialVersionUID = 1456963719970029200L;

	private static final transient Logger LOG = LoggerFactory.getLogger(SnmpAgentConfig.class);

    private InetAddress m_address;
    private InetAddress m_proxyFor;

    public SnmpAgentConfig() {
        this(null);
    }

    public SnmpAgentConfig(InetAddress agentAddress) {
        this(agentAddress, SnmpConfiguration.DEFAULTS);
    }

    public SnmpAgentConfig(InetAddress agentAddress, SnmpConfiguration defaults) {
        super(defaults);
        m_address = agentAddress;
    }

    public static SnmpAgentConfig parseProtocolConfigurationString(String protocolConfigString) {
        if (protocolConfigString == null) {
            throw new IllegalArgumentException("Protocol configuration string for SnmpAgentConfig must not be null.");
        }
        if (!protocolConfigString.startsWith("snmp:")) {
            throw new IllegalArgumentException("Invalid protocol configuration string for SnmpAgentConfig: Expected it to start with snmp:" + protocolConfigString);
        }

        SnmpAgentConfig agentConfig = new SnmpAgentConfig();

        String[] attributes = protocolConfigString.substring("snmp:".length()).split(",");

        for (String attribute : attributes) {
            String[] pair = attribute.split("=");
            if (pair.length != 2) {
                throw new IllegalArgumentException("unexpected format for key value pair in SnmpAgentConfig configuration string" + attribute);
            }

            String key = pair[0];
            String value = pair[1];

            if ("address".equalsIgnoreCase(key) && !"null".equals(value)) {
                agentConfig.setAddress(InetAddrUtils.addr(value));
            } else if ("port".equalsIgnoreCase(key)) {
                agentConfig.setPort(Integer.parseInt(value));
            } else if ("timeout".equalsIgnoreCase(key)) {
                agentConfig.setTimeout(Integer.parseInt(value));
            } else if ("retries".equalsIgnoreCase(key)) {
                agentConfig.setRetries(Integer.parseInt(value));
            } else if ("max-vars-per-pdu".equalsIgnoreCase(key)) {
                agentConfig.setMaxVarsPerPdu(Integer.parseInt(value));
            } else if ("max-repetitions".equalsIgnoreCase(key)) {
                agentConfig.setMaxRepetitions(Integer.parseInt(value));
            } else if ("max-request-size".equalsIgnoreCase(key)) {
                agentConfig.setMaxRequestSize(Integer.parseInt(value));
            } else if ("version".equalsIgnoreCase(key)) {
                agentConfig.setVersionAsString(value);
            } else if ("security-level".equalsIgnoreCase(key)) {
                agentConfig.setSecurityLevel(Integer.parseInt(value));
            } else if ("security-name".equalsIgnoreCase(key)) {
                agentConfig.setSecurityName(value);
            } else if ("auth-passphrase".equalsIgnoreCase(key)) {
                agentConfig.setAuthPassPhrase(value);
            } else if ("auth-protocol".equalsIgnoreCase(key)) {
                agentConfig.setAuthProtocol(value);
            } else if ("priv-passphrase".equalsIgnoreCase(key)) {
                agentConfig.setPrivPassPhrase(value);
            } else if ("priv-protocol".equalsIgnoreCase(key)) {
                agentConfig.setPrivProtocol(value);
            } else if ("read-community".equalsIgnoreCase(key)) {
                agentConfig.setReadCommunity(value);
            } else if ("context-name".equalsIgnoreCase(key)) {
            	agentConfig.setContextName(value);
            } else if ("engine-id".equalsIgnoreCase(key)) {
            	agentConfig.setEngineId(value);
            } else if ("context-engine-id".equalsIgnoreCase(key)) {
            	agentConfig.setContextEngineId(value);
            } else if ("enterprise-id".equalsIgnoreCase(key)) {
            	agentConfig.setEnterpriseId(value);
            } else if ("write-community".equalsIgnoreCase(key)) {
            	agentConfig.setWriteCommunity(value);
            } else {
                LOG.warn("Unexpected attribute in protocol configuration string for SnmpAgentConfig: '{}'", attribute);
            }
        }
        return agentConfig;
    }

    public String toProtocolConfigString() {
        StringBuffer buff = new StringBuffer("snmp:");
        buff.append("address=").append((m_address == null? null : InetAddrUtils.str(m_address)));
        buff.append(",port=").append(getPort());
        buff.append(",timeout=").append(getTimeout());
        buff.append(",retries=").append(getRetries());
        buff.append(",max-vars-per-pdu=").append(getMaxVarsPerPdu());
        buff.append(",max-repetitions=").append(getMaxRepetitions());
        buff.append(",max-request-size=").append(getMaxRequestSize());
        buff.append(",version=").append(versionToString(getVersion()));
        if (isVersion3()) {
            buff.append(",security-level=").append(getSecurityLevel());
            buff.append(",security-name=").append(getSecurityName());
            buff.append(",auth-passphrase=").append(getAuthPassPhrase());
            buff.append(",auth-protocol=").append(getAuthProtocol());
            buff.append(",priv-passphrase=").append(getPrivPassPhrase());
            buff.append(",priv-protocol=").append(getPrivProtocol());
            if (getContextName() != null) buff.append(",context-name=").append(getContextName());
            if (getEngineId() != null) buff.append(",engine-id=").append(getEngineId());
            if (getContextEngineId() != null) buff.append(",context-engine-id=").append(getContextEngineId());
            if (getEnterpriseId() != null) buff.append(",enterprise-id=").append(getEnterpriseId());
        } else {
            buff.append(",read-community=").append(getReadCommunity());
            buff.append(",write-community=").append(getWriteCommunity());
        }
        return buff.toString();
    }

        @Override
    public String toString() {
        StringBuffer buff = new StringBuffer("SnmpAgentConfig[");
        buff.append("Address: ").append((m_address == null? null : InetAddrUtils.str(m_address)));
        buff.append(", ProxyForAddress: ").append((m_proxyFor == null? null : InetAddrUtils.str(m_proxyFor)));
        buff.append(", Port: ").append(getPort());
        buff.append(", Timeout: ").append(getTimeout());
        buff.append(", Retries: ").append(getRetries());
        buff.append(", MaxVarsPerPdu: ").append(getMaxVarsPerPdu());
        buff.append(", MaxRepetitions: ").append(getMaxRepetitions());
        buff.append(", MaxRequestSize: ").append(getMaxRequestSize());
        buff.append(", Version: ").append(versionToString(getVersion()));
        if (isVersion3()) {
            buff.append(", Security level: ").append(getSecurityLevel());
            buff.append(", Security name: ").append(getSecurityName());
            buff.append(", auth-passphrase: ").append(getAuthPassPhrase());
            buff.append(", auth-protocol: ").append(getAuthProtocol());
            buff.append(", priv-passphrase: ").append(getPrivPassPhrase());
            buff.append(", priv-protocol: ").append(getPrivProtocol());
            buff.append(", Context name: ").append(getContextName());
            buff.append(", Engine ID: ").append(getEngineId());
            buff.append(", Context Engine ID: ").append(getContextEngineId());
            buff.append(", Enterprise ID:").append(getEnterpriseId());
        } else {
        	buff.append(", ReadCommunity: ").append(getReadCommunity());
        	buff.append(", WriteCommunity: ").append(getWriteCommunity());
        }
        buff.append("]");
        return buff.toString();
    }


    @XmlJavaTypeAdapter(InetAddrXmlAdapter.class)
    public InetAddress getAddress() {
        return m_address;
    }

    public void setAddress(InetAddress address) {
        m_address = address;
    }

    @XmlJavaTypeAdapter(InetAddrXmlAdapter.class)
    public InetAddress getProxyFor() {
        return m_proxyFor;
    }

    public void setProxyFor(InetAddress address) {
        m_proxyFor = address;
    }

    @XmlTransient
    public InetAddress getEffectiveAddress() {
        if (m_proxyFor == null) return m_address;
        return m_proxyFor;
    }

}
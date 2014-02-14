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
package org.opennms.netmgt.rrd.model.v1;

/**
 * The Enumeration CFType (Consolidation Function Type).
 */
public enum CFType {

    /** RRA:AVERAGE:xff:steps:rows. */
    AVERAGE,

    /** RRA:MIN:xff:steps:rows. */
    MIN,

    /** RRA:MAX:xff:steps:rows. */
    MAX,

    /** RRA:LAST:xff:steps:rows. */
    LAST;

    /**
     * Gets the CF Value.
     *
     * @return the string representation of the consolidation function
     */
    public String value() {
        return name();
    }

    /**
     * From value.
     *
     * @param v the string name of the CF
     * @return the consolidation function type
     */
    public static CFType fromValue(String v) {
        return v == null ? null : valueOf(v.trim());
    }
}

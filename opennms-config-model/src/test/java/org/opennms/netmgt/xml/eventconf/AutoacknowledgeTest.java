/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2012 The OpenNMS Group, Inc.
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

package org.opennms.netmgt.xml.eventconf;

import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;

import org.junit.runners.Parameterized.Parameters;
import org.opennms.core.test.xml.XmlTestNoCastor;

public class AutoacknowledgeTest extends XmlTestNoCastor<Autoacknowledge> {

	public AutoacknowledgeTest(final Autoacknowledge sampleObject, final String sampleXml, final String schemaFile) {
		super(sampleObject, sampleXml, schemaFile);
	}

	@Parameters
	public static Collection<Object[]> data() throws ParseException {
		Autoacknowledge autoacknowledge0 = new Autoacknowledge();
		Autoacknowledge autoacknowledge1 = new Autoacknowledge();
		autoacknowledge1.setContent("These are important data");
		autoacknowledge1.setState("on");
		return Arrays.asList(new Object[][] {
				{autoacknowledge0,
				"<autoacknowledge/>",
				"target/classes/xsds/eventconf.xsd" },
				{autoacknowledge1,
				"<autoacknowledge state=\"on\">These are important data</autoacknowledge>",
				"target/classes/xsds/eventconf.xsd" } 
		});
	}

}

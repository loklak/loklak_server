/**
 *  SourceType
 *  Copyright 22.02.2015 by Michael Peter Christen, @0rb1t3r
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package org.loklak.objects;

/**
 * The SourceType objects answers on the question "what kind of data format". Do
 * not mix up this with the ProviderType, because there can be many providers
 * for the same SourceType.
 */
public class SourceType {

	public final static SourceType TWITTER = new SourceType("TWITTER"); // generated
																		// at
																		// twitter
																		// and
																		// scraped
																		// from
																		// there
	public final static SourceType FOSSASIA_API = new SourceType("FOSSASIA_API"); // imported
																					// from
																					// FOSSASIA
																					// API
																					// data,
	public final static SourceType OPENWIFIMAP = new SourceType("OPENWIFIMAP"); // imported
																				// from
																				// OpenWifiMap
																				// API
																				// data
	public final static SourceType NODELIST = new SourceType("NODELIST"); // imported
																			// from
																			// Freifunk
																			// Nodelist
	public final static SourceType NETMON = new SourceType("NETMON"); // imported
																		// from
																		// Freifunk
																		// Netmon
	public final static SourceType FREIFUNK_NODE = new SourceType("FREIFUNK_NODE"); // imported
																					// from
																					// Freifunk
																					// wifi
																					// router
																					// node
																					// (custom
																					// schema)
	public final static SourceType NINUX = new SourceType("NINUX"); // imported
																	// from
																	// Ninux
																	// http://map.ninux.org
	public final static SourceType GEOJSON = new SourceType("GEOJSON"); // geojson
																		// feature
																		// collection
																		// provided
																		// from
																		// remote
																		// peer

	final private String typeName;

	public SourceType(String typeName) throws RuntimeException {
		if (!isValid(typeName))
			throw new RuntimeException("type names must be in uppercase");
		this.typeName = typeName;
	}

	/**
	 * we want type names to be in uppercase. Persons who implement new types
	 * names must have a search-engine view of the meaning of types and we want
	 * that type names are considered as constant name for similar services. The
	 * number of type names should be small and equal to the number of services
	 * which loklak supports.
	 * 
	 * @param typeName
	 * @return true if the name is valid
	 */
	public static boolean isValid(String typeName) {
		return typeName != null && typeName.length() >= 3 && typeName.indexOf(' ') < 0
				&& typeName.equals(typeName.toUpperCase());
	}

	public boolean equals(Object o) {
		return o instanceof SourceType && ((SourceType) o).typeName.equals(this.typeName);
	}

	public String toString() {
		return this.typeName;
	}
}

/**
 * OpenWifiMapPushServlet
 * Copyright 16.07.2015 by Dang Hai An, @zyzo
 * <p/>
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * <p/>
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program in the file lgpl21.txt
 * If not, see <http://www.gnu.org/licenses/>.
 */

package org.loklak.api.iot;

import org.json.JSONArray;
import org.json.JSONObject;
import org.loklak.harvester.JsonFieldConverter;
import org.loklak.harvester.JsonValidator;
import org.loklak.objects.SourceType;

public class OpenWifiMapPushServlet extends AbstractPushServlet {

    private static final long serialVersionUID = -5983742254182146642L;

    @Override
    protected SourceType getSourceType() {
        return SourceType.OPENWIFIMAP;
    }

    @Override
    protected JsonValidator.JsonSchemaEnum getValidatorSchema() {
        return JsonValidator.JsonSchemaEnum.OPENWIFIMAP;
    }

    @Override
    protected JsonFieldConverter.JsonConversionSchemaEnum getConversionSchema() {
        return JsonFieldConverter.JsonConversionSchemaEnum.OPENWIFIMAP;
    }

    @Override
    protected JSONArray extractMessages(JSONObject data) {
        return data.getJSONArray("rows");
    }

    @Override
    protected void customProcessing(JSONObject message) {}
}

/**
 *  GenericScraper
 *  Copyright 16.06.2016 by Damini Satya, @daminisatya
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

package org.loklak.api.search;

import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONObject;
import org.loklak.http.RemoteAccess;
import org.loklak.server.Query;
import java.net.URL;
import java.net.MalformedURLException;

import de.l3s.boilerpipe.extractors.ExtractorBase;
import de.l3s.boilerpipe.extractors.CommonExtractors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class GenericScraper extends HttpServlet {

	private static final long serialVersionUID = 4653635987712691127L;

	/**
     * PrintJSON
     * @param response
     * @param JSONObject genericScraperData
     */
	public void printJSON(HttpServletResponse response, JSONObject genericScraperData) throws ServletException, IOException {
		response.setCharacterEncoding("UTF-8");
		PrintWriter sos = response.getWriter();
		sos.print(genericScraperData.toString(2));
		sos.println();
	}

	/**
     * Article API
     * @param URL
     * @param JSONObject genericScraperData
     * @return genericScraperData
     */
	public JSONObject bpipeExtract (String url, JSONObject genericScraperData, ExtractorBase extractor) throws MalformedURLException{
        URL qurl = new URL(url);
        String data = "";

        try {
            data = extractor.getText(qurl);
            genericScraperData.put("query", qurl);
            genericScraperData.put("data", data);
            genericScraperData.put("NLP", "true");
        }
        catch (Exception e) {
            if ("".equals(data)) {
                try {
                    Document htmlPage = Jsoup.connect(url).get();
                    data = htmlPage.text();
                    genericScraperData.put("query", qurl);
                    genericScraperData.put("data", data);
                    genericScraperData.put("NLP", "false");
                } catch (Exception ex) {}
            }
        }
        return genericScraperData;
    }

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		doGet(request, response);
	}

	@Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        Query post = RemoteAccess.evaluate(request);

        String url = post.get("url", "");
        String type = post.get("type", "default");

        URL qurl = new URL(url);

        // This can also be done in one line:
        JSONObject genericScraperData = new JSONObject(true);
        switch(type) {
            // Returns articles text in the webpage
            case "article":
                genericScraperData = bpipeExtract(url, genericScraperData, CommonExtractors.ARTICLE_EXTRACTOR);
                printJSON(response, genericScraperData);
                break;
            // Returns default main text in the webpage
            case "default":
                genericScraperData = bpipeExtract(url, genericScraperData, CommonExtractors.DEFAULT_EXTRACTOR);
                printJSON(response, genericScraperData);
                break;
            // Returns big articles text in the webpage
            case "main":
                genericScraperData = bpipeExtract(url, genericScraperData, CommonExtractors.LARGEST_CONTENT_EXTRACTOR);
                printJSON(response, genericScraperData);
                break;            
            // Returns all text in the webpage
            case "all":
                genericScraperData = bpipeExtract(url, genericScraperData, CommonExtractors.KEEP_EVERYTHING_EXTRACTOR);
                printJSON(response, genericScraperData);
                break;            
            // Returns text in the webpage, based on trained data(see boilerpipe docs)
            case "canola":
                genericScraperData = bpipeExtract(url, genericScraperData, CommonExtractors.CANOLA_EXTRACTOR);
                printJSON(response, genericScraperData);
                break;            
            // Returns error message for wrong type
            default:
                type = "error";
                genericScraperData.put("error", "Please mention type of scraper: <type> ('article', 'default', 'main', 'all', 'canola')");
            	printJSON(response, genericScraperData);
                break;
        }
        genericScraperData.put("type", type);
        
        // Also try other extractors!
    }
}

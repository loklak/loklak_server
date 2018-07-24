/**
 *  ClientHelper
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

package org.loklak.http;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.HttpClientConnectionManager;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.loklak.data.DAO;

/**
 * Helper class to provide BufferedReader Objects for get and post connections
 */
public class ClientConnection {

    public static String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_10_3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.89 Safari/537.36";

    public  static final String CHARSET = "UTF-8";
    private static final byte LF = 10;
    private static final byte CR = 13;
    public static final byte[] CRLF = {CR, LF};
    private static final boolean debugLog = DAO.getConfig("flag.debug.redirect_unshortener", "false").equals("true");

    private static RequestConfig defaultRequestConfig = RequestConfig.custom()
            .setSocketTimeout(60000)
            .setConnectTimeout(60000)
            .setConnectionRequestTimeout(60000)
            .setContentCompressionEnabled(true)
            .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
            .build();

    private final static CloseableHttpClient httpClient = getClosableHttpClient();

    private int status;
    private BufferedInputStream inputStream;
    private CloseableHttpResponse httpResponse;

    private static class TrustAllHostNameVerifier implements HostnameVerifier {
		public boolean verify(String hostname, SSLSession session) {
			return true;
		}
	}

	/**
     * GET request
     * @param urlstring
     * @param useAuthentication
     * @throws IOException
     */
    public ClientConnection(String urlstring) throws IOException {
        HttpRequestBase request = new HttpGet(urlstring);
        request.setHeader("User-Agent", USER_AGENT);
        this.executeRequest(request);
    }

    /**
     * POST request
     * @param urlstring
     * @param map
     * @param useAuthentication
     * @throws ClientProtocolException
     * @throws IOException
     */
    public ClientConnection(String urlstring, Map<String, byte[]> map) throws ClientProtocolException, IOException {
    	HttpRequestBase request = new HttpPost(urlstring);
        MultipartEntityBuilder entityBuilder = MultipartEntityBuilder.create();
        entityBuilder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
        for (Map.Entry<String, byte[]> entry: map.entrySet()) {
            entityBuilder.addBinaryBody(entry.getKey(), entry.getValue());
        }
        ((HttpPost) request).setEntity(entityBuilder.build());
        request.setHeader("User-Agent", USER_AGENT);
        this.executeRequest(request);
    }

    public final static CloseableHttpClient getClosableHttpClient() {
        boolean trustAllCerts = !"none".equals(DAO.getConfig("httpsclient.trustselfsignedcerts", "peers"))
                && ("all".equals(DAO.getConfig("httpsclient.trustselfsignedcerts", "peers")));
        System.setProperty("http.keepAlive", "true"); // ensure that the keep alive strategy is used when default is built
        System.setProperty("http.maxConnections", "200");
        System.setProperty("http.agent", USER_AGENT);
        return HttpClients.custom()
                .useSystemProperties() // makes it possible to inject properties
                .setConnectionManager(getConnctionManager(trustAllCerts))
                .setDefaultRequestConfig(defaultRequestConfig)
                .setMaxConnPerRoute(200)
                .setMaxConnTotal(500)
                .build();
    }

    public InputStream getInputStream() {
        return this.inputStream;
    }

    /**
     * get a connection manager
     * @param trustAllCerts allow opportunistic encryption if needed
     * @return
     */
    private static HttpClientConnectionManager getConnctionManager(boolean trustAllCerts) {

    	Registry<ConnectionSocketFactory> socketFactoryRegistry = null;
    	if(trustAllCerts){
	    	try {
	    		SSLConnectionSocketFactory trustSelfSignedSocketFactory = new SSLConnectionSocketFactory(
				    		new SSLContextBuilder().loadTrustMaterial(null, new TrustSelfSignedStrategy()).build(),
				            new TrustAllHostNameVerifier());
				socketFactoryRegistry = RegistryBuilder
		                .<ConnectionSocketFactory> create()
		                .register("http", new PlainConnectionSocketFactory())
		                .register("https", trustSelfSignedSocketFactory)
		                .build();
			} catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
				DAO.severe(e);
			}
    	}

    	PoolingHttpClientConnectionManager cm = (trustAllCerts && socketFactoryRegistry != null) ?
        		new PoolingHttpClientConnectionManager(socketFactoryRegistry):
        		new PoolingHttpClientConnectionManager();

        // twitter specific options
        cm.setMaxTotal(200);
        cm.setDefaultMaxPerRoute(20);
        cm.setMaxPerRoute(new HttpRoute(new HttpHost("twitter.com", 80)), 50);
        cm.setMaxPerRoute(new HttpRoute(new HttpHost("twitter.com", 443)), 50);

        return cm;
    }

    private void executeRequest(HttpRequestBase request) throws IOException {

        this.httpResponse = null;
        HttpContext context = HttpClientContext.create();
        try {
            this.httpResponse = httpClient.execute(request, context);
        } catch (UnknownHostException e) {
            request.reset();
            throw new IOException("client connection failed: unknown host " + request.getURI().getHost());
        } catch (SocketTimeoutException e){
        	request.reset();
        	throw new IOException("client connection timeout for request: " + request.getURI());
        } catch (SSLHandshakeException e){
        	request.reset();
        	throw new IOException("client connection handshake error for domain " + request.getURI().getHost() + ": " + e.getMessage());
        } catch (Throwable e) {
            request.reset();
            throw new IOException("server fail: " + e.getMessage());
        }
        HttpEntity httpEntity = this.httpResponse.getEntity();
        if (httpEntity != null) {
            if (this.httpResponse.getStatusLine().getStatusCode() == 200) {
                try {
                    this.inputStream = new BufferedInputStream(httpEntity.getContent());
                } catch (IOException e) {
                    request.reset();
                    throw e;
                }
                Map<String, List<String>> header = new HashMap<String, List<String>>();
                for (Header h: httpResponse.getAllHeaders()) {
                    List<String> vals = header.get(h.getName());
                    if (vals == null) { vals = new ArrayList<String>(); header.put(h.getName(), vals); }
                    vals.add(h.getValue());
                }
            } else {
                request.reset();
                throw new IOException("client connection to " + request.getURI() + " fail: " + status + ": " + httpResponse.getStatusLine().getReasonPhrase());
            }
        } else {
            request.reset();
            throw new IOException("client connection to " + request.getURI() + " fail: no connection");
        }
    }

    /**
     * Get body of HttpEntity as String
     * @param httpREntity
     * @return response body
     * @throws IOException when readLine fails
     */
    public static String getHTML(HttpEntity httpEntity) throws IOException {
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(httpEntity.getContent()));
        String content = null;
        while ((content = reader.readLine()) != null)
            sb.append(content);
        return sb.toString();
    }

    /**
     * Get redirect URL from Meta tag in response body
     * @param httpEntity
     * @return redirect URL if any else null
     * @throws IOException when getHTML() fails
     */
    public static String getMetaRedirectURL(HttpEntity httpEntity) throws IOException {
        String html = getHTML(httpEntity);
        html = html.replace("\n", "");  // May have a line break between tags
        if (html.length() == 0)
            return null;
        int indexHttpEquiv = html.toLowerCase().indexOf("http-equiv=\"refresh\"");  // Converting to lowercase because of case insensitivity
        if (indexHttpEquiv < 0) {
            return null;
        }
        html = html.substring(indexHttpEquiv);
        int indexContent = html.toLowerCase().indexOf("content=");
        if (indexContent < 0) {
            return null;
        }
        html = html.substring(indexContent);
        int indexURLStart = html.toLowerCase().indexOf(";url=");
        if (indexURLStart < 0) {
            return null;
        }
        html = html.substring(indexURLStart + 5);
        int indexURLEnd = html.toLowerCase().indexOf("\"");
        if (indexURLEnd < 0) {
            return null;
        }
        return html.substring(0, indexURLEnd);
    }

    /**
     * Find value of location header from the given HttpResponse
     * @param httpResponse
     * @return Value of location field if exists, null otherwise
     */
    public static String getLocationHeader(CloseableHttpResponse httpResponse) {
        for (Header header: httpResponse.getAllHeaders()) {
            if (header.getName().equalsIgnoreCase("location")) {
                return header.getValue();
            }
        }
        return null;
    }

    /**
     * get a redirect for an url: this method shall be called if it is expected that a url
     * is redirected to another url. This method then discovers the redirect.
     * @param urlstring
     * @param useAuthentication
     * @param isGet
     * @return the redirect url for the given urlstring
     * @throws IOException if there is some error with the link
     */
    private final static String getRedirect(String urlstring, boolean isGet) throws IOException {
        HttpRequestBase req;
        if (isGet)
            req = new HttpGet(urlstring);
        else
            req = new HttpPost(urlstring);
        req.setConfig(RequestConfig.custom().setRedirectsEnabled(false).build());
        req.setHeader("User-Agent", USER_AGENT);
        CloseableHttpResponse httpResponse = httpClient.execute(req);
        HttpEntity httpEntity = httpResponse.getEntity();
        if (debugLog) {
            DAO.log("[" + (isGet ? "GET": "POST") + "] Status for " + urlstring + ": " + httpResponse.getStatusLine().toString());
        }
        if (httpEntity != null) {
            int httpStatusCode = httpResponse.getStatusLine().getStatusCode();
            if (300 <= httpStatusCode && httpStatusCode <= 308) {
                String location = getLocationHeader(httpResponse);
                EntityUtils.consumeQuietly(httpEntity);
                if (location != null) {  // A redirect was found
                    return location;
                }
                throw new IOException("redirect for  " + urlstring+ ": no location attribute found");
            } else if (isGet && httpStatusCode != 200) {
                /*
                    In most of the cases when requesting to http://fb.me/*, a GET request results in 400 Bad Request
                    But making a POST request does the job.
                */
                if (debugLog) {
                    DAO.log("GET method failed for " + urlstring + " trying POST");
                }
                return getRedirect(urlstring, false);
            } else {
                if (debugLog)
                    DAO.log("Trying to fetch Meta redirect URL for " + urlstring);
                String metaURL = getMetaRedirectURL(httpEntity);
                EntityUtils.consumeQuietly(httpEntity);
                if (metaURL != null) {  // A URL was found in meta tag
                    if (debugLog) {
                        DAO.log("Fetched Meta redirect URL for " + urlstring + " : " + metaURL);
                    }
                    if (!metaURL.startsWith("http")) {
                        URL u = new URL(new URL(urlstring), metaURL);
                        return u.toString();
                    }
                    return metaURL;
                }
                return urlstring;
            }
        } else {
            throw new IOException("client connection to " + urlstring + " fail: no connection");
        }
    }

    /**
     * get a redirect for an url: this method shall be called if it is expected that a url
     * is redirected to another url. This method then discovers the redirect.
     * @param urlstring
     * @return
     * @throws IOException
     */
    public static String getRedirect(String urlstring) throws IOException {
        return getRedirect(urlstring, true);
    }

    public void close() {
        if (this.httpResponse != null) {
            HttpEntity httpEntity = this.httpResponse.getEntity();
            if (httpEntity != null) EntityUtils.consumeQuietly(httpEntity);
            this.httpResponse = null;
        }
        if (this.inputStream != null) {
            try {this.inputStream.close();} catch (IOException e) {}
            this.inputStream = null;
        }
    }

    public void finalize() {
        this.close();
    }

    public static void download(String source_url, File target_file) {
        try {
            ClientConnection connection = new ClientConnection(source_url);
            try {
                OutputStream os = new BufferedOutputStream(new FileOutputStream(target_file));
                int count;
                byte[] buffer = new byte[2048];
                try {
                    while ((count = connection.inputStream.read(buffer)) > 0) os.write(buffer, 0, count);
                } catch (IOException e) {
                	DAO.severe(e);
                } finally {
                    os.close();
                }
            } catch (IOException e) {
            	DAO.severe(e);
            } finally {
                connection.close();
            }
        } catch (IOException e) {
        	DAO.severe(e);
        }
    }

    public static byte[] download(String source_url) throws IOException {
        try {
            ClientConnection connection = new ClientConnection(source_url);
            if (connection.inputStream == null) return null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int count;
            byte[] buffer = new byte[4096];
            try {
                while ((count = connection.inputStream.read(buffer)) > 0) baos.write(buffer, 0, count);
            } catch (IOException e) {
            	DAO.severe(e);
            } finally {
                connection.close();
            }
            return baos.toByteArray();
        } catch (IOException e) {
        	DAO.severe(e);
            return null;
        }
    }

    public int getStatusCode() {
        return this.httpResponse.getStatusLine().getStatusCode();
    }

    public static void main(String[] args) {
        // lets make a read-world test to find out which closing-methods cause less traffic
        String url[] = new String[]{
                "http://yacy.net/index.html",
                "http://yacy.net/de/index.html",
                "http://yacy.net/de/Bildschirmfotos.html",
                "http://yacy.net/de/Lehrfilme.html",
                "http://yacy.net/de/Philosophie.html",
                "http://yacy.net/de/Suchportal.html",
                "http://yacy.net/de/Anwendungen.html",
                "http://loklak.org/index.html",
                "http://yacy.net/de/API.html",
                "http://yacy.net/de/Technik.html",
                "http://yacy.net/de/Mitmachen.html",
                "http://yacy.net/en/index.html",
                "http://yacy.net/en/Screenshots.html",
                "http://yacy.net/en/Tutorials.html",
                "http://yacy.net/en/Philosophy.html",
                "http://yacy.net/en/Searchportal.html",
                "http://yacy.net/en/Applications.html",
                "http://yacy.net/en/API.html",
                "http://yacy.net/en/Technology.html",
                "http://yacy.net/en/Join.html"
        };
        long s = System.currentTimeMillis();
        for (int i = 0; i< url.length; i++) {
            try {
                long start = System.currentTimeMillis();
                byte[] b = download(url[i]);
                long stop = System.currentTimeMillis();
                long t = stop - start;
                System.out.println(b.length + " bytes in " + t + " milliseconds, " + (b.length * 1000 / (t + 1)) + " bytes/s");
                //if (i == 7) try {Thread.sleep(30000);} catch (InterruptedException e) {}
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("total time: " + (System.currentTimeMillis() - s) + " milliseconds");
    }
}

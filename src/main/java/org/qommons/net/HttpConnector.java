/*
 * HttpConnector.java Created Sep 20, 2011 by Andrew Butler, PSL
 */
package org.qommons.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;

import org.apache.log4j.Logger;
import org.qommons.LoggingWriter;
import org.qommons.QommonsUtils;

/** Facilitates easier HTTP connections. */
public class HttpConnector
{
	private static final Logger log = Logger.getLogger(HttpConnector.class);

	/** The name of the system property to set the SSL handler package in */
	public static final String SSL_HANDLER_PROP = "java.protocol.handler.pkgs";

	/** The name of the system property to set the trust store location in */
	public static final String TRUST_STORE_PROP = "javax.net.ssl.trustStore";

	/** The name of the system property to set the trust store password in */
	public static final String TRUST_STORE_PWD_PROP = "javax.net.ssl.trustStorePassword";

	/** Thrown when the HTTP connection gives an error */
	public static class HttpResponseException extends IOException
	{
		private int theResponseCode;

		private String theResponseMessage;

		/**
		 * Creates an HTTP response exception
		 *
		 * @param s The message for the exception
		 * @param responseCode The HTTP response code that caused this exception
		 * @param responseMessage The HTTP response message corresponding to the response code
		 */
		public HttpResponseException(String s, int responseCode, String responseMessage)
		{
			super(s);
			theResponseCode = responseCode;
		}

		/** @return The HTTP response code that caused this exception (e.g. 404) */
		public int getResponseCode()
		{
			return theResponseCode;
		}

		/** @return The HTTP response message corresponding to the response code (e.g. "Not Found") */
		public String getResponseMessage()
		{
			return theResponseMessage;
		}
	}

	private static class SecurityRetriever implements javax.net.ssl.X509TrustManager
	{
		private java.security.cert.X509Certificate[] theCerts;

		SecurityRetriever()
		{
		}

		@Override
		public java.security.cert.X509Certificate[] getAcceptedIssuers()
		{
			return new java.security.cert.X509Certificate [0];
		}

		@Override
		public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType)
		{
		}

		@Override
		public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType)
		{
			theCerts = certs;
		}

		java.security.cert.X509Certificate[] getCerts()
		{
			return theCerts;
		}
	}

	private final String theURL;

	private final HostnameVerifier theHostnameVerifier;

	private final KeyManager [] theKeyManagers;
	private final SSLSocketFactory theSocketFactory;

	private final Map<String, String> theCookies;

	private	final Boolean isFollowingRedirects;

	private final int theConnectTimeout;

	private final int theReadTimeout;

	private HttpConnector(String url, HostnameVerifier hostnameVerifier, KeyManager[] keyManagers, SSLSocketFactory socketFactory,
		Map<String, String> cookies, Boolean followRedirects, int connectTimeout, int readTimeout) {
		theURL = url;
		theHostnameVerifier=hostnameVerifier;
		theKeyManagers=keyManagers;
		theSocketFactory=socketFactory;
		theCookies = cookies;
		isFollowingRedirects=followRedirects;
		theConnectTimeout = -1;
		theReadTimeout = -1;
	}

	/** @return The URL that this connector connects to */
	public String getUrl()
	{
		return theURL;
	}

	/** @return Whether this connection keeps track of and sends cookies */
	public boolean usesCookies()
	{
		return theCookies != null;
	}

	/** Clears cookies set for this connection */
	public void clearCookies()	{
		if(theCookies != null)
			theCookies.clear();
	}

	/** @return The set of cookies set for this connection */
	public Map<String, String> getCookies()	{
		return Collections.unmodifiableMap(theCookies);
	}

	/**
	 * @return Whether connections made by this connector automatically follow redirect codes sent
	 *         from the server. Null means the property has not been set and the default value will
	 *         be used.
	 * @see HttpURLConnection#setFollowRedirects(boolean)
	 */
	public Boolean isFollowingRedirects()
	{
		return isFollowingRedirects;
	}

	/**
	 * @return The timeout value, in milliseconds that connections made by this connector will wait
	 *         for the connection to be established. A negative value indicates that this parameter
	 *         is not set and the default will be used.
	 * @see java.net.URLConnection#setConnectTimeout(int)
	 */
	public int getConnectTimeout()
	{
		return theConnectTimeout;
	}

	/**
	 * @return The timeout value, in milliseconds that connections made by this connector will wait
	 *         for data to be available to read. A negative value indicates that this parameter is
	 *         not set and the default will be used.
	 * @see java.net.URLConnection#setReadTimeout(int)
	 */
	public int getReadTimeout()
	{
		return theReadTimeout;
	}

	/**
	 * Sets the security parameters for a HTTPS connections in general
	 *
	 * @param handlerPkg The handler package for the HTTPS protocol
	 * @param provider The HTTPS security provider
	 */
	public static void setGlobalSecurityInfo(String handlerPkg, java.security.Provider provider)
	{
		if(handlerPkg != null)
		{
			String handlers = System.getProperty(SSL_HANDLER_PROP);
			if(handlers == null || !handlers.contains(handlerPkg))
				System.setProperty(SSL_HANDLER_PROP, handlers + "|" + handlerPkg);
		}
		if(provider != null)
			java.security.Security.addProvider(provider);
	}

	String BOUNDARY = "----------------" + QommonsUtils.getRandomString(16);

	/**
	 * Contacts the server and retrieves the security certificate information provided by the SSL
	 * server.
	 *
	 * @return The SSL certificates provided by the server, or null if the connection is not over
	 *         HTTPS
	 * @throws IOException If the connection cannot be made
	 */
	public java.security.cert.X509Certificate[] getServerCerts() throws IOException
	{
		String callURL = theURL;
		callURL += "?method=test";
		HttpURLConnection conn;
		java.net.URL url = new java.net.URL(callURL);
		conn = (HttpURLConnection) url.openConnection();
		if(isFollowingRedirects != null)
			conn.setInstanceFollowRedirects(isFollowingRedirects.booleanValue());
		if(theConnectTimeout >= 0)
			conn.setConnectTimeout(theConnectTimeout);
		if(theReadTimeout >= 0)
			conn.setReadTimeout(theReadTimeout);
		if(conn instanceof javax.net.ssl.HttpsURLConnection)
		{
			SecurityRetriever retriever = new SecurityRetriever();
			javax.net.ssl.SSLContext sc;
			try
			{
				sc = javax.net.ssl.SSLContext.getInstance("SSL");
				sc.init(theKeyManagers, new TrustManager [] {retriever},
					new java.security.SecureRandom());
			} catch(java.security.GeneralSecurityException e)
			{
				log.error("Could not initialize SSL context", e);
				IOException toThrow = new IOException("Could not initialize SSL context: "
					+ e.getMessage());
				toThrow.setStackTrace(e.getStackTrace());
				throw toThrow;
			}
			javax.net.ssl.HttpsURLConnection sConn = (javax.net.ssl.HttpsURLConnection) conn;
			sConn.setSSLSocketFactory(sc.getSocketFactory());
			sConn.setHostnameVerifier(new HostnameVerifier()
			{
				@Override
				public boolean verify(String hostname, javax.net.ssl.SSLSession session)
				{
					return true;
				}
			});
			try
			{
				conn.connect();
			} catch(IOException e)
			{
				if(retriever.getCerts() == null)
					throw e;
			}
			return retriever.getCerts();
		}
		else
			return null;
	}

	/**
	 * Encodes a string in URL format
	 *
	 * @param toEncode The string to format
	 * @return The URL-formatted string
	 * @throws IOException If an error occurs formatting the string
	 */
	public static String encode(String toEncode) throws IOException
	{
		try
		{
			toEncode = QommonsUtils.encodeUnicode(toEncode);
			return java.net.URLEncoder.encode(toEncode, "UTF-8");
		} catch(java.io.UnsupportedEncodingException e)
		{
			IOException toThrow = new IOException(e.getMessage());
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		}
	}

	/** Builds a connector */
	public static class Builder {
		private final String theURL;

		private List<KeyManager> theKeyManagers;
		private List<TrustManager> theTrustManagers;
		private HostnameVerifier theHostnameVerifier;

		private Boolean isFollowingRedirects;
		private int theConnectTimeout;
		private int theReadTimeout;

		private boolean withCookies;
		private final ParamsBuilder<Builder> theCookies;

		private Builder(String url) {
			theURL=url;

			theKeyManagers=new ArrayList<>();
			theTrustManagers=new ArrayList<>();
			withCookies=true;
			theCookies = new ParamsBuilder<>(this);

			theConnectTimeout=-1;
			theReadTimeout=-1;
		}

		/** @return A builder for the cookies to supply with the connector */
		public ParamsBuilder<Builder> withCookies() {
			withCookies=true;
			return theCookies;
		}

		/**
		 * Specifies that the connector should not supply any cookies
		 * 
		 * @return This builder
		 */
		public Builder noCookies(){
			withCookies=false;
			return this;
		}

		/**
		 * Adds a key manager that this connector will use to provide client certificates to HTTPS connections when requested.
		 *
		 * @param mgr The key manager to validate HTTPS connections. Typically a single instance of {@link javax.net.ssl.X509KeyManager}
		 *            will be used.
		 * @return This builder
		 */
		public Builder withKeyManager(KeyManager mgr){
			if(!theKeyManagers.contains(mgr))
				theKeyManagers.add(mgr);
			return this;
		}

		/**
		 * Adds a trust manager that this connector will use to validate server certificates provided from HTTPS connections
		 *
		 * @param mgr The trust manager to validate HTTPS connections. Typically a single instance of {@link javax.net.ssl.X509TrustManager}
		 *            will be used.
		 * @return This builder
		 */
		public Builder withTrustManager(TrustManager mgr){
			if(!theTrustManagers.contains(mgr))
				theTrustManagers.add(mgr);
			return this;
		}

		/**
		 * @param verifier The hostname verifier that this connector will use HTTPS connections
		 * @return This builder
		 */
		public Builder withHostnameVerifier(HostnameVerifier verifier){
			if(theHostnameVerifier!=null)
				System.err.println("WARNING: Replacing hostname verifier");
			theHostnameVerifier=verifier;
			return this;
		}

		/**
		 * @param follow Whether connections made by this connector should automatically follow redirect codes sent from the server. Null
		 *        means this property will be unset and the default value should be used.
		 * @return This builder
		 * @see HttpURLConnection#setFollowRedirects(boolean)
		 */
		public Builder followRedirects(boolean follow){
			isFollowingRedirects=follow;
			return this;
		}

		/**
		 * @param connectTimeout The timeout value, in milliseconds that connections made by this connector should wait for the connection
		 *        to be established. A negative value indicates that this parameter should not be set and the default should be used.
		 * @return This builder
		 * @see java.net.URLConnection#setConnectTimeout(int)
		 */
		public Builder withConnectTimeout(int connectTimeout){
			theConnectTimeout=connectTimeout;
			return this;
		}

		/**
		 * @param readTimeout The timeout value, in milliseconds that connections made by this connector should wait for data to be
		 *        available to read. A negative value indicates that this parameter should not be set and the default should be used.
		 * @return This builder
		 * @see java.net.URLConnection#setReadTimeout(int)
		 */
		public Builder withReadTimeout(int readTimeout){
			theReadTimeout=readTimeout;
			return this;
		}

		/**
		 * @return The built connector
		 * @throws NoSuchAlgorithmException If the "SSL" algorithm cannot be found in the environment
		 * @throws KeyManagementException If the SSL context cannot be initialized with the given {@link #withKeyManager(KeyManager) key
		 *         managers} and {@link #withTrustManager(TrustManager) trust managers}, if any are set
		 */
		public HttpConnector build() throws NoSuchAlgorithmException, KeyManagementException{
			KeyManager[] keyManagers = theKeyManagers.toArray(new KeyManager[theKeyManagers.size()]);
			TrustManager[] trustManagers = theTrustManagers.toArray(new TrustManager[theTrustManagers.size()]);
			SSLSocketFactory socketFactory;
			if (!theKeyManagers.isEmpty() || !theTrustManagers.isEmpty()) {
				javax.net.ssl.SSLContext sc;
				sc = javax.net.ssl.SSLContext.getInstance("SSL");
				sc.init(keyManagers, trustManagers, new java.security.SecureRandom());
				socketFactory = sc.getSocketFactory();
			} else
				socketFactory = null;
			Map<String, String> cookies;
			if (withCookies) {
				cookies = new LinkedHashMap<>();
				for (Map.Entry<String, Object> cookie : theCookies.theParams.entrySet())
					cookies.put(cookie.getKey(), (String) cookie.getValue());
			} else
				cookies = Collections.emptyMap();
			return new HttpConnector(theURL, theHostnameVerifier, keyManagers, socketFactory, cookies, isFollowingRedirects,
				theConnectTimeout, theReadTimeout);
		}
	}

	/**
	 * @param url The URL to connect to
	 * @return A builder for a {@link HttpConnector} to the given URL
	 */
	public static Builder build(String url) {
		return new Builder(url);
	}

	/**
	 * A builder for a parameter set
	 * 
	 * @param <T> The type of the parameter set's owner
	 */
	public static class ParamsBuilder<T>{
		private final T theBuilder;
		/** The parameters */
		protected final Map<String, Object> theParams;

		ParamsBuilder(T builder) {
			theBuilder=builder;
			theParams = new LinkedHashMap<>();
		}

		/** @return The builder that owns this parameter set */
		public T out(){
			return theBuilder;
		}

		/**
		 * @param param The parameter name
		 * @param value The parameter value
		 * @return This parameter builder
		 */
		protected ParamsBuilder<T> withObj(String param, Object value){
			Object old=theParams.put(param, value);
			if(old!=null)
				System.err.println("WARNING: Parameter "+param+" value was not empty: "+old);
			return this;
		}

		/**
		 * @param param The parameter name
		 * @param value The parameter value
		 * @return This parameter builder
		 */
		public ParamsBuilder<T> with(String param, String value){
			return withObj(param, value);
		}
	}

	/**
	 * A builder for a parameter set that can supply long values
	 * 
	 * @param <T> The type of the parameter set's owner
	 */
	public static class StreamParamsBuilder<T> extends ParamsBuilder<T>{
		StreamParamsBuilder(T builder){
			super(builder);
		}

		/**
		 * @param param The parameter name
		 * @param value The binary value stream
		 * @return This parameter builder
		 */
		public ParamsBuilder<T> with(String param, InputStream value){
			return withObj(param, value);
		}

		/**
		 * @param param The parameter name
		 * @param value The text value stream
		 * @return This parameter builder
		 */
		public ParamsBuilder<T> with(String param, Reader value){
			return withObj(param, value);
		}
	}

	/** A request to a URL */
	public class Request {
		private final ParamsBuilder<Request> theRequestProperties;
		private final ParamsBuilder<Request> theGetParams;
		private final StreamParamsBuilder<Request> thePostParams;

		private Object theContent;

		Request(){
			theRequestProperties=new ParamsBuilder<>(this);
			theGetParams=new ParamsBuilder<>(this);
			thePostParams=new StreamParamsBuilder<>(this);

			theRequestProperties.theParams.put("Accept-Encoding", "gzip");
			theRequestProperties.theParams.put("Accept-Charset", "UTF-8");
		}

		/** @return A parameter set to use to configure the request properties */
		public ParamsBuilder<Request> withRequestProperties(){
			return theRequestProperties;
		}

		/** @return A parameter set to use to configure the GET parameters */
		public ParamsBuilder<Request> withGet(){
			return theGetParams;
		}

		/** @return A parameter set to use to configure the POST parameters */
		public StreamParamsBuilder<Request> withPost(){
			return thePostParams;
		}

		/**
		 * @param content The content to supply
		 * @return This request
		 */
		public Request withContent(String content){
			if(theContent!=null)
				System.err.println("WARNING: Replacing content");
			theContent=content;
			return this;
		}

		/**
		 * @param content The text content to supply
		 * @return This request
		 */
		public Request withContent(Reader content){
			if(theContent!=null)
				System.err.println("WARNING: Replacing content");
			theContent=content;
			return this;
		}

		/**
		 * @param content The binary content to supply
		 * @return This request
		 */
		public Request withContent(InputStream content){
			if(theContent!=null)
				System.err.println("WARNING: Replacing content");
			theContent=content;
			return this;
		}

		/**
		 * Makes the connection
		 * 
		 * @return The connection
		 * @throws IOException If a connection error occurs
		 */
		public HttpURLConnection connect() throws IOException{
			HttpURLConnection conn = null;
			try
			{
				conn = getConnection();
				for (Map.Entry<String, Object> p : theRequestProperties.theParams.entrySet())
					conn.setRequestProperty(p.getKey(), (String) p.getValue());
				OutputStream outStream = null;
				if (!thePostParams.theParams.isEmpty()) {
					conn.setRequestMethod("POST");
					conn.setDoOutput(true);
					conn.connect();
					outStream = conn.getOutputStream();
					setPostParams(outStream);
				} else {
					conn.setRequestMethod("GET");
					conn.connect();
				}
				if (theContent != null) {
					if (outStream == null)
						outStream = conn.getOutputStream();
					writeContent(theContent, outStream);
				}
				if (outStream != null)
					outStream.close();
				readCookies(conn);
				return conn;
			} catch (IOException e) {
				if (conn == null || conn.getResponseCode() == 200)
					throw e;
				HttpResponseException toThrow = new HttpResponseException(e.getMessage(), conn.getResponseCode(),
					conn.getResponseMessage());
				toThrow.setStackTrace(e.getStackTrace());
				throw toThrow;
			} catch (Throwable e) {
				IOException toThrow = new IOException("Call to " + theURL + " failed: " + e);
				toThrow.setStackTrace(e.getStackTrace());
				throw toThrow;
			}
		}

		/**
		 * Uploads data to a server
		 *
		 * @param fileName The file name to label the data with
		 * @param mimeType The type of the data to send
		 * @param receiver The stream in which the server's return data will be placed. May be null if the return data not needed or expected.
		 *            This stream will be used when the {@link OutputStream#close() close()} method is called on the return value of
		 *            this method. This method will not call close on the receiver.
		 * @return An output stream that the caller may use to write the file data to. The {@link OutputStream#close() close()} method
		 *         must be called when data writing is finished.
		 * @throws IOException If the data cannot be uploaded
		 */
		public OutputStream uploadData(String fileName, String mimeType, final OutputStream receiver)
			throws IOException {
			final HttpURLConnection conn = getConnection();

			conn.setDoOutput(true);
			conn.setDoInput(true);
			conn.setUseCaches(false);
			conn.setDefaultUseCaches(false);
			for (Map.Entry<String, Object> p : theRequestProperties.theParams.entrySet())
				conn.setRequestProperty(p.getKey(), (String) p.getValue());
			conn.setRequestProperty("Connection", "Keep-Alive");
			// c.setRequestProperty("HTTP_REFERER", codebase);
			conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
			final OutputStream os;
			final java.io.Writer out;
			if (!thePostParams.theParams.isEmpty()) {
				conn.setRequestMethod("POST");
				conn.connect();
				os = conn.getOutputStream();
				@SuppressWarnings("resource")
				LoggingWriter logging = new LoggingWriter(new OutputStreamWriter(os, Charset.forName("UTF-8")), null);
				out = logging;
				out.write("--");
				out.write(BOUNDARY);
				out.write("\r\n");

				for (Map.Entry<String, Object> p : thePostParams.theParams.entrySet()) {
					// write content header
					out.write("Content-Disposition: form-data; name=\"" + p.getKey() + "\"\r\n\r\n");
					if (p.getValue() instanceof String)
						out.write((String) p.getValue());
					else if (p.getValue() instanceof Reader) {
						Reader reader = (Reader) p.getValue();
						int read = reader.read();
						while (read >= 0) {
							out.write(read);
							read = reader.read();
						}
						reader.close();
					} else if (p.getValue() instanceof InputStream)
						throw new IllegalArgumentException("InputStreams may not be post parameters for uploading data");
					else
						throw new IllegalArgumentException("Unrecognized post parameter value type: " + (p.getValue() == null ? "null" :
							p.getValue().getClass().getName()));
					out.write("\r\n--");
					out.write(BOUNDARY);
					out.write("\r\n");
				}
			} else {
				conn.setRequestMethod("GET");
				conn.connect();
				os = conn.getOutputStream();
				out = new LoggingWriter(new OutputStreamWriter(os, Charset.forName("UTF-8")), null);
				out.write("--");
				out.write(BOUNDARY);
				out.write("\r\n");
			}

			// write content header
			out.write("Content-Disposition: form-data; name=\"Upload Data\"; filename=\"" + fileName + "\"");
			out.write("\r\n");
			out.write("Content-Type: ");
			if (mimeType != null)
				out.write(mimeType);
			else
				out.write("application/octet-stream");
			out.write("\r\n");
			out.write("\r\n");
			out.flush();
			return new OutputStream() {
				private boolean isClosed;

				@Override public void write(int b) throws IOException {
					os.write(b);
				}

				@Override public void write(byte[] b) throws IOException {
					os.write(b);
				}

				@Override public void write(byte[] b, int off, int len) throws IOException {
					os.write(b, off, len);
				}

				@Override public void flush() throws IOException {
					os.flush();
				}

				@Override public void close() throws IOException {
					if (isClosed)
						return;
					isClosed = true;
					os.flush();
					out.write("\r\n");
					out.write("--");
					out.write(BOUNDARY);
					out.write("--");
					out.write("\r\n");
					out.flush();
					out.close();
					InputStream in = conn.getInputStream();
					int read = in.read();
					while (read >= 0) {
						if (receiver != null)
							receiver.write(read);
						read = in.read();
					}
					in.close();
				}
			};
		}

		private HttpURLConnection getConnection()
			throws IOException
		{
			String callURL = theURL;
			if(theGetParams.theParams.size() > 0)
			{
				StringBuilder args = new StringBuilder();
				boolean first = true;
				for(Map.Entry<String, Object> p : theGetParams.theParams.entrySet())
				{
					args.append(first ? '?' : '&');
					first = false;
					args.append(encode(p.getKey())).append('=').append(encode((String) p.getValue()));
				}
				callURL += args.toString();
			}
			HttpURLConnection conn;
			java.net.URL url = new java.net.URL(callURL);
			conn = (HttpURLConnection) url.openConnection();
			if(isFollowingRedirects != null)
				conn.setInstanceFollowRedirects(isFollowingRedirects.booleanValue());
			if(theConnectTimeout >= 0)
				conn.setConnectTimeout(theConnectTimeout);
			if(theReadTimeout >= 0)
				conn.setReadTimeout(theReadTimeout);
			if(conn instanceof javax.net.ssl.HttpsURLConnection)
			{
				javax.net.ssl.HttpsURLConnection sConn = (javax.net.ssl.HttpsURLConnection) conn;
				if(theSocketFactory != null)
					sConn.setSSLSocketFactory(theSocketFactory);
				if(theHostnameVerifier != null)
					sConn.setHostnameVerifier(theHostnameVerifier);
			}
			// Send client cookies
			Map<String, String> cookies = theCookies;
			if(cookies != null && !cookies.isEmpty())
			{
				StringBuilder cookie = new StringBuilder();
				boolean first = true;
				for(Map.Entry<String, String> c : cookies.entrySet())
				{
					if(!first)
						cookie.append(", ");
					first = false;
					cookie.append(c.getKey()).append('=').append(c.getValue());
				}
				conn.setRequestProperty("Cookie", cookie.toString());
			}
			return conn;
		}

		void setPostParams(OutputStream outStream) throws IOException{
			OutputStreamWriter wr = new OutputStreamWriter(outStream, Charset.forName("UTF-8"));
			boolean first = true;
			for (Map.Entry<String, Object> p : thePostParams.theParams.entrySet()) {
				if (!first)
					wr.write('&');
				first = false;
				wr.write(p.getKey());
				wr.write('=');
				wr.flush();
				writeContent(p.getValue(), outStream);
			}
		}

		void writeContent(Object content, OutputStream outStream) throws IOException {
			OutputStreamWriter wr = new OutputStreamWriter(outStream, Charset.forName("UTF-8"));
			if (content != null) {
				if (content instanceof String)
					wr.write((String) content);
				else if (content instanceof Reader) {
					Reader reader = (Reader) content;
					int read = reader.read();
					while (read >= 0) {
						wr.write(read);
						read = reader.read();
					}
					reader.close();
				} else {
					wr.flush();
					InputStream input = (InputStream) content;
					int read = input.read();
					while (read >= 0) {
						outStream.write(read);
						read = input.read();
					}
					input.close();
					outStream.flush();
				}
			}
			wr.flush();
		}

		void readCookies(HttpURLConnection conn){
			// Read cookies sent by the server
			Map<String, String> cookies = theCookies;
			if (cookies != null) {
				List<String> reqCookies = conn.getHeaderFields().get("Set-Cookie");
				if (reqCookies != null)
					for (String c : reqCookies) {
						String[] cSplit = c.split(";");
						for (String cs : cSplit) {
							cs = cs.trim();
							int eqIdx = cs.indexOf('=');
							if (eqIdx >= 0)
								cookies.put(cs.substring(0, eqIdx), cs.substring(eqIdx + 1));
							else
								cookies.put(cs, "true");
						}
					}
			}
		}
	}

	/** @return A request object to configure and make a request to this connector's URL */
	public Request createRequest(){
		return new Request();
	}
}

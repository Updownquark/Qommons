/*
 * HttpConnector.java Created Sep 20, 2011 by Andrew Butler, PSL
 */
package org.qommons.net;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import org.apache.log4j.Logger;
import org.qommons.ArrayUtils;
import org.qommons.LoggingWriter;
import org.qommons.QommonsUtils;

/** Facilitates easier HTTP connections. */
public class HttpConnector
{
	private static final Logger log = Logger.getLogger(HttpConnector.class);

	/**
	 * The delimiter that signfies following parameters as GET parameters in the connect and read
	 * methods
	 */
	public static String GET = new String("GET");

	/**
	 * The delimiter that signfies following parameters as POST parameters in the connect and read
	 * methods
	 */
	public static String POST = new String("POST");

	/**
	 * The delimiter that signfies following parameters as request properties in the connect and
	 * read methods
	 */
	public static String REQUEST_PROP = new String("REQUEST");

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

	private String theURL;

	private javax.net.ssl.KeyManager[] theKeyManagers;

	private javax.net.ssl.TrustManager[] theTrustManagers;

	private javax.net.ssl.HostnameVerifier theHostnameVerifier;

	private javax.net.ssl.SSLSocketFactory theSocketFactory;

	private java.util.LinkedHashMap<String, String> theCookies;

	private Boolean isFollowingRedirects;

	private int theConnectTimeout;

	private int theReadTimeout;

	/**
	 * Creates an HTTP connector
	 *
	 * @param url The URL to connect to
	 */
	public HttpConnector(String url)
	{
		theURL = url;
		theCookies = new java.util.LinkedHashMap<>();
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

	/** @param use Whether this connection should keep track of and send cookies */
	public void setUsesCookies(boolean use)
	{
		if(use == (theCookies != null))
			return;
		if(use)
			theCookies = new java.util.LinkedHashMap<>();
		else
			theCookies = null;
	}

	/** Clears cookies set for this connection */
	public void clearCookies()
	{
		if(theCookies != null)
			theCookies.clear();
	}

	/** @return The set of cookies set for this connection */
	public java.util.Map<String, String> getCookies()
	{
		return theCookies;
	}

	/**
	 * @return Whether connections made by this connector automatically follow redirect codes sent
	 *         from the server. Null means the property has not been set and the default value will
	 *         be used.
	 * @see java.net.HttpURLConnection#setFollowRedirects(boolean)
	 */
	public Boolean isFollowingRedirects()
	{
		return isFollowingRedirects;
	}

	/**
	 * @param follow Whether connections made by this connector should automatically follow redirect
	 *        codes sent from the server. Null means this property will be unset and the default
	 *        value should be used.
	 * @see java.net.HttpURLConnection#setFollowRedirects(boolean)
	 */
	public void setFollowRedirects(Boolean follow)
	{
		isFollowingRedirects = follow;
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
	 * @param timeout The timeout value, in milliseconds that connections made by this connector
	 *        should wait for the connection to be established. A negative value indicates that this
	 *        parameter should not be set and the default should be used.
	 * @see java.net.URLConnection#setConnectTimeout(int)
	 */
	public void setConnectTimeout(int timeout)
	{
		theConnectTimeout = timeout;
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
	 * @param timeout The timeout value, in milliseconds that connections made by this connector
	 *        should wait for data to be available to read. A negative value indicates that this
	 *        parameter should not be set and the default should be used.
	 * @see java.net.URLConnection#setReadTimeout(int)
	 */
	public void setReadTimeout(int timeout)
	{
		theReadTimeout = timeout;
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

	/**
	 * Sets the key managers that this connector will use to provide client certificates to HTTPS
	 * connections when requested.
	 *
	 * @param keyManagers The key managers to validate HTTPS connections. Typically this will be a
	 *        single instance of {@link javax.net.ssl.X509KeyManager}.
	 * @throws NoSuchAlgorithmException If the "SSL" algorithm cannot be found in the environment
	 * @throws KeyManagementException If the SSL context cannot be initialized with the given key
	 *         managers
	 */
	public void setKeyManager(javax.net.ssl.KeyManager... keyManagers)
		throws NoSuchAlgorithmException, KeyManagementException
	{
		theKeyManagers = keyManagers;
		javax.net.ssl.SSLContext sc;
		sc = javax.net.ssl.SSLContext.getInstance("SSL");
		sc.init(theKeyManagers, theTrustManagers, new java.security.SecureRandom());
		theSocketFactory = sc.getSocketFactory();
	}

	/**
	 * Sets the trust managers that this connector will use to validate server certificates provided
	 * from HTTPS connections
	 *
	 * @param trustManagers The trust managers to validate HTTPS connections. Typically this will be
	 *        a single instance of {@link javax.net.ssl.X509TrustManager}.
	 * @throws NoSuchAlgorithmException If the "SSL" algorithm cannot be found in the environment
	 * @throws KeyManagementException If the SSL context cannot be initialized with the given trust
	 *         managers
	 */
	public void setTrustManager(javax.net.ssl.TrustManager... trustManagers)
		throws NoSuchAlgorithmException, KeyManagementException
	{
		theTrustManagers = trustManagers;
		javax.net.ssl.SSLContext sc;
		sc = javax.net.ssl.SSLContext.getInstance("SSL");
		sc.init(theKeyManagers, theTrustManagers, new java.security.SecureRandom());
		theSocketFactory = sc.getSocketFactory();
	}

	/** @param hv The hostname verifier that this connector will use HTTPS connections */
	public void setHostnameVerifier(javax.net.ssl.HostnameVerifier hv)
	{
		theHostnameVerifier = hv;
	}

	/**
	 * Gets the response from a call to the server
	 *
	 * @param params The parameters to send to the server. See {@link #sortParams(Object[])}.
	 * @return The input stream response from the server
	 * @throws IOException A {@link HttpResponseException} if the server responds with an error, or
	 *         another IOException if the connection fails otherwise
	 */
	public java.io.InputStream read(Object... params) throws IOException
	{
		java.util.HashMap<String, ? extends Object> [] sorted = sortParams(params);
		return read((java.util.Map<String, String>) sorted[0], sorted[1],
			(java.util.Map<String, String>) sorted[2]);
	}

	/**
	 * Gets the response from a call to the server
	 *
	 * @param getParams The parameters to pass to the server in the URL
	 * @param postParams The parameters to pass to the server through the connection itself
	 * @param reqProps The request properties for the connection
	 * @return The input stream response from the server
	 * @throws IOException A {@link HttpResponseException} if the server responds with an error, or
	 *         another IOException if the connection fails otherwise
	 */
	public java.io.InputStream read(java.util.Map<String, String> getParams,
		java.util.Map<String, ? extends Object> postParams, java.util.Map<String, String> reqProps)
			throws IOException
	{
		if(reqProps == null)
			reqProps = new java.util.HashMap<>();
		if(!reqProps.containsKey("Accept-Encoding"))
			reqProps.put("Accept-Encoding", "gzip");
		if(!reqProps.containsKey("Accept-Charset"))
			reqProps.put("Accept-Charset", "UTF-8");
		java.net.HttpURLConnection conn = connect(getParams, postParams, reqProps);
		try
		{
			java.io.InputStream is = conn.getInputStream();
			String encoding = conn.getContentEncoding();
			if(encoding != null && encoding.equalsIgnoreCase("gzip"))
				is = new java.util.zip.GZIPInputStream(is);
			return is;
		} catch(Throwable e)
		{
			IOException toThrow = new IOException("Call to " + theURL + " failed: " + e);
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		}
	}

	/**
	 * Connects to the server with all relevant parameters, properties, and cookies. The result is
	 * ready to be read.
	 *
	 * @param params The parameters to send to the server. See {@link #sortParams(Object[])}.
	 * @return The URL connection to the server, with the {@link java.net.URLConnection#connect()}
	 *         method already called.
	 * @throws IOException A {@link HttpResponseException} if the server responds with an error, or
	 *         another IOException if the connection fails otherwise
	 */
	public java.net.HttpURLConnection connect(Object... params) throws IOException
	{
		java.util.HashMap<String, ? extends Object> [] sorted = sortParams(params);
		return connect((java.util.Map<String, String>) sorted[0], sorted[1],
			(java.util.Map<String, String>) sorted[2]);
	}

	/**
	 * Connects to the server with all relevant parameters, properties, and cookies. The result is
	 * ready to be read.
	 *
	 * @param getParams The parameters to pass to the server in the URL
	 * @param postParams The parameters to pass to the server through the connection itself
	 * @param reqProps The request properties for the connection
	 * @return The URL connection to the server, with the {@link java.net.URLConnection#connect()}
	 *         method already called.
	 * @throws IOException A {@link HttpResponseException} if the server responds with an error, or
	 *         another IOException if the connection fails otherwise
	 */
	public java.net.HttpURLConnection connect(java.util.Map<String, String> getParams,
		java.util.Map<String, ? extends Object> postParams, java.util.Map<String, String> reqProps)
			throws IOException
	{
		java.net.HttpURLConnection conn = null;
		try
		{
			conn = getConnection(getParams);
			if(reqProps != null)
				for(java.util.Map.Entry<String, String> p : reqProps.entrySet())
					conn.setRequestProperty(p.getKey(), p.getValue());
			if(postParams != null)
			{
				conn.setRequestMethod("POST");
				conn.setDoOutput(true);
				conn.connect();
				java.io.OutputStreamWriter wr;
				java.io.OutputStream outStream = conn.getOutputStream();
				wr = new java.io.OutputStreamWriter(outStream);
				boolean first = true;
				for(java.util.Map.Entry<String, ? extends Object> p : postParams.entrySet())
				{
					if(!first)
						wr.write('&');
					first = false;
					wr.write(p.getKey());
					wr.write('=');
					if(p.getValue() instanceof String)
						wr.write((String) p.getValue());
					else if(p.getValue() instanceof java.io.Reader)
					{
						java.io.Reader reader = (java.io.Reader) p.getValue();
						int read = reader.read();
						while(read >= 0)
						{
							wr.write(read);
							read = reader.read();
						}
						reader.close();
					}
					else if(p.getValue() instanceof java.io.InputStream)
					{
						wr.flush();
						java.io.InputStream input = (java.io.InputStream) p.getValue();
						int read = input.read();
						while(read >= 0)
						{
							outStream.write(read);
							read = input.read();
						}
						input.close();
						outStream.flush();
					}
					else
						throw new IllegalArgumentException(
							"Unrecognized post parameter value type: "
								+ (p.getValue() == null ? "null" : p.getValue().getClass()
									.getName()));
				}
				wr.close();
			}
			else
			{
				conn.setRequestMethod("GET");
				conn.connect();
			}
			// Read cookies sent by the server
			java.util.Map<String, String> cookies = theCookies;
			if(cookies != null)
			{
				java.util.List<String> reqCookies = conn.getHeaderFields().get("Set-Cookie");
				if(reqCookies != null)
					for(String c : reqCookies)
					{
						String [] cSplit = c.split(";");
						for(String cs : cSplit)
						{
							cs = cs.trim();
							int eqIdx = cs.indexOf('=');
							if(eqIdx >= 0)
								cookies.put(cs.substring(0, eqIdx), cs.substring(eqIdx + 1));
							else
								cookies.put(cs, "true");
						}
					}
			}
			return conn;
		} catch(IOException e)
		{
			if(conn == null || conn.getResponseCode() == 200)
				throw e;
			HttpResponseException toThrow = new HttpResponseException(e.getMessage(),
				conn.getResponseCode(), conn.getResponseMessage());
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		} catch(Throwable e)
		{
			IOException toThrow = new IOException("Call to " + theURL + " failed: " + e);
			toThrow.setStackTrace(e.getStackTrace());
			throw toThrow;
		}
	}

	/**
	 * Creates a connection to the server, setting the GET parameters, cookies and security
	 * settings. The connection returned from this method is ready to connect to the server, but has
	 * not made a connection yet.
	 *
	 * @param getParams The parameters to pass to the server in the URL, as name-value pairs
	 * @return The URL connection, before the {@link java.net.URLConnection#connect()} method has
	 *         been called
	 * @throws IOException If the connection cannot be initiated
	 */
	public java.net.HttpURLConnection getConnection(String... getParams) throws IOException
	{
		java.util.HashMap<String, String> gp = new java.util.HashMap<>();
		for(int p = 0; p < getParams.length - 1; p += 2)
			gp.put(getParams[p], getParams[p + 1]);
		return getConnection(gp);
	}

	/**
	 * Creates a connection to the server, setting the GET parameters, cookies and security
	 * settings. The connection returned from this method is ready to connect to the server, but has
	 * not made a connection yet.
	 *
	 * @param getParams The parameters to pass to the server in the URL
	 * @return The URL connection, before the {@link java.net.URLConnection#connect()} method has
	 *         been called
	 * @throws IOException If the connection cannot be initiated
	 */
	public java.net.HttpURLConnection getConnection(java.util.Map<String, String> getParams)
		throws IOException
	{
		String callURL = theURL;
		if(getParams!=null && getParams.size() > 0)
		{
			StringBuilder args = new StringBuilder();
			boolean first = true;
			for(java.util.Map.Entry<String, String> p : getParams.entrySet())
			{
				args.append(first ? '?' : '&');
				first = false;
				args.append(encode(p.getKey())).append('=').append(encode(p.getValue()));
			}
			callURL += args.toString();
		}
		java.net.HttpURLConnection conn;
		java.net.URL url = new java.net.URL(callURL);
		conn = (java.net.HttpURLConnection) url.openConnection();
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
		java.util.Map<String, String> cookies = theCookies;
		if(cookies != null && !cookies.isEmpty())
		{
			StringBuilder cookie = new StringBuilder();
			boolean first = true;
			for(java.util.Map.Entry<String, String> c : cookies.entrySet())
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

	String BOUNDARY = "----------------" + QommonsUtils.getRandomString(16);

	/**
	 * Uploads data to a server
	 *
	 * @param fileName The file name to label the data with
	 * @param mimeType The type of the data to send
	 * @param getParams The parameters to send as part of the request URL
	 * @param formParams The parameters to send as form data in the content of the request
	 * @param reqProps The properties to set in the request
	 * @param receiver The stream in which the server's return data will be placed. May be null if the return data not needed or expected.
	 *            This stream will be used when the {@link java.io.OutputStream#close() close()} method is called on the return value of
	 *            this method. This method will not call close on the receiver.
	 * @return An output stream that the caller may use to write the file data to. The {@link java.io.OutputStream#close() close()} method
	 *         must be called when data writing is finished.
	 * @throws IOException If the data cannot be uploaded
	 */
	@SuppressWarnings("resource")
	public java.io.OutputStream uploadData(String fileName, String mimeType,
		java.util.Map<String, String> getParams,
		java.util.Map<String, ? extends Object> formParams, java.util.Map<String, String> reqProps,
		final java.io.OutputStream receiver) throws IOException
	{
		final java.net.HttpURLConnection conn = getConnection(getParams);

		conn.setRequestProperty("Accept-Charset", java.nio.charset.Charset.forName("UTF-8").name());
		conn.setDoOutput(true);
		conn.setDoInput(true);
		conn.setUseCaches(false);
		conn.setDefaultUseCaches(false);
		if(reqProps != null)
			for(java.util.Map.Entry<String, String> p : reqProps.entrySet())
				conn.setRequestProperty(p.getKey(), p.getValue());
		conn.setRequestProperty("Connection", "Keep-Alive");
		// c.setRequestProperty("HTTP_REFERER", codebase);
		conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + BOUNDARY);
		final java.io.OutputStream os;
		final java.io.Writer out;
		if(formParams != null && !formParams.isEmpty())
		{
			conn.setRequestMethod("POST");
			conn.connect();
			os = conn.getOutputStream();
			out = new LoggingWriter(new java.io.OutputStreamWriter(os, java.nio.charset.Charset.forName("UTF-8")), null);
			out.write("--");
			out.write(BOUNDARY);
			out.write("\r\n");

			for(java.util.Map.Entry<String, ? extends Object> p : formParams.entrySet())
			{
				// write content header
				out.write("Content-Disposition: form-data; name=\"" + p.getKey() + "\"\r\n\r\n");
				if(p.getValue() instanceof String)
					out.write((String) p.getValue());
				else if(p.getValue() instanceof java.io.Reader)
				{
					java.io.Reader reader = (java.io.Reader) p.getValue();
					int read = reader.read();
					while(read >= 0)
					{
						out.write(read);
						read = reader.read();
					}
					reader.close();
				}
				else if(p.getValue() instanceof java.io.InputStream)
					throw new IllegalArgumentException(
						"InputStreams may not be post parameters for uploading data");
				else
					throw new IllegalArgumentException("Unrecognized post parameter value type: "
						+ (p.getValue() == null ? "null" : p.getValue().getClass().getName()));
				out.write("\r\n--");
				out.write(BOUNDARY);
				out.write("\r\n");
			}
		}
		else
		{
			conn.setRequestMethod("GET");
			conn.connect();
			os = conn.getOutputStream();
			out = new LoggingWriter(new java.io.OutputStreamWriter(os, java.nio.charset.Charset.forName("UTF-8")), null);
			out.write("--");
			out.write(BOUNDARY);
			out.write("\r\n");
		}

		// write content header
		out.write("Content-Disposition: form-data; name=\"Upload Data\"; filename=\"" + fileName
			+ "\"");
		out.write("\r\n");
		out.write("Content-Type: ");
		if(mimeType != null)
			out.write(mimeType);
		else
			out.write("application/octet-stream");
		out.write("\r\n");
		out.write("\r\n");
		out.flush();
		return new java.io.OutputStream()
		{
			private boolean isClosed;

			@Override
			public void write(int b) throws IOException
			{
				os.write(b);
			}

			@Override
			public void write(byte [] b) throws IOException
			{
				os.write(b);
			}

			@Override
			public void write(byte [] b, int off, int len) throws IOException
			{
				os.write(b, off, len);
			}

			@Override
			public void flush() throws IOException
			{
				os.flush();
			}

			@Override
			public void close() throws IOException
			{
				if(isClosed)
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
				java.io.InputStream in = conn.getInputStream();
				int read = in.read();
				while(read >= 0)
				{
					if(receiver != null)
						receiver.write(read);
					read = in.read();
				}
				in.close();
			}
		};
	}

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
		java.net.HttpURLConnection conn;
		java.net.URL url = new java.net.URL(callURL);
		conn = (java.net.HttpURLConnection) url.openConnection();
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
				sc.init(theKeyManagers, new javax.net.ssl.TrustManager [] {retriever},
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
			sConn.setHostnameVerifier(new javax.net.ssl.HostnameVerifier()
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
	 * Sorts the set of parameters into GET and POST parameters and request properties.
	 *
	 * @param params The request parameters to sort, as name-value pairs. These may be punctuated by
	 *        {@link #GET}, {@link #POST}, or {@link #REQUEST_PROP} to designate sets of parameters
	 *        of each type. If the list does not start with one of these, the intial parameters are
	 *        assumed to be GET parameters.
	 * @return A 3-item array with the set of GET and POST parameters and request properties,
	 *         respectively
	 */
	public static java.util.HashMap<String, ? extends Object> [] sortParams(Object [] params)
	{
		java.util.HashMap<String, String> getParams = null;
		java.util.HashMap<String, Object> postParams = null;
		java.util.HashMap<String, String> reqProps = null;

		int type = 0; // 0=GET, 1=POST, 2=request property

		int p;
		for(p = 0; p < params.length - 1; p += 2)
		{
			if(params[p] == GET)
			{
				type = 0;
				p--;
				continue;
			}
			else if(params[p] == POST)
			{
				type = 1;
				p--;
				continue;
			}
			else if(params[p] == REQUEST_PROP)
			{
				type = 2;
				p--;
				continue;
			}
			if(!(params[p] instanceof String))
				throw new IllegalArgumentException("Parameter names must be strings: "
					+ ArrayUtils.toString(params));
			if(!(params[p + 1] instanceof String))
			{
				switch(type)
				{
				case 0:
					throw new IllegalArgumentException("GET parameter values must be strings: "
						+ ArrayUtils.toString(params));
				case 1:
					if(params[p + 1] instanceof java.io.Reader)
						break;
					else if(params[p + 1] instanceof java.io.InputStream)
						break;
					else
						throw new IllegalArgumentException(
							"POST parameter values must be String, Reader, or InputStream: "
								+ ArrayUtils.toString(params));
				default:
					throw new IllegalArgumentException("Request Property values must be strings: "
						+ ArrayUtils.toString(params));
				}
			}
			switch(type)
			{
			case 0:
				if(getParams == null)
					getParams = new java.util.HashMap<>();
				getParams.put((String) params[p], (String) params[p + 1]);
				break;
			case 1:
				if(postParams == null)
					postParams = new java.util.HashMap<>();
				postParams.put((String) params[p], params[p + 1]);
				break;
			default:
				if(reqProps == null)
					reqProps = new java.util.HashMap<>();
				reqProps.put((String) params[p], (String) params[p + 1]);
				break;
			}
		}

		return new java.util.HashMap [] {getParams, postParams, reqProps};
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
}

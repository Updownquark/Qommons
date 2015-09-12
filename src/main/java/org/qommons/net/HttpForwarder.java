/*
 * UrlForwarder.java Created Jan 22, 2010 by Andrew Butler, PSL
 */
package org.qommons.net;

import java.io.*;
import java.net.HttpURLConnection;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/** Allows forwarding of input from one servlet to another via {@link java.net.URL} */
public class HttpForwarder
{
	static int BUFFER_LENGTH = 32 * 1024;

	/** An interceptor can be used to examine or modify transactions from input or to output */
	public interface HttpInterceptor
	{
		/**
		 * Intercepts communications from the servlet request
		 * 
		 * @param request The request to get the data to write to the URL
		 * @param out The output stream to write to the URL
		 * @return Data to pass to
		 *         {@link #interceptOutput(HttpServletResponse, InputStream, OutputStream, Object)}
		 * @throws IOException If an error occurs reading the input or writing to the output
		 */
		Object interceptInput(HttpServletRequest request, OutputStream out) throws IOException;

		/**
		 * Intercepts responses from the URL
		 * 
		 * @param response The HTTP response to write the data to
		 * @param in The input stream of the URL
		 * @param out The output stream of the response
		 * @param fromInput The data from
		 *        {@link #interceptInput(javax.servlet.http.HttpServletRequest, OutputStream)}
		 * @throws IOException If an error occurs reading the input or writing to the output
		 */
		void interceptOutput(HttpServletResponse response, InputStream in, OutputStream out,
			Object fromInput) throws IOException;
	}

	/** Implements {@link HttpInterceptor} in a way that simply forwards the information */
	public static class DefaultHttpInterceptor implements HttpInterceptor
	{
		private byte [] buffer = new byte [BUFFER_LENGTH];

		public Object interceptInput(javax.servlet.http.HttpServletRequest request, OutputStream out)
			throws IOException
		{
			java.io.Writer writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(out));
			boolean first = true;
			for(Object param : request.getParameterMap().keySet())
			{
				if(!first)
					writer.write("&");
				writer.write(param + "=" + request.getParameter((String) param));
				first = false;
			}
			writer.flush();
			return null;
		}

		public void interceptOutput(HttpServletResponse response, InputStream in, OutputStream out,
			Object fromInput) throws IOException
		{
			int numBytesRead;
			while((numBytesRead = in.read(buffer, 0, buffer.length)) > 0)
				out.write(buffer, 0, numBytesRead);
		}
	}

	/** Parses some requests and responses into JSON for easier interception by subclasses */
	public static class JsonInterceptor extends DefaultHttpInterceptor
	{
		@Override
		public Object interceptInput(HttpServletRequest request, OutputStream out)
			throws IOException
		{
			JSONObject jsonReq = new JSONObject();
			java.util.Enumeration<String> paramEnum = request.getParameterNames();
			while(paramEnum.hasMoreElements())
			{
				String paramName = paramEnum.nextElement();
				jsonReq.put(paramName, request.getParameter(paramName));
			}
			return interceptJsonInput(request, jsonReq, out);
		}

		/**
		 * Accepts a JSON-parsed request
		 * 
		 * @param request The HTTP request
		 * @param jsonReq The request parameters. Individual parameters are not JSON-parsed
		 * @param out The output stream to write the parameters to
		 * @return An object to pass to
		 *         {@link #interceptOutput(HttpServletResponse, InputStream, OutputStream, Object)}
		 * @throws IOException If an error occurs writing the data
		 */
		public Object interceptJsonInput(HttpServletRequest request, JSONObject jsonReq,
			OutputStream out) throws IOException
		{
			java.io.Writer writer = new java.io.BufferedWriter(new java.io.OutputStreamWriter(out));
			boolean first = true;
			for(java.util.Map.Entry<Object, Object> entry : (java.util.Collection<java.util.Map.Entry<Object, Object>>) jsonReq
				.entrySet())
			{
				if(!first)
					writer.write("&");
				writer.write(entry.getKey() + "=" + entry.getValue());
				first = false;
			}
			writer.flush();
			return null;
		}

		@Override
		public void interceptOutput(HttpServletResponse response, InputStream in, OutputStream out,
			Object fromInput) throws IOException
		{
			String contentType = response.getContentType();
			if(contentType != null && contentType.contains("text/prisms-json"))
			{
				java.io.BufferedReader reader = new java.io.BufferedReader(
					new java.io.InputStreamReader(in));
				JSONArray retEvents = (JSONArray) org.json.simple.JSONValue.parse(reader);
				interceptJsonOutput(response, retEvents, out, fromInput);
			}
			else
				super.interceptOutput(response, in, out, fromInput);
		}

		/**
		 * Accepts a JSON-parsed response
		 * 
		 * @param response The HTTP response
		 * @param retEvents The JSON-parsed response
		 * @param out The output stream to write the response to
		 * @param fromInput The return value from
		 *        {@link #interceptInput(HttpServletRequest, OutputStream)}
		 * @throws IOException If an error occurs writing the data
		 */
		public void interceptJsonOutput(HttpServletResponse response, JSONArray retEvents,
			OutputStream out, Object fromInput) throws IOException
		{
			byte [] bytes = retEvents.toString().getBytes();
			if(bytes == null)
				return;

			BufferedOutputStream bos = new BufferedOutputStream(out, bytes.length);
			bos.write(bytes, 0, bytes.length);
		}
	}

	private HttpInterceptor theInterceptor;

	private String theCookiePrefix;

	/**
	 * Creates an HTTP forwarder
	 * 
	 * @param interceptor The interceptor for this forwarder to use
	 * @param cookiePrefix The prefix to use for cookies forwarded to the client by this forwarder.
	 *        This prefix prevents cookie-clashing.
	 */
	public HttpForwarder(HttpInterceptor interceptor, String cookiePrefix)
	{
		theInterceptor = interceptor;
		theCookiePrefix = cookiePrefix;
	}

	/**
	 * Forwards the HTTP request to a URL, sending the URL's response to the HTTP response
	 * 
	 * @param request The request to forward
	 * @param response The response to respond to
	 * @param url The URL to forward the request to and get the response from
	 * @throws IOException If an error occurs reading or writing HTTP data
	 */
	public void forward(javax.servlet.http.HttpServletRequest request,
		javax.servlet.http.HttpServletResponse response, String url) throws IOException
	{
		java.net.URL httpUrl = new java.net.URL(url);
		java.net.URLConnection con = httpUrl.openConnection();
		if(con == null)
			throw new java.net.ConnectException("Could not connect to " + url);

		if(con instanceof HttpURLConnection)
		{
			forwardRequestCookies(request, (HttpURLConnection) con);
			java.util.Enumeration<String> headerEnum = request.getHeaderNames();
			while(headerEnum.hasMoreElements())
			{
				String headerName = headerEnum.nextElement();
				if(headerName.equalsIgnoreCase("content-length") || headerName.contains("Cookie"))
					continue;
				String value = request.getHeader(headerName);
				con.addRequestProperty(headerName, value);
			}
		}

		con.setDoOutput(true);
		java.io.OutputStream out = con.getOutputStream();
		BufferedOutputStream bos = new BufferedOutputStream(out, BUFFER_LENGTH);
		Object fromInput = theInterceptor.interceptInput(request, bos);
		bos.flush();
		out.close();

		if(con instanceof HttpURLConnection)
			((HttpURLConnection) con).setInstanceFollowRedirects(true);
		con.connect();

		boolean zipped = false;
		if(con instanceof HttpURLConnection)
		{
			forwardResponseCookies((HttpURLConnection) con, response);
			for(java.util.Map.Entry<String, java.util.List<String>> entry : ((HttpURLConnection) con)
				.getHeaderFields().entrySet())
			{
				if(entry.getKey() == null || entry.getValue().size() == 0)
					continue;
				if(entry.getKey().toLowerCase().equals("content-encoding"))
					zipped = entry.getValue().get(0).equalsIgnoreCase("gzip");
				else if(entry.getKey().toLowerCase().equals("content-length")
					|| entry.getKey().contains("Cookie"))
					continue;
				StringBuilder value = new StringBuilder();
				for(String v : entry.getValue())
				{
					if(value.length() != 0)
						value.append(';');
					value.append(v);
				}
				response.setHeader(entry.getKey(), value.toString());
			}
		}

		java.io.InputStream in = con.getInputStream();
		if(in == null)
			throw new IOException("Could not get input stream from URL connection");

		out = response.getOutputStream();
		if(out == null)
			throw new IOException("Could not get output stream of response");

		BufferedInputStream bis = new BufferedInputStream(in, BUFFER_LENGTH);
		bos = new BufferedOutputStream(out, BUFFER_LENGTH);
		OutputStream os;
		InputStream is;
		if(zipped)
		{
			java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(bis);
			java.util.zip.GZIPOutputStream gzos = new java.util.zip.GZIPOutputStream(bos);
			is = gzis;
			os = gzos;
		}
		else
		{
			is = bis;
			os = bos;
		}
		try
		{
			theInterceptor.interceptOutput(response, is, os, fromInput);
		} finally
		{
			os.flush();
			out.close();
			in.close();
		}
	}

	void forwardRequestCookies(javax.servlet.http.HttpServletRequest request, HttpURLConnection conn)
	{
		javax.servlet.http.Cookie[] cookies = request.getCookies();
		StringBuilder cookiesString = new StringBuilder();
		if(cookies != null)
		{
			for(javax.servlet.http.Cookie cookie : cookies)
			{
				String cookieName = cookie.getName();
				if(!cookieName.startsWith(theCookiePrefix))
					continue;
				cookieName = cookieName.substring(theCookiePrefix.length());
				if(cookiesString.length() != 0)
					cookiesString.append(';');
				cookiesString.append(cookieName);
				cookiesString.append('=');
				cookiesString.append(cookie.getValue());
			}
			conn.addRequestProperty("Cookie", cookiesString.toString());
		}
	}

	void forwardResponseCookies(java.net.HttpURLConnection conn,
		javax.servlet.http.HttpServletResponse response) throws IOException
	{
		int i = 1;
		String headerFieldKey = conn.getHeaderFieldKey(i);
		while(headerFieldKey != null)
		{
			if(headerFieldKey.equalsIgnoreCase("set-cookie"))
			{
				String [] cookiesString = conn.getHeaderField(i).split(";");
				for(String cookie : cookiesString)
				{
					int idx = cookie.indexOf('=');
					String name = cookie.substring(0, idx).trim();
					if(name.equalsIgnoreCase("Path"))
						continue; // Path is a reserved word in the HTTP cookie spec
					String value = cookie.substring(idx + 1);
					response
						.addCookie(new javax.servlet.http.Cookie(theCookiePrefix + name, value));
				}
			}
			i++;
			headerFieldKey = conn.getHeaderFieldKey(i);
		}
	}

	/** @param interceptor The interceptor to set */
	public void setInterceptor(HttpInterceptor interceptor)
	{
		theInterceptor = interceptor;
	}
}

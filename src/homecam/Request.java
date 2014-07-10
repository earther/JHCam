package homecam;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.Map.Entry;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Request implements Runnable {
	static Logger log = LoggerFactory.getLogger("AccessLog");
	private final static int nonceLength = 64;
	private final static String salt = Utils.random(nonceLength);
	public final static int bufferSize = 4 * 1024;
	public final static int maxBufferSize = 256 * 1024;
	public SimpleDateFormat simFrmt = null;
	public SimpleDateFormat gmtFrmt = null;

	NanoHTTPD server;
	String headLine;
	String uri;
	String method;
	Properties header;
	Properties parms;
	Properties files;
	Socket socket;
	String[] auth = null;

	public Request(Socket socket, NanoHTTPD server) {
		/*
		 * this.uri = uri; this.method = method; this.header = header;
		 * this.parms = parms; this.files = files;
		 */
		this.socket = socket;
		this.server = server;
		Thread t = new Thread(this);
		t.setDaemon(true);
		t.start();
	}

	String getInetAddr() {
		String ret = header.getProperty("x-client-orig-addr");
		if (ret == null || ret.length() == 0)
			ret = socket.getInetAddress().toString();
		if (ret.length() > 0 && ret.charAt(0) == '/')
			ret = ret.substring(1);
		return ret;
	}

	String getNonce() {
		String inetAddr = this.getInetAddr();
		String jsessionid = header.getProperty("x-jsession-id");
		String userAgent = header.getProperty("user-agent");
		String nonce = salt + inetAddr + userAgent + jsessionid;
		char[] b = new char[nonceLength];
		int l = nonce.length();
		for (int p = 0, i = 0; p < b.length; ++i) {
			char c = nonce.charAt(l - i - 1);
			if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
				b[p] = c;
				++p;
			} else if (c >= 'A' && c <= 'F') {
				c = (char) ((int) c + 32);
				b[p] = c;
				++p;
			}
		}
		return new String(b);
		/*
		 * nonce = Utils.getHexChar(nonce); nonce = Utils.reverse(nonce); int n
		 * = nonce.length(); if (n > nonceLength) nonce = nonce.substring(0,
		 * nonceLength); else if (n < nonceLength) nonce = nonce +
		 * salt.substring(n); return nonce;
		 */
	}

	String getSessionKey() {
		return getNonce();
	}

	static String getSessionDigest(String sessionKey) {
		return DigestUtils.sha256Hex(sessionKey);
	}

	void initAuth() {
		if (this.auth != null)
			return;
		String auth = header.getProperty("authorization");
		if (auth != null && auth.indexOf(' ') > 0
				&& auth.length() > auth.indexOf(' ') + 1) {
			auth = auth.substring(auth.indexOf(' ') + 1);
			this.auth = auth.split(",");
		}
	}

	String getAuthField(String field) {
		initAuth();
		if (auth == null)
			return "";
		field += "=";
		for (String c : auth) {
			c = c.trim();
			if (c.startsWith(field)) {
				c = c.substring(field.length(), c.length());
				if (c.startsWith("\"") && c.endsWith("\"")) {
					c = c.substring(1, c.length() - 1);
				}
				return c;
			}
		}
		return "";
	}

	public String getCookie(String name) {
		String cookie = header.getProperty("cookie");
		if (cookie == null || cookie.length() == 0)
			return null;
		String[] cs = cookie.split(";");
		for (String c : cs) {
			c = c.trim();
			if (c.startsWith(name + '=')) {
				return c.substring(name.length() + 1);
			}
		}
		return null;
	}

	public void run() {
		try {
			InputStream is = socket.getInputStream();
			if (is == null)
				return;
			/*
			 * System.out.println("==" + socket.getPort() + "==");
			 * System.out.println(socket.getReceiveBufferSize());
			 * System.out.println(socket.getKeepAlive());
			 * System.out.println(socket.getReuseAddress());
			 * System.out.println(socket.getTcpNoDelay());
			 * System.out.println(socket.getSendBufferSize());
			 * System.out.println(socket.getSoLinger());
			 * System.out.println(socket.getSoTimeout()); if (mySocket
			 * instanceof SSLSocket) { SSLSocket sss = (SSLSocket) mySocket;
			 * System.out .println(Utils.join(sss.getEnabledCipherSuites()));
			 * System.out.println(Utils.join(sss.getEnabledProtocols()));
			 * System.out.println(sss.getUseClientMode());
			 * System.out.println(sss.getWantClientAuth()); }
			 * System.out.println("==" + socket.getInetAddress() + "==");
			 */

			// Read the first 8192 bytes.
			// The full header should fit in here.
			// Apache's default header limit is 8KB.
			int bufsize = 8192;
			byte[] buf = new byte[bufsize];
			int rlen = is.read(buf, 0, bufsize);
			if (rlen <= 0)
				return;

			// Create a BufferedReader for parsing the header.
			ByteArrayInputStream hbis = new ByteArrayInputStream(buf, 0, rlen);
			BufferedReader hin = new BufferedReader(new InputStreamReader(hbis));
			Properties pre = new Properties();
			parms = new Properties();
			header = new Properties();
			files = new Properties();

			// Decode the header into parms and header java properties
			decodeHeader(hin, pre, parms, header);
			method = pre.getProperty("method");
			uri = pre.getProperty("uri");
			Thread.currentThread().setName(this.getInetAddr());
			long size = 0x7FFFFFFFFFFFFFFFl;
			String contentLength = header.getProperty("content-length");
			if (contentLength != null) {
				try {
					size = Integer.parseInt(contentLength);
				} catch (NumberFormatException ex) {
				}
			}

			// We are looking for the byte separating header from body.
			// It must be the last byte of the first two sequential new
			// lines.
			int splitbyte = 0;
			boolean sbfound = false;
			while (splitbyte < rlen) {
				if (buf[splitbyte] == '\r' && buf[++splitbyte] == '\n'
						&& buf[++splitbyte] == '\r' && buf[++splitbyte] == '\n') {
					sbfound = true;
					break;
				}
				splitbyte++;
			}
			splitbyte++;

			// Write the part of body already read to ByteArrayOutputStream
			// f
			ByteArrayOutputStream f = new ByteArrayOutputStream();
			if (splitbyte < rlen)
				f.write(buf, splitbyte, rlen - splitbyte);

			// While Firefox sends on the first read all the data fitting
			// our buffer, Chrome and Opera sends only the headers even if
			// there is data for the body. So we do some magic here to find
			// out whether we have already consumed part of body, if we
			// have reached the end of the data to be sent or we should
			// expect the first byte of the body at the next read.
			if (splitbyte < rlen)
				size -= rlen - splitbyte + 1;
			else if (!sbfound || size == 0x7FFFFFFFFFFFFFFFl)
				size = 0;

			// Now read all the body and write it to f
			buf = new byte[512];
			while (rlen >= 0 && size > 0) {
				rlen = is.read(buf, 0, 512);
				size -= rlen;
				if (rlen > 0)
					f.write(buf, 0, rlen);
			}

			// Get the raw body as a byte []
			byte[] fbuf = f.toByteArray();

			// Create a BufferedReader for easily reading it as string.
			ByteArrayInputStream bin = new ByteArrayInputStream(fbuf);
			BufferedReader in = new BufferedReader(new InputStreamReader(bin));

			// If the method is POST, there may be parameters
			// in data section, too, read it:
			if (method.equalsIgnoreCase("POST")) {
				String contentType = "";
				String contentTypeHeader = header.getProperty("content-type");
				StringTokenizer st = new StringTokenizer(contentTypeHeader,
						"; ");
				if (st.hasMoreTokens()) {
					contentType = st.nextToken();
				}

				if (contentType.equalsIgnoreCase("multipart/form-data")) {
					// Handle multipart/form-data
					if (!st.hasMoreTokens())
						sendError(
								Server.HTTP_BADREQUEST,
								"BAD REQUEST: Content type is multipart/form-data but boundary missing. Usage: GET /example/file.html");
					String boundaryExp = st.nextToken();
					st = new StringTokenizer(boundaryExp, "=");
					if (st.countTokens() != 2)
						sendError(
								Server.HTTP_BADREQUEST,
								"BAD REQUEST: Content type is multipart/form-data but boundary syntax error. Usage: GET /example/file.html");
					st.nextToken();
					String boundary = st.nextToken();

					decodeMultipartData(boundary, fbuf, in, parms, files);
				} else {
					// Handle application/x-www-form-urlencoded
					String postLine = "";
					char pbuf[] = new char[512];
					int read = in.read(pbuf);
					while (read >= 0 && !postLine.endsWith("\r\n")) {
						postLine += String.valueOf(pbuf, 0, read);
						read = in.read(pbuf);
					}
					postLine = postLine.trim();
					decodeParms(postLine, parms);
				}
			}

			// Ok, now do the serve()
			Response r = server.serve(this);
			if (r == null)
				sendError(Server.HTTP_INTERNALERROR,
						"SERVER INTERNAL ERROR: Serve() returned a null response.");
			else
				sendResponse(r);

			in.close();
			is.close();
		} catch (IOException ioe) {
			try {
				sendError(Server.HTTP_INTERNALERROR,
						"SERVER INTERNAL ERROR: IOException: "
								+ ioe.getMessage());
			} catch (Throwable t) {
			}
		} catch (InterruptedException ie) {
			// Thrown by sendError, ignore and exit the thread.
		}
	}

	/**
	 * Decodes the sent headers and loads the data into java Properties' key -
	 * value pairs
	 **/
	private void decodeHeader(BufferedReader in, Properties pre,
			Properties parms, Properties header) throws InterruptedException {
		try {
			// Read the request line
			headLine = in.readLine();
			// System.out.println(inLine);
			if (headLine == null)
				return;
			StringTokenizer st = new StringTokenizer(headLine);
			if (!st.hasMoreTokens())
				sendError(Server.HTTP_BADREQUEST,
						"BAD REQUEST: Syntax error. Usage: GET /example/file.html");

			String method = st.nextToken();
			pre.put("method", method);

			if (!st.hasMoreTokens())
				sendError(Server.HTTP_BADREQUEST,
						"BAD REQUEST: Missing URI. Usage: GET /example/file.html");

			String uri = st.nextToken();

			// Decode parameters from the URI
			int qmi = uri.indexOf('?');
			if (qmi >= 0) {
				decodeParms(uri.substring(qmi + 1), parms);
				uri = decodePercent(uri.substring(0, qmi));
			} else
				uri = decodePercent(uri);

			// If there's another token, it's protocol version,
			// followed by HTTP headers. Ignore version but parse headers.
			// NOTE: this now forces header names lowercase since they are
			// case insensitive and vary by client.
			if (st.hasMoreTokens()) {
				String line = in.readLine();
				while (line != null && line.trim().length() > 0) {
					int p = line.indexOf(':');
					if (p >= 0)
						header.put(line.substring(0, p).trim().toLowerCase(),
								line.substring(p + 1).trim());
					line = in.readLine();
				}
			}

			pre.put("uri", uri);
		} catch (IOException ioe) {
			sendError(Server.HTTP_INTERNALERROR,
					"SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
		}
	}

	/**
	 * Decodes the Multipart Body data and put it into java Properties' key -
	 * value pairs.
	 **/
	private void decodeMultipartData(String boundary, byte[] fbuf,
			BufferedReader in, Properties parms, Properties files)
			throws InterruptedException {
		try {
			int[] bpositions = getBoundaryPositions(fbuf, boundary.getBytes());
			int boundarycount = 1;
			String mpline = in.readLine();
			while (mpline != null) {
				if (mpline.indexOf(boundary) == -1)
					sendError(
							Server.HTTP_BADREQUEST,
							"BAD REQUEST: Content type is multipart/form-data but next chunk does not start with boundary. Usage: GET /example/file.html");
				boundarycount++;
				Properties item = new Properties();
				mpline = in.readLine();
				while (mpline != null && mpline.trim().length() > 0) {
					int p = mpline.indexOf(':');
					if (p != -1)
						item.put(mpline.substring(0, p).trim().toLowerCase(),
								mpline.substring(p + 1).trim());
					mpline = in.readLine();
				}
				if (mpline != null) {
					String contentDisposition = item
							.getProperty("content-disposition");
					if (contentDisposition == null) {
						sendError(
								Server.HTTP_BADREQUEST,
								"BAD REQUEST: Content type is multipart/form-data but no content-disposition info found. Usage: GET /example/file.html");
					}
					StringTokenizer st = new StringTokenizer(
							contentDisposition, "; ");
					Properties disposition = new Properties();
					while (st.hasMoreTokens()) {
						String token = st.nextToken();
						int p = token.indexOf('=');
						if (p != -1)
							disposition.put(token.substring(0, p).trim()
									.toLowerCase(), token.substring(p + 1)
									.trim());
					}
					String pname = disposition.getProperty("name");
					pname = pname.substring(1, pname.length() - 1);

					String value = "";
					if (item.getProperty("content-type") == null) {
						while (mpline != null && mpline.indexOf(boundary) == -1) {
							mpline = in.readLine();
							if (mpline != null) {
								int d = mpline.indexOf(boundary);
								if (d == -1)
									value += mpline;
								else
									value += mpline.substring(0, d - 2);
							}
						}
					} else {
						if (boundarycount > bpositions.length)
							sendError(Server.HTTP_INTERNALERROR,
									"Error processing request");
						int offset = stripMultipartHeaders(fbuf,
								bpositions[boundarycount - 2]);
						String path = saveTmpFile(fbuf, offset,
								bpositions[boundarycount - 1] - offset - 4);
						files.put(pname, path);
						value = disposition.getProperty("filename");
						value = value.substring(1, value.length() - 1);
						do {
							mpline = in.readLine();
						} while (mpline != null
								&& mpline.indexOf(boundary) == -1);
					}
					parms.put(pname, value);
				}
			}
		} catch (IOException ioe) {
			sendError(Server.HTTP_INTERNALERROR,
					"SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
		}
	}

	/**
	 * Find the byte positions where multipart boundaries start.
	 **/
	public int[] getBoundaryPositions(byte[] b, byte[] boundary) {
		int matchcount = 0;
		int matchbyte = -1;
		List<Integer> matchbytes = new LinkedList<Integer>();
		for (int i = 0; i < b.length; i++) {
			if (b[i] == boundary[matchcount]) {
				if (matchcount == 0)
					matchbyte = i;
				matchcount++;
				if (matchcount == boundary.length) {
					matchbytes.add(matchbyte);
					matchcount = 0;
					matchbyte = -1;
				}
			} else {
				i -= matchcount;
				matchcount = 0;
				matchbyte = -1;
			}
		}
		int[] ret = new int[matchbytes.size()];
		int i = 0;
		for (int imb : matchbytes) {
			ret[i] = imb;
			++i;
		}
		return ret;
	}

	/**
	 * Retrieves the content of a sent file and saves it to a temporary file.
	 * The full path to the saved file is returned.
	 **/
	private String saveTmpFile(byte[] b, int offset, int len) {
		String path = "";
		if (len > 0) {
			String tmpdir = System.getProperty("java.io.tmpdir");
			try {
				File temp = File.createTempFile("NanoHTTPD", "", new File(
						tmpdir));
				OutputStream fstream = new FileOutputStream(temp);
				fstream.write(b, offset, len);
				fstream.close();
				path = temp.getAbsolutePath();
			} catch (Exception e) { // Catch exception if any
				System.err.println("Error: " + e.getMessage());
			}
		}
		return path;
	}

	/**
	 * It returns the offset separating multipart file headers from the file's
	 * data.
	 **/
	private int stripMultipartHeaders(byte[] b, int offset) {
		int i = 0;
		for (i = offset; i < b.length; i++) {
			if (b[i] == '\r' && b[++i] == '\n' && b[++i] == '\r'
					&& b[++i] == '\n')
				break;
		}
		return i + 1;
	}

	/**
	 * Decodes the percent encoding scheme. <br/>
	 * For example: "an+example%20string" -> "an example string"
	 */
	private String decodePercent(String str) throws InterruptedException {
		try {
			StringBuffer sb = new StringBuffer();
			for (int i = 0; i < str.length(); i++) {
				char c = str.charAt(i);
				switch (c) {
				case '+':
					sb.append(' ');
					break;
				case '%':
					sb.append((char) Integer.parseInt(str.substring(i + 1,
							i + 3), 16));
					i += 2;
					break;
				default:
					sb.append(c);
					break;
				}
			}
			return sb.toString();
		} catch (Exception e) {
			sendError(Server.HTTP_BADREQUEST,
					"BAD REQUEST: Bad percent-encoding.");
			return null;
		}
	}

	/**
	 * Decodes parameters in percent-encoded URI-format ( e.g.
	 * "name=Jack%20Daniels&pass=Single%20Malt" ) and adds them to given
	 * Properties. NOTE: this doesn't support multiple identical keys due to the
	 * simplicity of Properties -- if you need multiples, you might want to
	 * replace the Properties with a Hashtable of Vectors or such.
	 */
	private void decodeParms(String parms, Properties p)
			throws InterruptedException {
		if (parms == null)
			return;

		StringTokenizer st = new StringTokenizer(parms, "&");
		while (st.hasMoreTokens()) {
			String e = st.nextToken();
			int sep = e.indexOf('=');
			if (sep >= 0)
				p.put(decodePercent(e.substring(0, sep)).trim(),
						decodePercent(e.substring(sep + 1)));
		}
	}

	/**
	 * Returns an error message as a HTTP response and throws
	 * InterruptedException to stop further request processing.
	 */
	public void sendError(String status, String msg)
			throws InterruptedException {
		Response r = new Response(status, Server.MIME_PLAINTEXT,
				new ByteArrayInputStream(msg.getBytes()));
		r.markNoCache();
		sendResponse(r);
		throw new InterruptedException();
	}

	/**
	 * Sends given response to the socket.
	 */
	public void sendResponse(Response response) {
		try {
			if (response.status == null)
				throw new Error("sendResponse(): Status can't be null.");

			OutputStream out = socket.getOutputStream();
			PrintWriter pw = new PrintWriter(out);
			pw.print("HTTP/1.0 " + response.status + " \r\n");

			if (response.mimeType != null)
				pw.print("Content-Type: " + response.mimeType + "\r\n");

			if (response.header == null
					|| response.header.getProperty("Date") == null)
				pw.print("Date: " + getGmtFrmt().format(new Date()) + "\r\n");

			if (response.header != null) {
				for (Entry<Object, Object> rhe : response.header.entrySet()) {
					String key = (String) rhe.getKey();
					String value = (String) rhe.getValue();
					pw.print(key + ": " + value + "\r\n");
				}
			}

			pw.print("\r\n");
			pw.flush();

			if (response.data != null) {
				int pending = response.data.available(); // This is to support
				// partial sends, see
				// serveFile()
				int bs = Math.min(Math.max(pending, bufferSize), maxBufferSize);
				// System.out.println(" @ " + bs);
				byte[] buff = new byte[bs];
				while (pending > 0) {
					int read = response.data.read(buff, 0, ((pending > bs) ? bs
							: pending));
					// System.out.println(" | " + read);
					if (read <= 0)
						break;
					out.write(buff, 0, read);
					pending -= read;
				}
			} else if (response.outputStreamHandler != null) {
				response.outputStreamHandler.output(socket, out);
			}
			out.flush();
			out.close();
			if (response.data != null)
				response.data.close();

			if (!headLine.startsWith("GET /announce")) {
				String contentLength = (response.header
						.containsKey("Content-Length") ? response.header
						.getProperty("Content-Length") : "-");
				Object[] p = {
						this.getInetAddr(),
						this.getAuthField("username"),
						headLine,
						response.status.substring(0, response.status
								.indexOf(' ')), contentLength };
				log.info("{} {} \"{}\" {} {}", p);
			}
		} catch (IOException ioe) {
			// Couldn't write? No can do.
			try {
				socket.close();
			} catch (Throwable t) {
			}
		}
	}

	SimpleDateFormat getGmtFrmt() {
		if (gmtFrmt == null)
			gmtFrmt = Server.getGmtFrmt();
		return gmtFrmt;
	}

	SimpleDateFormat getSimpleFrmt() {
		if (simFrmt == null)
			simFrmt = Utils.getSimpleFormat();
		return simFrmt;
	}

	LinkedHashMap<String, Object> getLogInfo() {
		LinkedHashMap<String, Object> ret = new LinkedHashMap<String, Object>(4);
		ret.put("inet-addr", header.getProperty("x-client-orig-addr"));
		ret.put("user-agent", header.getProperty("user-agent"));
		ret.put("last-logged", getSimpleFrmt().format(new Date()));
		ret.put("last-request", headLine);
		return ret;
	}
}

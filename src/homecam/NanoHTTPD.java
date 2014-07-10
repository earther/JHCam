package homecam;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.TimeZone;

/**
 * changelog:
 * 
 * <code>
 *  line 131,132 bufferSize, maxBufferSize
 *  line 779 int bs = Math.min(Math.max(pending, bufferSize), maxBufferSize);
 *  line 1032 public static Hashtable theMimeTypes
 * </code>
 * 
 * A simple, tiny, nicely embeddable HTTP 1.0 (partially 1.1) server in Java
 * 
 * <p>
 * NanoHTTPD version 1.24, Copyright &copy; 2001,2005-2011 Jarno Elonen
 * (elonen@iki.fi, http://iki.fi/elonen/) and Copyright &copy; 2010 Konstantinos
 * Togias (info@ktogias.gr, http://ktogias.gr)
 * 
 * <p>
 * <b>Features + limitations: </b>
 * <ul>
 * 
 * <li>Only one Java file</li>
 * <li>Java 1.1 compatible</li>
 * <li>Released as open source, Modified BSD licence</li>
 * <li>No fixed config files, logging, authorization etc. (Implement yourself if
 * you need them.)</li>
 * <li>Supports parameter parsing of GET and POST methods</li>
 * <li>Supports both dynamic content and file serving</li>
 * <li>Supports file upload (since version 1.2, 2010)</li>
 * <li>Supports partial content (streaming)</li>
 * <li>Supports ETags</li>
 * <li>Never caches anything</li>
 * <li>Doesn't limit bandwidth, request time or simultaneous connections</li>
 * <li>Default code serves files and shows all HTTP parameters and headers</li>
 * <li>File server supports directory listing, index.html and index.htm</li>
 * <li>File server supports partial content (streaming)</li>
 * <li>File server supports ETags</li>
 * <li>File server does the 301 redirection trick for directories without '/'</li>
 * <li>File server supports simple skipping for files (continue download)</li>
 * <li>File server serves also very long files without memory overhead</li>
 * <li>Contains a built-in list of most common mime types</li>
 * <li>All header names are converted lowercase so they don't vary between
 * browsers/clients</li>
 * 
 * </ul>
 * 
 * <p>
 * <b>Ways to use: </b>
 * <ul>
 * 
 * <li>Run as a standalone app, serves files and shows requests</li>
 * <li>Subclass serve() and embed to your own program</li>
 * <li>Call serveFile() from serve() with your own base directory</li>
 * 
 * </ul>
 * 
 * Homepage: http://iki.fi/elonen/code/nanohttpd/
 * 
 * See the end of the source file for distribution license (Modified BSD
 * licence)
 */
public class NanoHTTPD {
	// ==================================================
	// API parts
	// ==================================================

	/**
	 * Override this to customize the server.
	 * <p>
	 * 
	 * (By default, this delegates to serveFile() and allows directory listing.)
	 * 
	 * @param uri
	 *            Percent-decoded URI without parameters, for example
	 *            "/index.cgi"
	 * @param method
	 *            "GET", "POST" etc.
	 * @param parms
	 *            Parsed, percent decoded parameters from URI and, in case of
	 *            POST, data.
	 * @param header
	 *            Header entries, percent decoded
	 * @return HTTP response, see class Response for details
	 */
	public Response serve(Request request) throws InterruptedException {
		System.out.println(request.method + " '" + request.uri + "' ");

		Enumeration<?> e = request.header.propertyNames();
		while (e.hasMoreElements()) {
			String value = (String) e.nextElement();
			System.out.println("  HDR: '" + value + "' = '"
					+ request.header.getProperty(value) + "'");
		}
		e = request.parms.propertyNames();
		while (e.hasMoreElements()) {
			String value = (String) e.nextElement();
			System.out.println("  PRM: '" + value + "' = '"
					+ request.parms.getProperty(value) + "'");
		}
		e = request.files.propertyNames();
		while (e.hasMoreElements()) {
			String value = (String) e.nextElement();
			System.out.println("  UPLOADED: '" + value + "' = '"
					+ request.files.getProperty(value) + "'");
		}

		return serveFile(request.uri, request.header, myRootDir, true);
	}

	/**
	 * Some HTTP response status codes
	 */
	public static final String HTTP_OK = "200 OK",
			HTTP_PARTIALCONTENT = "206 Partial Content",
			HTTP_RANGE_NOT_SATISFIABLE = "416 Requested Range Not Satisfiable",
			HTTP_REDIRECT = "301 Moved Permanently",
			HTTP_NOTMODIFIED = "304 Not Modified",
			HTTP_UNAUTHORIZED = "401 Unauthorized",
			HTTP_FORBIDDEN = "403 Forbidden", HTTP_NOTFOUND = "404 Not Found",
			HTTP_BADREQUEST = "400 Bad Request",
			HTTP_INTERNALERROR = "500 Internal Server Error",
			HTTP_NOTIMPLEMENTED = "501 Not Implemented";

	/**
	 * Common mime types for dynamic content
	 */
	public static final String MIME_PLAINTEXT = "text/plain",
			MIME_HTML = "text/html",
			MIME_DEFAULT_BINARY = "application/octet-stream",
			MIME_XML = "text/xml", MIME_JPG = "image/jpeg";

	// ==================================================
	// Socket & server code
	// ==================================================
	/**
	 * Starts a HTTP server to given port.
	 * <p>
	 * Throws an IOException if the socket is already in use
	 */
	public NanoHTTPD(int port, File wwwroot) throws IOException {
		myTcpPort = port;
		this.myRootDir = wwwroot;
		this.myServerSocket = new ServerSocket(myTcpPort);
		final NanoHTTPD server = this;
		myThread = new Thread(new Runnable() {
			public void run() {
				try {
					while (true)
						new Request(myServerSocket.accept(), server);
				} catch (IOException ioe) {
				}
			}
		});
		myThread.setDaemon(true);
		myThread.start();
	}

	/**
	 * Starts a HTTP server to given port.
	 * <p>
	 * Throws an IOException if the socket is already in use. Starts serving
	 * files from current directory.
	 */
	public NanoHTTPD(int port) throws IOException {
		this(port, new File(".").getAbsoluteFile());
	}

	/**
	 * Stops the server.
	 */
	public void stop() {
		try {
			myServerSocket.close();
			myThread.join();
		} catch (IOException ioe) {
		} catch (InterruptedException e) {
		}
	}

	/**
	 * Starts as a standalone file server and waits for Enter.
	 */
	public static void main(String[] args) {
		System.out
				.println("NanoHTTPD 1.24 (C) 2001,2005-2011 Jarno Elonen and (C) 2010 Konstantinos Togias\n"
						+ "(Command line options: [-p port] [-d root-dir] [--licence])\n");

		// Defaults
		int port = 80;
		File wwwroot = new File(".").getAbsoluteFile();

		// Show licence if requested
		for (int i = 0; i < args.length; ++i)
			if (args[i].equalsIgnoreCase("-p"))
				port = Integer.parseInt(args[i + 1]);
			else if (args[i].equalsIgnoreCase("-d"))
				wwwroot = new File(args[i + 1]).getAbsoluteFile();
			else if (args[i].toLowerCase().endsWith("licence")) {
				System.out.println(LICENCE + "\n");
				break;
			}

		try {
			new NanoHTTPD(port, wwwroot);
		} catch (IOException ioe) {
			System.err.println("Couldn't start server:\n" + ioe);
			System.exit(-1);
		}

		System.out.println("Now serving files in port " + port + " from \""
				+ wwwroot + "\"");
		System.out.println("Hit Enter to stop.\n");

		try {
			System.in.read();
		} catch (Throwable t) {
		}
	}

	/**
	 * URL-encodes everything between "/"-characters. Encodes spaces as '%20'
	 * instead of '+'.
	 */
	private String encodeUri(String uri) {
		String newUri = "";
		StringTokenizer st = new StringTokenizer(uri, "/ ", true);
		while (st.hasMoreTokens()) {
			String tok = st.nextToken();
			if (tok.equals("/"))
				newUri += "/";
			else if (tok.equals(" "))
				newUri += "%20";
			else {
				// newUri += URLEncoder.encode(tok);
				// For Java 1.4 you'll want to use this instead:
				try {
					newUri += URLEncoder.encode(tok, "UTF-8");
				} catch (java.io.UnsupportedEncodingException uee) {
				}
			}
		}
		return newUri;
	}

	private final int myTcpPort;
	private final ServerSocket myServerSocket;
	private final Thread myThread;
	public final File myRootDir;

	// ==================================================
	// File server code
	// ==================================================

	/**
	 * Serves file from homeDir and its' subdirectories (only). Uses only URI,
	 * ignores all headers and HTTP parameters.
	 */
	public Response serveFile(String uri, Properties header, File homeDir,
			boolean allowDirectoryListing) {
		Response res = null;

		// Make sure we won't die of an exception later
		if (!homeDir.isDirectory())
			res = new Response(HTTP_INTERNALERROR, MIME_PLAINTEXT,
					"INTERNAL ERRROR: serveFile(): given homeDir is not a directory.");

		if (res == null) {
			// Remove URL arguments
			uri = uri.trim().replace(File.separatorChar, '/');
			if (uri.indexOf('?') >= 0)
				uri = uri.substring(0, uri.indexOf('?'));

			// Prohibit getting out of current directory
			/*
			 * if (uri.startsWith("..") || uri.endsWith("..") ||
			 * uri.indexOf("../") >= 0) res = new Response(HTTP_FORBIDDEN,
			 * MIME_PLAINTEXT,
			 * "FORBIDDEN: Won't serve ../ for security reasons.");
			 */
		}

		File f = new File(homeDir, uri);
		if (!f.exists())
			f = new File(homeDir, uri + ".html");
		if (res == null && !f.exists())
			res = new Response(HTTP_NOTFOUND, MIME_PLAINTEXT,
					"Error 404, file not found.");

		// List the directory, if necessary
		if (res == null && f.isDirectory()) {
			// Browsers get confused without '/' after the
			// directory, send a redirect.
			if (!uri.endsWith("/")) {
				uri += "/";
				res = new Response(HTTP_REDIRECT, MIME_HTML,
						"<html><body>Redirected: <a href=\"" + uri + "\">"
								+ uri + "</a></body></html>");
				res.addHeader("Location", uri);
			}

			if (res == null) {
				// First try index.html and index.htm
				if (new File(f, "index.html").exists())
					f = new File(homeDir, uri + "/index.html");
				else if (new File(f, "index.htm").exists())
					f = new File(homeDir, uri + "/index.htm");
				// No index file, list the directory if it is readable
				else if (allowDirectoryListing && f.canRead()) {
					String[] files = f.list();
					String msg = "<html><body><h1>Directory " + uri
							+ "</h1><br/>";

					if (uri.length() > 1) {
						String u = uri.substring(0, uri.length() - 1);
						int slash = u.lastIndexOf('/');
						if (slash >= 0 && slash < u.length())
							msg += "<b><a href=\""
									+ uri.substring(0, slash + 1)
									+ "\">..</a></b><br/>";
					}

					if (files != null) {
						for (int i = 0; i < files.length; ++i) {
							File curFile = new File(f, files[i]);
							boolean dir = curFile.isDirectory();
							if (dir) {
								msg += "<b>";
								files[i] += "/";
							}

							msg += "<a href=\"" + encodeUri(uri + files[i])
									+ "\">" + files[i] + "</a>";

							// Show file size
							if (curFile.isFile()) {
								long len = curFile.length();
								msg += " &nbsp;<font size=2>(";
								if (len < 1024)
									msg += len + " bytes";
								else if (len < 1024 * 1024)
									msg += len / 1024 + "."
											+ (len % 1024 / 10 % 100) + " KB";
								else
									msg += len / (1024 * 1024) + "." + len
											% (1024 * 1024) / 10 % 100 + " MB";

								msg += ")</font>";
							}
							msg += "<br/>";
							if (dir)
								msg += "</b>";
						}
					}
					msg += "</body></html>";
					res = new Response(HTTP_OK, MIME_HTML, msg);
				} else {
					res = new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT,
							"FORBIDDEN: No directory listing.");
				}
			}
		}

		try {
			if (res == null) {
				// Get MIME type from file name extension, if possible
				String mime = null;
				int dot = f.getCanonicalPath().lastIndexOf('.');
				if (dot >= 0)
					mime = (String) theMimeTypes.get(f.getCanonicalPath()
							.substring(dot + 1).toLowerCase());
				if (mime == null)
					mime = MIME_DEFAULT_BINARY;

				// Calculate etag
				String etag = Integer.toHexString((f.getAbsolutePath()
						+ f.lastModified() + "" + f.length()).hashCode());

				// Support (simple) skipping:
				long startFrom = 0;
				long endAt = -1;
				String range = header.getProperty("range");
				if (range != null) {
					if (range.startsWith("bytes=")) {
						range = range.substring("bytes=".length());
						int minus = range.indexOf('-');
						try {
							if (minus > 0) {
								startFrom = Long.parseLong(range.substring(0,
										minus));
								endAt = Long.parseLong(range
										.substring(minus + 1));
							}
						} catch (NumberFormatException nfe) {
						}
					}
				}

				// Change return code and add Content-Range header when skipping
				// is requested
				long fileLen = f.length();
				if (range != null && startFrom >= 0) {
					if (startFrom >= fileLen) {
						res = new Response(HTTP_RANGE_NOT_SATISFIABLE,
								MIME_PLAINTEXT, "");
						res.addHeader("Content-Range", "bytes 0-0/" + fileLen);
						res.addHeader("ETag", etag);
					} else {
						if (endAt < 0)
							endAt = fileLen - 1;
						long newLen = endAt - startFrom + 1;
						if (newLen < 0)
							newLen = 0;

						final long dataLen = newLen;
						FileInputStream fis = new FileInputStream(f) {
							public int available() throws IOException {
								return (int) dataLen;
							}
						};
						fis.skip(startFrom);

						res = new Response(HTTP_PARTIALCONTENT, mime, fis);
						res.addHeader("Content-Length", "" + dataLen);
						res.addHeader("Content-Range", "bytes " + startFrom
								+ "-" + endAt + "/" + fileLen);
						res.addHeader("ETag", etag);
					}
				} else {
					String ifNoneMatch = header.getProperty("if-none-match");
					if (ifNoneMatch != null) {
						int n = ifNoneMatch.length();
						if (n > 0 && ifNoneMatch.charAt(0) == '"'
								&& ifNoneMatch.charAt(n - 1) == '"')
							ifNoneMatch = ifNoneMatch.substring(1, n - 1);
						if (etag.equals(ifNoneMatch))
							res = new Response(HTTP_NOTMODIFIED, mime);
					}
					if (res == null) {
						res = new Response(HTTP_OK, mime,
								new FileInputStream(f));
						res.addHeader("Content-Length", "" + fileLen);
					}
					res.addHeader("ETag", "\"" + etag + '"');
					res.addHeader("Cache-Control", "public, max-age=259200");

					SimpleDateFormat fmt = getGmtFrmt();

					String lastModified = fmt
							.format(new Date(f.lastModified()));
					res.addHeader("Last-Modified", lastModified);

					long lex = System.currentTimeMillis() + 259200 * 1000;
					Date dex = new Date(lex);
					res.addHeader("Expires", fmt.format(dex));
					res.addHeader("Age", "0");
				}
			}
		} catch (IOException ioe) {
			res = new Response(HTTP_FORBIDDEN, MIME_PLAINTEXT,
					"FORBIDDEN: Reading file failed.");
		}

		res.addHeader("Accept-Ranges", "bytes"); // Announce that the file
		// server accepts partial
		// content requestes
		return res;
	}

	public static SimpleDateFormat getGmtFrmt() {
		SimpleDateFormat gmtFrmt = new SimpleDateFormat(
				"E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
		gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
		return gmtFrmt;
	}

	/**
	 * Hashtable mapping (String)FILENAME_EXTENSION -> (String)MIME_TYPE
	 */
	public static Hashtable<String, String> theMimeTypes = new Hashtable<String, String>();
	static {
		StringTokenizer st = new StringTokenizer("css		text/css "
				+ "htm		text/html " + "html		text/html " + "xml		text/xml "
				+ "txt		text/plain " + "asc		text/plain " + "gif		image/gif "
				+ "jpg		image/jpeg " + "jpeg		image/jpeg " + "png		image/png "
				+ "mp3		audio/mpeg " + "m3u		audio/mpeg-url "
				+ "mp4		video/mp4 " + "ogv		video/ogg " + "flv		video/x-flv "
				+ "mov		video/quicktime "
				+ "swf		application/x-shockwave-flash "
				+ "js		application/javascript " + "pdf		application/pdf "
				+ "doc		application/msword " + "ogg		application/x-ogg "
				+ "zip		application/octet-stream "
				+ "exe		application/octet-stream " + "ico		image/x-icon ");
		while (st.hasMoreTokens())
			theMimeTypes.put(st.nextToken(), st.nextToken());
	}

	/**
	 * The distribution licence
	 */
	private static final String LICENCE = "Copyright (C) 2001,2005-2011 by Jarno Elonen <elonen@iki.fi>\n"
			+ "and Copyright (C) 2010 by Konstantinos Togias <info@ktogias.gr>\n"
			+ "\n"
			+ "Redistribution and use in source and binary forms, with or without\n"
			+ "modification, are permitted provided that the following conditions\n"
			+ "are met:\n"
			+ "\n"
			+ "Redistributions of source code must retain the above copyright notice,\n"
			+ "this list of conditions and the following disclaimer. Redistributions in\n"
			+ "binary form must reproduce the above copyright notice, this list of\n"
			+ "conditions and the following disclaimer in the documentation and/or other\n"
			+ "materials provided with the distribution. The name of the author may not\n"
			+ "be used to endorse or promote products derived from this software without\n"
			+ "specific prior written permission. \n"
			+ " \n"
			+ "THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR\n"
			+ "IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES\n"
			+ "OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.\n"
			+ "IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,\n"
			+ "INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT\n"
			+ "NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,\n"
			+ "DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY\n"
			+ "THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT\n"
			+ "(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE\n"
			+ "OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.";
}

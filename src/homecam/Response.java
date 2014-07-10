package homecam;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP response. Return one of these from serve().
 */
public class Response {
	public static final Logger log = LoggerFactory.getLogger(Response.class);

	/**
	 * Default constructor: response = HTTP_OK, data = mime = 'null'
	 */
	public Response() {
		this.status = Server.HTTP_OK;
	}

	public Response(String status, String mimeType) {
		this.status = status;
		this.mimeType = mimeType;
	}

	/**
	 * Basic constructor.
	 */
	public Response(String status, String mimeType, InputStream data) {
		this.status = status;
		this.mimeType = mimeType;
		this.data = data;
	}

	/**
	 * Convenience method that makes an InputStream out of given text.
	 */
	public Response(String status, String mimeType, String txt) {
		this.status = status;
		this.mimeType = mimeType;
		try {
			this.data = new ByteArrayInputStream(txt.getBytes("UTF-8"));
		} catch (java.io.UnsupportedEncodingException uee) {
			log.error(mimeType, uee);
		}
	}

	/**
	 * Adds given line to the header.
	 */
	public void addHeader(String name, String value) {
		header.put(name, value);
	}

	public void markNoCache() {
		addHeader("Cache-Control",
				"private, no-cache, no-store, must-revalidate, max-age=0");
		addHeader("Pragma", "no-cache");
		addHeader("Expires", "-1");
	}

	/**
	 * HTTP status code after processing, e.g. "200 OK", HTTP_OK
	 */
	public String status;

	/**
	 * MIME type of content, e.g. "text/html"
	 */
	public String mimeType;

	/**
	 * Data of the response, may be null.
	 */
	public InputStream data;

	// public InputStream fullStream;

	public OutputStreamHandler outputStreamHandler;

	/**
	 * Headers for the HTTP response. Use addHeader() to add lines.
	 */
	public Properties header = new Properties();
}

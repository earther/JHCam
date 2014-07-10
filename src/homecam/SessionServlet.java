package homecam;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionServlet extends Servlet {
	static Logger log = LoggerFactory.getLogger(SessionServlet.class);

	public Response serve(Request request) {
		Response ret = new Response(Server.HTTP_OK, Server.MIME_HTML);
		ret.outputStreamHandler = new OutputStreamHandler() {
			public void output(Socket socket, OutputStream output) {
				OutputStreamWriter out = new OutputStreamWriter(output);
				try {
					out
							.write("<html><head><title>Sessions</title><style type=\"text/css\">th, td {border:1px solid silver;}</style></head><body><table><tbody>\n");
					boolean header = false;
					for (Entry<String, LinkedHashMap<String, Object>> s : server.authener.logInfo
							.entrySet()) {
						LinkedHashMap<String, Object> row = s.getValue();
						if (!header) {
							header = true;
							for (Entry<String, Object> cell : row.entrySet()) {
								out.append("<th>");
								out.append(cell.getKey());
								out.append("</th>\n");
							}
							out.append("</tr><tr>\n");
						}
						out.append("<tr>");
						for (Entry<String, Object> cell : row.entrySet()) {
							out.append("<td>");
							out.append("" + cell.getValue());
							out.append("</td>\n");
						}
						out.append("</tr>");
					}
					out.append("</tbody></table></body></html>");
					out.flush();
				} catch (IOException e) {
					log.error("", e);
				}
			}
		};
		return ret;
	}
}

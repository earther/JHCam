package homecam;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Server extends NanoHTTPD {
	static Logger log = LoggerFactory.getLogger(Server.class);
	final Authener authener;
	int port = 80;
	File myRootDir = new File(".");
	String welcomePage = "/index.html";
	String servletUriSuffix = ".action";
	String servletClassSuffix = "Servlet";
	SystemProperties prop;
	Map<String, Servlet> servlets = new Hashtable<String, Servlet>(1);

	public static Server getInstance() throws IOException {
		String msg = "== START == ";
		try {
			msg += InetAddress.getLocalHost().toString() + '/'
					+ InetAddressing.getPublic();
		} catch (IOException ioe) {
			log.error("", ioe);
		}
		try {
			log.info(msg);
			Locale.setDefault(Locale.TAIWAN);
			SystemProperties prop = new SystemProperties();
			InputStream ins = ClassLoader
					.getSystemResourceAsStream("homecam.properties");
			prop.load(ins);
			ins.close();

			Utils.logMap(log, prop);

			int port = Integer
					.parseInt(prop.getProperty("homecam.Server.port"));
			File myRootDir = new File(
					prop.getProperty("homecam.Server.myRootDir"));

			Server s = new Server(port, myRootDir, prop);

			return s;
		} catch (Exception e) {
			log.error(e.toString(), e);
			return null;
		}
	}

	public Server(int port, File myRootDir, SystemProperties prop)
			throws IOException {
		super(port, myRootDir);
		this.prop = prop;
		this.welcomePage = prop.getProperty("homecam.Server.welcomePage");
		this.servletClassSuffix = prop
				.getProperty("homecam.Server.servletUriSuffix");
		this.servletClassSuffix = prop
				.getProperty("homecam.Server.servletClassSuffix");
		this.port = port;
		this.myRootDir = myRootDir;
		this.authener = new Authener(this);
	}

	public Response serve(Request request) throws InterruptedException {

		authener.authen(request);

		if (request.uri.equals("/")) {
			Response welcome = serveFile(request.uri, request.header,
					getHomeDir(), true);
			welcome.markNoCache();
			return welcome;
		} else if (request.getCookie("d") != null
				&& request.uri.endsWith(request.getCookie("d"))) {
			Servlet servlet = getServlet("/homecam.Capturer.action");
			return servlet.serve(request);
		} else if (request.uri.endsWith(servletUriSuffix)) {
			Servlet servlet = getServlet(request.uri);
			return servlet.serve(request);
		} else {
			try {
				String ext = request.uri.substring(
						request.uri.lastIndexOf('.') + 1).toLowerCase();
				if (ext.equals(request.uri) || theMimeTypes.containsKey(ext)
						|| !authener.requireAuthen(request.uri, request.method)) {
					return serveFile(request.uri, request.header, getHomeDir(),
							true);
				}
			} catch (Exception e) {
				log.error(request.uri, e);
			}
		}
		return new Response(Server.HTTP_FORBIDDEN, Server.MIME_PLAINTEXT,
				"Sorry cannot serve " + request.uri);
	}

	Servlet getServlet(String uri) {
		uri = uri.substring(1, uri.lastIndexOf('.')) + servletClassSuffix;
		Servlet ret = servlets.get(uri);
		if (ret != null)
			return ret;
		synchronized (servlets) {
			ret = servlets.get(uri);
			if (ret != null)
				return ret;
			try {
				log.info("newInstance {}", uri);
				ret = (Servlet) Class.forName(uri).newInstance();
				ret.setServer(this);
				ret.init();
			} catch (Exception e) {
				log.error(uri, e);
			}
			log.trace(uri);
			log.trace("ret {}", ret);
			servlets.put(uri, ret);
		}
		return ret;
	}

	public void stop() {
		log.info("stopping...");
		super.stop();
		authener.finalize();
		log.info("== STOP ==");
	}

	public File getHomeDir() {
		return myRootDir;
	}

	public static void main(String[] args) {
		try {
			Server s = Server.getInstance();
			System.in.read();
			s.stop();
		} catch (IOException e) {
			log.error(e.getLocalizedMessage(), e);
		}
	}
}

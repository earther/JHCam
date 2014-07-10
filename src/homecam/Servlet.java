package homecam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Servlet {
	public static final Logger log = LoggerFactory.getLogger(Servlet.class);
	Server server;

	public void setServer(Server server) {
		this.server = server;
	}

	public void init() throws Exception {
	}

	public abstract Response serve(Request request);

	public void finalize() {
		try {
			super.finalize();
		} catch (Throwable e) {
			log.error("", e);
		}
	}
}

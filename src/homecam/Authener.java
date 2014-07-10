package homecam;

import java.util.LinkedHashMap;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Authener {
	static Logger log = LoggerFactory.getLogger(Authener.class);
	Server s;
	HttpAuthener httpAuthener;
	FormAuthener formAuthener;
	final UserAccounts accounts = new UserAccounts();
	String[] allowedAddr;
	String[][] noAuthen;
	LinkedHashMap<String, LinkedHashMap<String, Object>> logInfo = new LinkedHashMap<String, LinkedHashMap<String, Object>>();

	public Authener(Server s) {
		this.s = s;
		httpAuthener = new HttpAuthener(s, accounts);
		formAuthener = new FormAuthener(s, accounts);
		allowedAddr = s.prop.getProperty("homecam.Authener.allowedAddr").split(
				";");
		String[] naPairs = s.prop.getProperty("homecam.Authener.noAuthen")
				.split(";");
		noAuthen = new String[naPairs.length][2];
		for (int i = 0; i < naPairs.length; ++i) {
			String pair = naPairs[i];
			String[] p = pair.split(":");
			noAuthen[i] = p;
		}
	}

	boolean requireAuthen(String uri, String method) {
		for (String[] p : noAuthen) {
			String m = p[0];
			String u = p[1];
			if (m.equals("") || m.equals(method)) {
				if (Utils.matches(u, uri))
					return false;
			}
		}
		return true;
	}

	boolean inetAllowed(Request request) {
		String inetAddr = request.getInetAddr();
		for (String ip : allowedAddr) {
			if (inetAddr.startsWith(ip))
				return true;
		}
		return false;
	}

	void authen(Request request) throws InterruptedException {

		if (request.uri.equals("/logout"))
			logout(request);

		if (!requireAuthen(request.uri, request.method) || inetAllowed(request))
			return;

		try {
			formAuthener.authenSession(request);
		} catch (Exception l) {
			httpAuthener.authen(request);
			formAuthener.authen(request);
		}
		logInfo.put(request.getSessionKey(), request.getLogInfo());
	}

	public void finalize() {
		for (Entry<String, LinkedHashMap<String, Object>> s : logInfo
				.entrySet()) {
			log.info(s.getKey());
			Utils.logMap(log, s.getValue());
		}
	}

	void logout(Request request) throws InterruptedException {
		httpAuthener.logout(request);
		formAuthener.logout(request);
		Response r = new Response(Server.HTTP_OK, Server.MIME_PLAINTEXT, "Bye");
		r.markNoCache();
		request.sendResponse(r);
		throw new InterruptedException();
	}
}

package homecam;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

import javax.net.ssl.SSLSocket;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FormAuthener implements Authenable {
	static Logger log = LoggerFactory.getLogger(FormAuthener.class);
	Server s;
	UserAccounts accounts;
	long loginTimeout = 5 * 60 * 1000;
	String loginFile;
	FormAuthenerStore store;

	public FormAuthener(Server s, UserAccounts accounts) {
		this.s = s;
		this.accounts = accounts;
		loginTimeout = Long.parseLong(s.prop
				.getProperty("homecam.Authener.loginTimeout"));
		loginFile = s.prop.getProperty("homecam.FormAuthener.loginFile");

		store = new FormAuthenerStore(this);
		store.load();
	}

	Map<String, Long> allowed = new HashMap<String, Long>(4);
	Properties sessions = new Properties();
	Properties landingPages = new Properties();

	public void authen(Request request) throws InterruptedException {
		try {
			authenLogin(request);
		} catch (InterruptedException e) {
			throw e;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			request.sendResponse(getAuthenResponse(request));
			throw new InterruptedException(e.getMessage());
		}
	}

	void authenSession(Request request) throws Exception {
		final String sessionKey = request.getSessionKey();
		Long lastAccess = allowed.get(sessionKey);
		long sysdate = System.currentTimeMillis();
		if (lastAccess != null && sysdate < lastAccess + loginTimeout) {
			String cookie = request.getCookie("d");
			if (cookie != null && cookie.length() == 64
					&& sessions.getProperty(sessionKey).equals(cookie)) {
				Thread t = new Thread() {
					public void run() {
						store.store(sessionKey, null);
					}
				};
				t.setName("Store");
				t.setPriority(Thread.MIN_PRIORITY);
				t.start();
				return;
			}
		}
		String msg = "Server: " + new Date(sysdate) + "; Last Access: "
				+ new Date(lastAccess);
		log.info("fail {}: {}", msg, request.socket);
		throw new Exception(msg);
	}

	Response getAuthenResponse(Request request) {
		Response r = s.serveFile(loginFile, request.header, s.myRootDir, false);
		r.markNoCache();
		landingPages.setProperty(request.getSessionKey(), request.uri);
		return r;
	}

	String getLandingLocation(Request request, String sessionKey) {
		String host = request.header.getProperty("host");
		StringBuilder l = new StringBuilder(host.length() + 10);

		boolean ssl = request.socket instanceof SSLSocket;
		l.append("http");
		if (ssl)
			l.append('s');
		l.append("://");
		l.append(host);
		int port = request.socket.getLocalPort();
		if (ssl && port == 443)
			;
		else if (!ssl && port == 80)
			;
		else {
			String suffix = ":" + port;
			if (!host.endsWith(suffix))
				l.append(suffix);
		}
		String landingPage = landingPages.containsKey(sessionKey) ? landingPages
				.getProperty(sessionKey)
				: "/";
		l.append(landingPage);
		return l.toString();
	}

	void authenLogin(Request request) throws Exception {
		long sysdate = System.currentTimeMillis();
		if (accounts.isRegistered(request)) {
			success(request);
		}

		String t = request.parms.getProperty("t");
		String s = request.parms.getProperty("s");
		String n = request.parms.getProperty("n");
		String z = request.parms.getProperty("z");
		if (t == null || t.length() == 0 || s == null || s.length() != 64
				|| n == null || n.length() != 64 || z == null
				|| z.length() != 64 || !request.method.equals("POST")) {
			// not a login action
			throw new Exception("Not login "
					+ Utils.join(request.parms.entrySet().toArray()));
		}

		long _sysdate = Long.parseLong(t);
		String q = request.parms.getProperty("q");
		if (q == null || _sysdate != Long.valueOf(q, 36)) {
			throw new Exception("q " + q);
		}

		q = Utils.reverse(q);
		q = DigestUtils.sha256Hex(q);
		if (!q.equals(request.parms.getProperty("p"))) {
			throw new Exception("p " + request.parms.getProperty("p"));
		}

		if (Math.abs(sysdate - _sysdate) < loginTimeout) {
			// s: password digest = SHA256( n + t + password)
			// n: client random generated nonce
			// t: client creation timestamp
			// password: predefined known (not transmitted)
			String digest = n + ' ' + t + ' '
					+ accounts.getFormPassword(request);
			digest = DigestUtils.sha256Hex(digest);
			if (digest.equals(s)) {
				success(request);
			}
		}
		String msg = "Server: "
				+ Utils.getSimpleFormat().format(new Date(sysdate))
				+ "; Client: "
				+ Utils.getSimpleFormat().format(new Date(_sysdate));
		log.info("fail {}: {}", msg, request.socket);
		throw new Exception(msg);
	}

	private void success(Request request) throws InterruptedException {
		String sessionKey = request.getSessionKey();
		String digest = Request.getSessionDigest(sessionKey);
		store.store(sessionKey, digest);

		Response r = new Response();
		r.status = Server.HTTP_REDIRECT;
		r.mimeType = Server.MIME_PLAINTEXT;

		SimpleDateFormat gmtFrmt = new SimpleDateFormat(
				"E, d MMM yyyy HH:mm:ss 'GMT'", Locale.US);
		gmtFrmt.setTimeZone(TimeZone.getTimeZone("GMT"));
		String cookie = String.format("d=%s; expires=%s; path=/", digest,
				gmtFrmt.format(new Date(System.currentTimeMillis() + 7 * 24
						* 60 * 60 * 1000)));
		r.addHeader("Set-Cookie", cookie);

		String location = getLandingLocation(request, sessionKey);
		r.addHeader("Location", location);
		request.sendResponse(r);
		String msg = "Success " + request.socket.toString() + '>' + location;
		log.info(msg);
		Utils.logMap(log, request.header);
		Utils.logMap(log, request.parms);
		throw new InterruptedException(msg);
	}

	public void logout(Request request) {
		String sessionKey = request.getSessionKey();
		allowed.remove(sessionKey);
		sessions.remove(sessionKey);
	}
}

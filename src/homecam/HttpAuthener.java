package homecam;

import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpAuthener implements Authenable {
	static Logger log = LoggerFactory.getLogger(HttpAuthener.class);
	private final boolean QUALITY_OF_PROTECTION_AUTH = true;
	final Server s;
	final UserAccounts accounts;
	final long loginTimeout;
	final String realm;
	final Map<String, Long> allowed = new HashMap<String, Long>(4);

	public HttpAuthener(Server s, UserAccounts accounts) {
		this.s = s;
		this.accounts = accounts;
		loginTimeout = Long.parseLong(s.prop
				.getProperty("homecam.Authener.loginTimeout"));
		realm = s.prop.getProperty("homecam.HttpAuthener.realm");
	}

	Response getAuthenResponse(Request request) {
		String nonce = request.getNonce();
		allowed.put(nonce, System.currentTimeMillis());
		Response r = new Response(Server.HTTP_UNAUTHORIZED,
				Server.MIME_PLAINTEXT);
		String header = "Digest realm=\"" + realm + "\", nonce=\"" + nonce
				+ "\"";
		if (QUALITY_OF_PROTECTION_AUTH) {
			header += ", qop=\"auth\"";
		}
		r.addHeader("WWW-Authenticate", header);
		r.markNoCache();
		return r;
	}

	void checkNonce(Request request) throws Exception {
		String nonce = request.getNonce();
		String _nonce = request.getAuthField("nonce");
		if (!nonce.equals(_nonce))
			throw new GeneralSecurityException(_nonce);
		Long lastAccess = allowed.get(nonce);
		long sysdate = System.currentTimeMillis();
		if (lastAccess != null && sysdate < lastAccess + loginTimeout) {
			allowed.put(nonce, sysdate);
			return;
		}
		throw new Exception("Server: " + new Date(sysdate) + "; Last Access: "
				+ new Date(lastAccess));
	}

	public void authen(Request request) throws InterruptedException {
		try {
			authenDigest(request);
		} catch (InterruptedException e) {
		} catch (Exception e) {
			if (e instanceof GeneralSecurityException) {
				log.trace(e.getMessage(), e);
			} else {
				log.error(e.getMessage(), e);
			}
			request.sendResponse(getAuthenResponse(request));
			throw new InterruptedException(e.getMessage());
		}
	}

	void authenDigest(Request request) throws Exception {
		checkNonce(request);
		String ha1 = accounts.getHttpAuthenHa1(request);

		String uri = request.getAuthField("uri");
		String ha2 = request.method + ':' + uri;
		ha2 = DigestUtils.md5Hex(ha2);

		String nonce = request.getAuthField("nonce");
		String response;
		if (QUALITY_OF_PROTECTION_AUTH) {
			String nc = request.getAuthField("nc");
			String cnonce = request.getAuthField("cnonce");
			response = ha1 + ':' + nonce + ':' + nc + ':' + cnonce + ":auth:"
					+ ha2;
		} else {
			response = ha1 + ':' + nonce + ':' + ha2;
		}
		response = DigestUtils.md5Hex(response);

		String _response = request.getAuthField("response");
		if (!_response.equals(response)) {
			throw new Exception(request.header.getProperty("authorization"));
		}
		/*
		 * log.info("Success " + request.socket.toString() + ':' +
		 * request.getAuthField("username")); Utils.logMap(log, request.header);
		 * Utils.logMap(log, request.parms);
		 */
	}

	public void logout(Request request) {
		String nonce = request.getNonce();
		allowed.remove(nonce);
	}
}

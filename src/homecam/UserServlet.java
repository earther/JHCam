package homecam;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserServlet extends Servlet {
	static Logger log = LoggerFactory.getLogger(UserServlet.class);

	public Response serve(Request request) {
		String username = request.parms.getProperty("username");
		if (request.method.equals("POST") && username != null) {
			String action = request.parms.getProperty("action");
			try {
				if ("Change Password".equals(action)) {
					set(request);
				} else if ("Register Browser".equals(action)) {
					server.authener.accounts.register(username, request);
				} else {
					throw new IllegalArgumentException(action);
				}
				Response r = new Response(Server.HTTP_OK,
						Server.MIME_PLAINTEXT, Server.HTTP_OK);
				return r;
			} catch (Exception e) {
				log.error(username, e);
				try {
					request.sendError(Server.HTTP_FORBIDDEN, username + ':'
							+ ' ' + e.getLocalizedMessage());
				} catch (InterruptedException inte) {
					log.error(username, inte);
				}
			}
		}
		return server.serveFile("/user.html", request.header, server.myRootDir,
				false);
	}

	void set(Request request) throws Exception {
		String username = request.parms.getProperty("username");

		String po1 = server.authener.accounts
				.getProperty(UserAccounts.HTTP_AUTHEN + username);

		if (po1 != null) {
			String _po1 = request.parms.getProperty("po1");
			_po1 = SecurityUtils.aesEncrypt(_po1, username).trim();
			if (!po1.equals(_po1))
				throw new Exception("Wrong Old Password 1");

			String po2 = server.authener.accounts
					.getProperty(UserAccounts.FORM_AUTHEN + username);
			String _po2 = request.parms.getProperty("po2");
			_po2 = SecurityUtils.aesEncrypt(_po2, username).trim();
			if (!po2.equals(_po2))
				throw new Exception("Wrong Old Password 2");
		}
		String pn1 = request.parms.getProperty("pn1");
		String pr1 = request.parms.getProperty("pr1");
		if (!pn1.equals(pr1))
			throw new Exception("Wrong New Password 1");
		String pn2 = request.parms.getProperty("pn2");
		String pr2 = request.parms.getProperty("pr2");
		if (!pn2.equals(pr2))
			throw new Exception("Wrong New Password 2");

		if (pn1.length() < 6)
			throw new Exception("Password 1 Length < 6");
		if (pn1.equals(username))
			throw new Exception("Password 1 == " + username);
		if (pn2.length() < 8)
			throw new Exception("Password 1 Length < 8");
		if (pn2.equals(username))
			throw new Exception("Password 2 == " + username);
		if (pn1.equals(pn2))
			throw new Exception("Password 1 == Password 2");

		server.authener.accounts.setUser(username, pn1, pn2);
	}
}

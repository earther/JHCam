package homecam;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UserAccounts extends Properties {
	private static final long serialVersionUID = 1L;
	static Logger log = LoggerFactory.getLogger(UserAccounts.class);
	private Properties httpAuthenHa1 = new Properties();
	public final static String HTTP_AUTHEN = "http.";
	public final static String FORM_AUTHEN = "form.";
	public final static String REGISTER_INETADDR = "register.inetaddr.";
	public final static String REGISTER_BROWSER = "register.browser.";
	public final static String FILE = "passwd";

	public UserAccounts() {
		super();
		try {
			InputStream in = new FileInputStream(FILE);
			this.load(in);
			in.close();
		} catch (IOException e) {
			log.error(e.getMessage(), e);
		}
	}

	public String getHttpAuthenHa1(Request request) {
		String username = request.getAuthField("username");
		String ha1 = httpAuthenHa1.getProperty(username);
		if (ha1 != null)
			return ha1;
		String realm = request.getAuthField("realm");
		String password = this.getProperty(HTTP_AUTHEN + username);
		password = SecurityUtils.aesDecrypt(username, password);
		ha1 = username + ':' + realm + ':' + password;
		ha1 = DigestUtils.md5Hex(ha1);
		httpAuthenHa1.setProperty(username, ha1);
		return ha1;
	}

	public String getFormPassword(Request request) {
		String username = request.getAuthField("username");
		String password = this.getProperty(FORM_AUTHEN + username);
		password = SecurityUtils.aesDecrypt(username, password);
		return password;
	}

	public boolean isRegistered(Request request) {
		String inetAddr = request.getInetAddr();
		String browser = request.header.getProperty("user-agent");
		String username = request.getAuthField("username");
		String _inetAddr = this.getProperty(REGISTER_INETADDR + username);
		String _browser = this.getProperty(REGISTER_BROWSER + username);
		if (inetAddr.equals(_inetAddr) && browser.equals(_browser)) {
			log.info("isRegistered {}/{}", inetAddr, browser);
			return true;
		}
		return false;
	}

	public boolean register(String username, Request request) {
		String inetAddr = request.getInetAddr();
		String browser = request.header.getProperty("user-agent");
		this.setProperty(REGISTER_INETADDR + username, inetAddr);
		this.setProperty(REGISTER_BROWSER + username, browser);
		try {
			OutputStream out = new FileOutputStream(FILE);
			this.store(out, "Total " + this.size());
			out.close();
			return true;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return false;
		}
	}

	public boolean setUser(String username, String http, String form) {
		http = SecurityUtils.aesEncrypt(http, username).trim();
		form = SecurityUtils.aesEncrypt(form, username).trim();
		this.setProperty(HTTP_AUTHEN + username, http);
		this.setProperty(FORM_AUTHEN + username, form);
		try {
			OutputStream out = new FileOutputStream(FILE);
			this.store(out, "Total " + this.size());
			out.close();
			return true;
		} catch (Exception e) {
			log.error(e.getMessage(), e);
			return false;
		}
	}

	public static void main(String[] args) {
		UserAccounts ua = new UserAccounts();
		ua.setUser("jasmine", "2+2=5 ignorance;", "2+2=4 true freedom.");
	}
}

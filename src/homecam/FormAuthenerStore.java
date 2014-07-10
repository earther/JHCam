package homecam;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FormAuthenerStore {
	static Logger log = LoggerFactory.getLogger(FormAuthenerStore.class);
	FormAuthener formAuthener;
	String lastSessionKey = null;

	FormAuthenerStore(FormAuthener formAuthener) {
		this.formAuthener = formAuthener;
	}

	public void load() {
		try {
			_load();
		} catch (IOException e) {
			log.error("", e);
		}
	}

	void _load() throws IOException {
		Properties prop = new Properties();
		FileInputStream inStream = new FileInputStream(
				"formAuthener.allowed.properties");
		prop.load(inStream);
		inStream.close();

		for (Object k : prop.keySet()) {
			if (!(k instanceof String))
				continue;
			String key = (String) k;
			String val = prop.getProperty(key);
			try {
				Long value = Long.parseLong(val);
				formAuthener.allowed.put(key, value);
			} catch (Exception e) {
				log.error(val, e);
			}
		}

		synchronized (this) {
			inStream = new FileInputStream("formAuthener.sessions.properties");
			formAuthener.sessions.load(inStream);
			inStream.close();
		}
		removeTimeouts();
	}

	synchronized void removeTimeouts() {
		long sysdate = System.currentTimeMillis();
		List<String> toBeRemoved = new LinkedList<String>();
		for (String sessionKey : formAuthener.allowed.keySet()) {
			Long lastAccess = formAuthener.allowed.get(sessionKey);
			if (lastAccess == null
					|| sysdate > lastAccess + formAuthener.loginTimeout) {
				toBeRemoved.add(sessionKey);
				log.info("{} exceeds timeout: {}", sessionKey, lastAccess);
			}
		}
		for (String sessionKey : toBeRemoved) {
			formAuthener.allowed.remove(sessionKey);
			formAuthener.sessions.remove(sessionKey);
		}
		toBeRemoved.clear();
		for (Object sk : formAuthener.sessions.keySet()) {
			String sessionKey = (String) sk;
			if (!formAuthener.allowed.containsKey(sessionKey)) {
				toBeRemoved.add(sessionKey);
			}
		}
		for (String sessionKey : toBeRemoved) {
			formAuthener.sessions.remove(sessionKey);
		}
	}

	synchronized void _store(String sessionKey, String digest)
			throws IOException {
		if (sessionKey != null)
			formAuthener.allowed.put(sessionKey, System.currentTimeMillis());

		Properties prop = new Properties();
		for (String sk : formAuthener.allowed.keySet()) {
			Long lastAccess = formAuthener.allowed.get(sk);
			prop.setProperty(sk, lastAccess.toString());
		}

		FileOutputStream out = new FileOutputStream(
				"formAuthener.allowed.properties");
		prop.store(out, sessionKey);
		out.close();

		if (digest == null)
			digest = Request.getSessionDigest(sessionKey);
		formAuthener.sessions.setProperty(sessionKey, digest);
		out = new FileOutputStream("formAuthener.sessions.properties");
		formAuthener.sessions.store(out, sessionKey);
		out.close();
	}

	public void store(final String sessionKey, final String digest) {
		if (sessionKey != null && sessionKey.equals(lastSessionKey)) {
			removeTimeouts();
			if (formAuthener.allowed.containsKey(sessionKey)) {
				return;
			}
		}
		log.info("store {}", sessionKey);
		lastSessionKey = sessionKey;
		try {
			_store(sessionKey, digest);
		} catch (IOException e) {
			log.error("", e);
		}
	}

	public void finalize() {
		store(null, null);
	}
}

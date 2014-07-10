package homecam;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SystemProperties extends java.util.Properties {
	static Logger log = LoggerFactory.getLogger(SystemProperties.class);
	private static final long serialVersionUID = 1L;

	public static enum Driver {
		lti, SasCam
	};

	static String hostname = null;
	static Driver driver;

	public String getProperty(String key) {
		if (hostname == null)
			try {
				hostname = InetAddress.getLocalHost().getHostName();
			} catch (UnknownHostException e) {
				log.error(key, e);
			}
		try {
			String ret = super.getProperty(key + '.' + hostname);
			if (ret != null)
				return ret;
			ret = super.getProperty(key);
			if (ret != null)
				return ret;
		} catch (Exception e) {
		}
		return null;
	}
}

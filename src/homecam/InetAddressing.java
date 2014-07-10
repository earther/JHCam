package homecam;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InetAddressing {

	public static final Logger log = LoggerFactory
			.getLogger(InetAddressing.class);
	public static final Pattern IP = Pattern
			.compile("(\\d{1,3}\\.){3}\\d{1,3}");
	public static final String userAgent = "Mozilla/5.0 (Windows NT 5.1) AppleWebKit/537.31 (KHTML, like Gecko) Chrome/26.0.1410.64 Safari/537.31";
	public static final String host = "checkip.dyndns.com";
	public static final int maxTrial = 5;

	public static String getPrivate() throws UnknownHostException {
		for (int i = 0; i < maxTrial; ++i) {
			String ret = InetAddress.getLocalHost().getHostAddress();
			if (!"127.0.0.1".equals(ret))
				return ret;
		}
		return null;
	}

	public static String getPublic() throws IOException {
		String ret = null;
		for (int i = 0; i < maxTrial && ret == null; ++i) {
			try {
				ret = _getPublic();
			} catch (UnknownHostException uknhoste) {
				log.error(uknhoste.toString());
				try {
					Thread.sleep(10000);
				} catch (InterruptedException e) {
					log.error(e.toString());
				}
			}
		}
		return ret;
	}

	private static String _getPublic() throws IOException {
		URL u = new URL("http://" + host + "/?d=" + Math.random());
		URLConnection uc = u.openConnection();
		HttpURLConnection huc = (HttpURLConnection) uc;
		huc.setDoInput(true);
		huc.setRequestProperty("Host", host);
		huc.setRequestProperty("user-agent", userAgent);
		InputStream is = huc.getInputStream();
		BufferedReader input = new BufferedReader(new InputStreamReader(is));
		String line = null;
		String ret = null;
		while ((line = input.readLine()) != null) {
			log.trace(line);
			Matcher m = IP.matcher(line);
			if (m.find()) {
				for (int i = 0; i < m.groupCount(); ++i) {
					log.info("parsed {}", m.group(i));
					if (ret == null)
						ret = m.group(i);
				}
			}
		}
		input.close();
		return ret;
	}
}

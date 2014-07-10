package homecam;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lti.civil.CaptureDeviceInfo;
import com.lti.civil.CaptureException;
import com.lti.civil.CaptureStream;
import com.lti.civil.CaptureSystem;
import com.lti.civil.CaptureSystemFactory;
import com.lti.civil.DefaultCaptureSystemFactorySingleton;
import com.lti.civil.VideoFormat;

public class CapturerServlet extends Servlet {
	static Logger log = LoggerFactory.getLogger(CapturerServlet.class);
	static String outputMime;
	CaptureSystem system;
	Map<String, Capturer> capturers;
	Set<String> excludedCaptureDevices;
	String preferredCaptureDevice;

	@SuppressWarnings("unchecked")
	public void init() throws Exception {
		RecorderServlet.recording = Boolean.parseBoolean(server.prop
				.getProperty("homecam.Capturer.recording"));
		RecorderServlet.maxRecord = Integer.parseInt(server.prop
				.getProperty("homecam.Capturer.maxRecord"));
		RecorderServlet.recordedDirectory = server.prop
				.getProperty("homecam.Capturer.recordedDirectory");
		RecorderServlet.houseKeepDay = Integer.parseInt(server.prop
				.getProperty("homecam.Capturer.houseKeepDay"));
		Capturer.capturerTimeout = Integer.parseInt(server.prop
				.getProperty("homecam.Capturer.capturerTimeout"));
		Capturer.capturerSoftTimeout = Integer.parseInt(server.prop
				.getProperty("homecam.Capturer.capturerSoftTimeout"));
		CapturerImage.outputFormat = server.prop
				.getProperty("homecam.Capturer.outputFormat");
		Capturer.resolutions = new LinkedList<Resolution>();
		String[] strRess = server.prop.getProperty(
				"homecam.Capturer.outputResolution").split(",");
		for (String strRes : strRess) {
			Capturer.resolutions.add(new Resolution(strRes));
		}
		outputMime = server.prop.getProperty("homecam.Capturer.outputMime");
		String[] excludedCaptureDevice = server.prop.getProperty(
				"homecam.Capturer.excludedCaptureDevice").split(",");
		excludedCaptureDevices = new HashSet<String>();
		for (String ecd : excludedCaptureDevice) {
			excludedCaptureDevices.add(ecd);
		}
		preferredCaptureDevice = server.prop
				.getProperty("homecam.Capturer.preferredCaptureDevice");

		CaptureSystemFactory factory = DefaultCaptureSystemFactorySingleton
				.instance();
		log.trace("factory {}", factory);
		system = factory.createCaptureSystem();
		log.trace("system {}", system);
		system.init();
		List<CaptureDeviceInfo> list = (List<CaptureDeviceInfo>) system
				.getCaptureDeviceInfoList();
		capturers = new LinkedHashMap<String, Capturer>(list.size() * 2);
		for (CaptureDeviceInfo info : list) {
			String c = info.getDeviceID();
			if (excludedCaptureDevices.contains(c)) {
				log.info("{} exclude {}", c, info.getDescription());
				continue;
			}
			log.info("{} +++++++ {}", c, info.getDescription());
			try {
				CaptureStream captureStream = system.openCaptureDeviceStream(c);
				Capturer cap = new Capturer(info, captureStream);
				capturers.put(c, cap);
			} catch (CaptureException e) {
				log.error(c, e);
			}

			if (StringUtils.isEmpty(preferredCaptureDevice))
				preferredCaptureDevice = info.getDeviceID();
		}
		if (capturers.get(preferredCaptureDevice) == null) {
			preferredCaptureDevice = capturers.keySet().iterator().next();
		} else if (!StringUtils.equals(preferredCaptureDevice, capturers
				.keySet().iterator().next())) {
			Map<String, Capturer> newlist = new LinkedHashMap<String, Capturer>(
					list.size() * 2);
			Capturer first = capturers.remove(preferredCaptureDevice);
			newlist.put(preferredCaptureDevice, first);
			newlist.putAll(capturers);
			capturers = newlist;
		}
	}

	Response listDevices() {
		Response ret;
		StringBuilder buf = new StringBuilder();
		for (Capturer cap : capturers.values()) {
			String value = StringEscapeUtils
					.escapeHtml4(cap.info.getDeviceID());
			String text = StringEscapeUtils.escapeHtml4(cap.info
					.getDescription());
			buf.append("<option value=\"" + value + "\">" + text
					+ "</option>\n");
		}
		ret = new Response(Server.HTTP_OK, Server.MIME_HTML, buf.toString());
		ret.markNoCache();
		return ret;
	}

	Response listFormats(Capturer cap) {
		Response ret;
		try {
			StringBuilder buf = new StringBuilder();
			List<VideoFormat> fmts = cap.captureStream.enumVideoFormats();
			for (VideoFormat fmt : fmts) {
				String sfmt = Capturer.getFormatString(fmt);
				log.info(sfmt);
				if (fmt.getWidth() > 0 && fmt.getHeight() > 0) {
					buf.append("<option value=\"" + sfmt + "\">" + sfmt
							+ "</option>\n");
				}
			}
			ret = new Response(Server.HTTP_OK, Server.MIME_HTML, buf.toString());
		} catch (CaptureException ce) {
			log.error(cap.toString(), ce);
			ret = new Response(Server.HTTP_INTERNALERROR,
					Server.MIME_PLAINTEXT, ce.getMessage());
		}
		ret.markNoCache();
		return ret;
	}

	Response listResolutions() {
		StringBuilder buf = new StringBuilder();
		for (Resolution res : Capturer.resolutions) {
			buf.append("<option value=\"" + res + "\">" + res + "</option>\n");
		}
		return new Response(Server.HTTP_OK, Server.MIME_HTML, buf.toString());
	}

	Response currentFormat(Capturer cap) {
		try {
			VideoFormat vfmt = cap.captureStream.getVideoFormat();
			return new Response(Server.HTTP_OK, Server.MIME_HTML, Capturer
					.getFormatString(vfmt));
		} catch (CaptureException ce) {
			log.error(cap.toString(), ce);
			return new Response(Server.HTTP_INTERNALERROR,
					Server.MIME_PLAINTEXT, ce.getMessage());
		}
	}

	public Response serve(Request request) {
		String c = request.parms.getProperty("c");
		if ("listDevices".equals(c)) {
			return listDevices();
		} else if ("listResolutions".equals(c)) {
			return listResolutions();
		}

		if (StringUtils.isEmpty(c))
			c = preferredCaptureDevice;
		Capturer cap = capturers.get(c);
		if (cap == null) {
			return new Response(Server.HTTP_INTERNALERROR,
					Server.MIME_PLAINTEXT, "Capturer " + c + " not found.");
		}

		String f = request.parms.getProperty("f");
		if ("listFormats".equals(f)) {
			return listFormats(cap);
		} else if ("currentFormat".equals(f)) {
			return currentFormat(cap);
		}

		byte[] bytes = cap.getBytes(f);
		InputStream ins = new ByteArrayInputStream(bytes);
		Response r = new Response(Server.HTTP_OK, outputMime, ins);
		r.addHeader("Content-Length", "" + bytes.length);
		r.markNoCache();
		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
		// RecorderServlet.record(bytes);
		return r;
	}

	public void finalize() {
		try {
			for (Capturer cap : capturers.values()) {
				cap.captureStream.dispose();
			}
			system.dispose();
			system = null;
		} catch (CaptureException e) {
			log.error("", e);
		}
	}
}

package homecam;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lti.civil.CaptureDeviceInfo;
import com.lti.civil.CaptureException;
import com.lti.civil.CaptureObserver;
import com.lti.civil.CaptureStream;
import com.lti.civil.Image;
import com.lti.civil.VideoFormat;

public class Capturer implements Runnable, CaptureObserver {
	static final Logger log = LoggerFactory.getLogger(Capturer.class);
	static int capturerTimeout = 10 * 60 * 1000;
	static int capturerSoftTimeout = 3 * 60 * 1000;
	static List<Resolution> resolutions;
	final CaptureDeviceInfo info;
	final CaptureStream captureStream;
	String currentFormat = null;
	Resolution resolution = null;
	CapturerImage img = new CapturerImage();
	boolean started = false;
	long lastAccess = -1;
	int maxRecord;
	boolean recording = false;

	public static String getFormatString(VideoFormat vfmt) {
		return vfmt.getWidth() + " x " + vfmt.getHeight() + " @"
				+ (int) vfmt.getFPS() + "fps";
	}

	public Capturer(CaptureDeviceInfo info, CaptureStream captureStream)
			throws CaptureException {
		this.info = info;
		this.captureStream = captureStream;
		log.trace("captureStream {} ", captureStream);
		VideoFormat vfmt = getVideoFormat(null);
		if (vfmt != null && captureStream.getVideoFormat() != vfmt) {
			log.info("using fmt: {} for {}", getFormatString(vfmt), info
					.getDeviceID());
			try {
				captureStream.setVideoFormat(vfmt);
			} catch (CaptureException e) {
				log.info("setting fmt... {}", e.getMessage());
			}
		}
		currentFormat = getFormatString(vfmt);
		captureStream.setObserver(this);
		// captureStream.start();
		// started = true;

		// thread is just to stop captureStream if nobody uses
		Thread t = new Thread(this, "CapturerServlet");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		t.start();
	}

	VideoFormat getVideoFormat(String sfmt) throws CaptureException {
		VideoFormat vfmt = captureStream.getVideoFormat();
		log.info("current fmt: {} for {}", getFormatString(vfmt), info
				.getDeviceID());
		List<VideoFormat> fmts = captureStream.enumVideoFormats();
		for (VideoFormat fmt : fmts) {
			log.info("available fmt: {}", getFormatString(fmt));
			if (sfmt == null
					&& vfmt.getWidth() * vfmt.getHeight() < fmt.getWidth()
							* fmt.getHeight()) {
				vfmt = fmt;
			} else if (sfmt != null && sfmt.equals(getFormatString(fmt))) {
				return vfmt;
			}
		}
		return vfmt;
	}

	public synchronized byte[] getBytes(String sfmt) {
		if (StringUtils.isEmpty(sfmt))
			sfmt = currentFormat;
		if (!currentFormat.equals(sfmt)) {
			try {
				VideoFormat fmt = getVideoFormat(sfmt);
				if (fmt != captureStream.getVideoFormat()) {
					if (started) {
						captureStream.stop();
						started = false;
					}
					captureStream.setVideoFormat(fmt);
					currentFormat = sfmt;
				}
			} catch (CaptureException e) {
				log.error(sfmt, e);
			}
		}
		if (!started) {
			log.debug("start {}", captureStream);
			try {
				captureStream.start();
				started = true;
				// captureStream need to be started, give way
				Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
				for (int i = 0; i < 50 && img.img == null; ++i) {
					log.trace(".");
					Thread.yield();
				}
			} catch (CaptureException e) {
				log.error("", e);
			}
		}
		lastAccess = System.currentTimeMillis();
		return img.getBytes();
	}

	// used by lti-civil
	public void onNewImage(CaptureStream caps, Image image) {
		if (System.currentTimeMillis() > lastAccess + capturerSoftTimeout) {
			return;
		}
		try {
			img = new CapturerImage(image);
			RecorderServlet.record(img.getBytes());
		} catch (Exception e) {
			log.error(e.getLocalizedMessage(), e);
		}
	}

	public void onError(CaptureStream caps, CaptureException capex) {
		log.error(info.getDeviceID(), capex);
	}

	// just for check to captureStream.stop
	public void run() {
		while (true) {
			try {
				if (!started)
					Thread.sleep(capturerTimeout * 2);
				else {
					Thread.sleep(capturerTimeout);
					// check to see if anybody using
					if (started
							&& System.currentTimeMillis() > lastAccess
									+ capturerTimeout) {
						try {
							log.warn("stop / {}", Utils.getSimpleFormat()
									.format(new Date(lastAccess)));
							captureStream.stop();
						} catch (CaptureException e) {
							log.error(info.getDeviceID(), e);
						}
						started = false;
					}
				}
			} catch (InterruptedException e) {
				log.error(e.getLocalizedMessage(), e);
			}
		}
	}

	public void finalize() {
		try {
			captureStream.stop();
			started = false;
		} catch (CaptureException e) {
			log.error(info.getDeviceID(), e);
		}
	}
}

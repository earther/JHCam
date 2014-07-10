package homecam;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecorderServlet extends Servlet {
	public static final Logger log = LoggerFactory
			.getLogger(RecorderServlet.class);
	public static final String zipDirDateFormat = "yyyy.MM.dd";
	public static final String zipFileDateFormat = Utils.simpleDateFormat;
	public static String recordedDirectory = "recorded/";
	public static boolean recording = true;
	public static int maxRecord;
	public static boolean houseKeeped = false;
	public static int houseKeepDay = 15;

	public static void logMemory() {
		Runtime r = Runtime.getRuntime();
		log.info("total {}; max {}; free {}", new Object[] { r.totalMemory(),
				r.maxMemory(), r.freeMemory() });
	}

	public static void record(byte[] image) {
		if (!recording)
			return;
		try {
			_record(image);
			if (!houseKeeped) {
				houseKeeped = true;
				HouseKeeper housek = new HouseKeeper(null,
						new String[] { ".jpg" });
				housek.setDirName(recordedDirectory);
				housek.setTimeoutDay(houseKeepDay);
				housek.setSubDirectoryLevel(1);
				housek.setPurgeSubDirectory(true);
				housek.start();
			}
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
	}

	public static void _record(byte[] image) throws Exception {
		Date sysdate = new Date();
		File dir = getDirectory(sysdate);
		ensureCapacity(dir);
		SimpleDateFormat format = new SimpleDateFormat(zipFileDateFormat);
		String filename = format.format(sysdate) + ".jpg";
		File file = new File(dir, filename);
		OutputStream os = new FileOutputStream(file);
		os.write(image);
		os.close();
	}

	public static String start() {
		if (recording)
			return "Already started.";
		recording = true;
		return "Started recording.";
	}

	public static String stop() throws Exception {
		if (!recording)
			return "Not started.";
		recording = false;
		File dir = getDirectory(new Date());
		int rec = dir.listFiles().length;
		return "Stopped. " + rec + " snaps recorded.";
	}

	public static void ensureCapacity(File dir) {
		File[] files = dir.listFiles();
		if (files == null || files.length < maxRecord)
			return;
		Arrays.sort(files);
		files[0].delete();
	}

	public static File getDirectory(Date sysdate) throws Exception {
		String dirname = recordedDirectory
				+ new SimpleDateFormat(zipDirDateFormat).format(sysdate) + '/';
		File dir = new File(dirname);
		dir.mkdirs();
		return dir;
	}

	public Response download(Request request) throws Exception {
		final File dir = getDirectory(new Date());
		final File[] files = dir.listFiles();
		if (files == null || files.length == 0) {
			return new Response(Server.HTTP_BADREQUEST, Server.MIME_PLAINTEXT,
					"Nothing recorded.");
		}

		final String filename = dir.getName() + '-' + files.length + ".zip";
		Response r = new Response(Server.HTTP_OK, "application/zip");
		r.addHeader("Content-Disposition", "attachment; filename=" + filename);
		r.outputStreamHandler = new OutputStreamHandler() {
			public void output(Socket socket, OutputStream output) {
				try {
					Utils.zipFile(files, output);
				} catch (IOException ioe) {
					log.error(filename, ioe);
				}
			}
		};
		return r;
	}

	static class Snap {
		long sysdate;
		byte[] image;

		public Snap(long sysdate, byte[] image) {
			this.sysdate = sysdate;
			this.image = image;
		}
	}

	public Response serve(Request request) {
		if (!request.method.equals("POST")) {
			return server.serveFile("/index.html", request.header,
					server.myRootDir, true);
		}
		String c = request.parms.getProperty("command");
		log.info(c);
		logMemory();
		try {
			if ("record-start".equals(c)) {
				return new Response(Server.HTTP_OK, Server.MIME_PLAINTEXT,
						start());
			} else if ("record-stop".equals(c)) {
				return new Response(Server.HTTP_OK, Server.MIME_PLAINTEXT,
						stop());
			} else if ("record-download".equals(c)) {
				return download(request);
			}
		} catch (Exception e) {
			return new Response(Server.HTTP_INTERNALERROR,
					Server.MIME_PLAINTEXT, e.getLocalizedMessage());
		}
		return new Response(Server.HTTP_BADREQUEST, Server.MIME_PLAINTEXT, c);
	}
}

package homecam;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.slf4j.Logger;

public class Utils {
	public static final String simpleDateFormat = "yyyy.MM.dd-HH.mm.ss.SSS";
	static final String HEX = "1234567890abcdef";

	public static SimpleDateFormat getSimpleFormat() {
		return new SimpleDateFormat(simpleDateFormat);
	}

	public static String random(int len) {
		StringBuilder d = new StringBuilder(len);
		for (int i = 0; i < len; ++i) {
			int r = (int) Math.floor(Math.random() * HEX.length());
			d.append(HEX.charAt(r));
		}
		return d.toString();
	}

	public static String reverse(String src) {
		int n = src.length();
		char[] d = new char[n];
		for (int i = 0; i < n; ++i) {
			d[i] = src.charAt(n - i - 1);
		}
		return new String(d);
	}

	public static void appendHexChar(StringBuilder s, String src) {
		int n = src.length();
		for (int i = 0; i < n; ++i) {
			char c = src.charAt(i);
			if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) {
				s.append(c);
			} else if (c >= 'A' && c <= 'F') {
				s.append((char) ((int) c + 32));
			}
		}
	}

	public static String getHexChar(String src) {
		StringBuilder s = new StringBuilder(src.length());
		appendHexChar(s, src);
		return s.toString();
	}

	public static void shuffle(char[] s) {
		for (int i = 0; i < s.length; ++i) {
			int r1 = (int) Math.floor(Math.random() * s.length);
			int r2 = (int) Math.floor(Math.random() * s.length);
			char t = s[r1];
			s[r1] = s[r2];
			s[r2] = t;
		}
	}

	public static String shuffle(String src) {
		char[] s = src.toCharArray();
		shuffle(s);
		return new String(s);
	}

	public static String getSize(long len) {
		String msg = "";
		if (len < 1024)
			msg += len + " bytes";
		else if (len < 1024 * 1024) {
			long dec = (len % 1024 / 10 % 100);
			msg += len / 1024 + "." + dec + (dec < 10 ? "0" : "") + " KB";
		} else {
			long dec = len % (1024 * 1024) / 10 % 100;
			msg += len / (1024 * 1024) + "." + dec + (dec < 10 ? "0" : "")
					+ " MB";
		}
		return msg;
	}

	public static boolean matches(String ptn, String src) {
		if (src.equals(ptn))
			return true;
		else if (ptn.charAt(0) == '#') {
			return Pattern.matches(ptn.substring(1), src);
		} else if (ptn.charAt(0) == '*' && ptn.charAt(ptn.length() - 1) == '*') {
			ptn = ptn.substring(1, ptn.length() - 1);
			return src.contains(ptn);
		} else if (ptn.charAt(0) == '*') {
			ptn = ptn.substring(1);
			return src.endsWith(ptn);
		} else if (ptn.charAt(ptn.length() - 1) == '*') {
			ptn = ptn.substring(0, ptn.length() - 1);
			return src.startsWith(ptn);
		} else if (ptn.indexOf('*') > 0) {
			String part = ptn.substring(0, ptn.indexOf('*'));
			if (src.startsWith(part)) {
				part = ptn.substring(ptn.indexOf('*') + 1);
				return src.endsWith(part);
			}
			return false;
		}
		return false;
	}

	public static String join(Object[] ary) {
		if (ary == null || ary.length == 0)
			return null;
		StringBuilder s = new StringBuilder();
		for (Object a : ary) {
			s.append(a);
			s.append(',');
		}
		if (s.length() < 1)
			return "";
		return s.substring(0, s.length() - 1);
	}

	public static void logMap(Logger log, Map<?, ?> map) {
		Object[] propNames = (Object[]) map.keySet().toArray();
		Arrays.sort(propNames);
		for (Object pn : propNames) {
			log.info(" + {}={}", pn, map.get(pn));
		}
	}

	public static void main(String[] args) {
		System.out.println(matches("1234", "#\\d{2,3}"));
	}

	public static final int MIN_BUFFER_SIZE = 4 * 1024;
	public static final int MAX_BUFFER_SIZE = 1024 * 1024;

	public static int getBufferSize(int bufferSize) {
		bufferSize = Math.max(bufferSize, MIN_BUFFER_SIZE);
		bufferSize = Math.min(bufferSize, MAX_BUFFER_SIZE);
		return bufferSize;
	}

	public static long copyStream(InputStream input, OutputStream output,
			int bufferSize) throws IOException {
		bufferSize = getBufferSize(bufferSize);
		byte[] buffer = new byte[bufferSize];
		long count = 0;
		int n = 0;
		while (-1 != (n = input.read(buffer))) {
			output.write(buffer, 0, n);
			count += n;
		}
		return count;
	}

	public static long copy(File input, OutputStream output) throws IOException {
		InputStream is = new FileInputStream(input);
		long ret = copyStream(is, output, (int) input.length());
		is.close();
		return ret;
	}

	public static long zipFile(File[] files, File zipfile) throws IOException {
		return zipFile(files, new FileOutputStream(zipfile));
	}

	public static long zipFile(File[] files, OutputStream output)
			throws IOException {
		long ret = 0;
		ZipOutputStream zos = new ZipOutputStream(output);
		for (File f : files) {
			ZipEntry e = new ZipEntry(f.getName());
			e.setTime(f.lastModified());
			zos.putNextEntry(e);
			ret += copy(f, zos);
		}
		zos.close();
		return ret;
	}
}

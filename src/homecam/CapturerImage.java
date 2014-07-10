package homecam;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextLayout;
import java.awt.image.BufferedImage;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lti.civil.Image;
import com.lti.civil.awt.AWTImageConverter;

public class CapturerImage {
	static Logger log = LoggerFactory.getLogger(CapturerImage.class);
	static String outputFormat;
	final BufferedImage img;
	final private byte[] bytes;

	public static byte[] getEmptyBytes() {
		try {
			// log.warn("null image @{}", captureStream);
			File file = new File("default.jpg");
			int s, len = (int) file.length();
			InputStream is = new FileInputStream(file);
			ByteArrayOutputStream bos = new ByteArrayOutputStream(len);
			byte[] buf = new byte[len];
			while ((s = is.read(buf, 0, len)) != -1) {
				bos.write(buf, 0, s);
			}
			is.close();
			RecorderServlet.record(buf); // debug
			return buf;
		} catch (Exception e) {
			return new byte[] { 0 };
		}
	}

	public static byte[] getBytes(RenderedImage img) {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			ImageIO.write(img, outputFormat, os);
		} catch (Exception e) {
			log.error(e.getLocalizedMessage(), e);
		}
		byte[] compressed = os.toByteArray();
		return compressed;
	}

	public CapturerImage() {
		img = null;
		bytes = getEmptyBytes();
	}

	public CapturerImage(Image image) {
		img = AWTImageConverter.toBufferedImage(image);
		try {
			makeWaterMark(img);
		} catch (Exception e) {
			log.error("", e);
		}
		bytes = getBytes(img);
	};

	public void makeWaterMark(BufferedImage image) {
		SimpleDateFormat format = new SimpleDateFormat("MM/dd HH:mm:ss.SSS");
		String mark = format.format(new Date());
		format = null;

		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
				RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
				RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		g.setFont(new Font("Courier New", Font.BOLD, 20));
		g.setComposite(AlphaComposite
				.getInstance(AlphaComposite.SRC_OVER, 0.8f));
		g.setPaint(Color.WHITE);
		// g.setPaint(new GradientPaint(0, 0, Color.BLACK, 30, 20, Color.YELLOW,
		// true));
		TextLayout tl = new TextLayout(mark, g.getFont(), g
				.getFontRenderContext());
		// Rectangle2D bounds = tl.getBounds();
		double x = 10; // (image.getWidth() - bounds.getWidth()) / 2 -
		// bounds.getX();
		double y = 25; // (image.getHeight() - bounds.getHeight()) / 2 -
		// bounds.getY();
		tl.draw(g, (float) x, (float) y);
		g.dispose();
	}

	public byte[] getBytes() {
		return bytes;
	}
}

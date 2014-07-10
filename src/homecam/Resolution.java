package homecam;

public class Resolution {
	int width;
	int height;

	public Resolution(String strRes) {
		int pos = strRes.indexOf('x');
		width = Integer.parseInt(strRes.substring(0, pos));
		height = Integer.parseInt(strRes.substring(pos + 1));
	}

	public String toString() {
		return "" + width + 'x' + height;
	}
}

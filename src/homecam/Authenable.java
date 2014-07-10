package homecam;

public interface Authenable {
	public void authen(Request request) throws InterruptedException;

	public void logout(Request request);
}

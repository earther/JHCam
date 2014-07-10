package homecam;

import java.io.OutputStream;
import java.net.Socket;

public interface OutputStreamHandler {
	public void output(Socket socket, OutputStream output);
}

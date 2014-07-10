package homecam;

import java.io.IOException;

public class Latester {
	public static void main(String[] args) throws IOException {
		String salt = Utils.random(64);
		for (int i = 0; i < 900; ++i) {
			String nonce = Utils.random((int) Math.floor(Math.random() * 128));
			System.out.println(nonce);
			int n = nonce.length();
			if (n > 64)
				nonce = nonce.substring(n - 64);
			else if (n < 64)
				nonce = nonce + salt.substring(n);
			System.out.println(nonce);
			System.out.println("");
		}
		System.out.println(salt);
	}
}

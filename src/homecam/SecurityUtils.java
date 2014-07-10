package homecam;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecurityUtils {
	public static final Logger log = LoggerFactory
			.getLogger(SecurityUtils.class);
	public static String ALGORITHM = "AES/CBC/PKCS5Padding";

	public static String aesDecrypt(String sKey, String sBase64Src) {
		try {
			sKey = length16(sKey);
			SecretKeySpec skeySpec = new SecretKeySpec(sKey.getBytes(), "AES");
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.DECRYPT_MODE, skeySpec,
					new IvParameterSpec(sKey.getBytes()));

			byte[] ptext = cipher.doFinal(Base64.decodeBase64(sBase64Src));

			return new String(ptext, "UTF8");
		} catch (Exception e) {
			log.error("", e);
		}
		return null;
	}

	public static String aesEncrypt(String sSrc, String sKey) {
		try {
			sKey = length16(sKey);
			SecretKeySpec skeySpec = new SecretKeySpec(sKey.getBytes(), "AES");
			Cipher cipher = Cipher.getInstance(ALGORITHM);
			cipher.init(Cipher.ENCRYPT_MODE, skeySpec,
					new IvParameterSpec(sKey.getBytes()));

			return Base64.encodeBase64String(cipher.doFinal(sSrc.getBytes()));
		} catch (Exception e) {
			log.error("", e);
		}
		return null;
	}

	static String length16(String key) {
		if (key.length() == 16)
			return key;
		if (key.length() > 16)
			return key.substring(0, 16);
		key += "0123456789abcdef";
		return key.substring(0, 16);
	}

	public static void main(String[] args) {
		String key = "makediffer@gmail.com";
		String src = "2+2=4 true freedom.";

		String tgr = aesEncrypt(src, key);
		System.out.println(tgr);

		System.out.println(aesDecrypt(key, tgr));
	}
}

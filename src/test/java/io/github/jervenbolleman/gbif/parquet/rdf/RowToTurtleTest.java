package io.github.jervenbolleman.gbif.parquet.rdf;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class RowToTurtleTest {

	@Test
	void digest() throws NoSuchAlgorithmException {
		MessageDigest instance = MessageDigest.getInstance("SHA-256");
		byte[] toD = "test".getBytes(UTF_8);
		byte[] d = RowToTurtle.digest(instance, toD);
		instance.update(toD);
		String fh = HexFormat.of().formatHex(instance.digest());
		String string = "nl:" + fh;
		assertEquals(new String(d, UTF_8), string);
	}

	@Test
	void escape() {
		String escape = RowToTurtle.escape("\\");
		assertEquals("\\\\", escape);
		String escape2 = RowToTurtle.escape("somehwatlonger\\");
		assertEquals("somehwatlonger\\\\", escape2);
		String escape3 = RowToTurtle.escape("somehwatlonger\\andpost");
		assertEquals("somehwatlonger\\\\andpost", escape3);
	}
}

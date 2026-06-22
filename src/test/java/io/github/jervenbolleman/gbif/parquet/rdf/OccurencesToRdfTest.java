package io.github.jervenbolleman.gbif.parquet.rdf;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OccurencesToRdfTest {

	@TempDir
	Path tmp;

	@Test
	void test() throws IOException {
		OccurencesToRdf otr = new OccurencesToRdf();
		Path tp = tmp.resolve("test.parquet");
		try (InputStream is = OccurencesToRdf.class.getClassLoader().getResourceAsStream("test.parquet");
				OutputStream out = Files.newOutputStream(tp, StandardOpenOption.CREATE_NEW)) {
			is.transferTo(out);
		}
		String ttl; 
		try (ByteArrayOutputStream fos = new ByteArrayOutputStream()) {
			int convertFile = otr.convertFile(tp, Instant.now(), fos);
			assertEquals(0, convertFile);
			fos.close();
			ttl = fos.toString(StandardCharsets.UTF_8);
		}
		assertTrue(ttl.contains("gbifocc:1920853226"));
	}

}

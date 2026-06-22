package io.github.jervenbolleman.gbif.parquet.rdf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RowToTurtleTest {

	

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

package io.github.jervenbolleman.gbif.parquet.rdf;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

record Digester(MessageDigest md) {
	Digester() throws NoSuchAlgorithmException {
		this(MessageDigest.getInstance("SHA-256"));
	}

	byte[] digest(byte[] loc) {
		AddDigest out = new AddDigest();
		HexFormat.of().formatHex(out, md.digest(loc));
		md.reset();
		return out.content;
	}

	private static class AddDigest implements Appendable {
		private final byte[] content;
		private int at = 3;

		public AddDigest() {
			content = new byte[67];
			content[0] = 'n';
			content[1] = 'l';
			content[2] = ':';
		}

		@Override
		public Appendable append(CharSequence csq) throws IOException {
			for (int i = 0; i < csq.length(); i++) {
				append(csq.charAt(i));
			}
			return this;
		}

		@Override
		public Appendable append(CharSequence csq, int start, int end) throws IOException {
			for (int i = start; i < end; i++) {
				append(csq.charAt(i));
			}
			return this;
		}

		@Override
		public Appendable append(char c) throws IOException {
			content[at++] = (byte) c;
			return this;
		}

	}
}

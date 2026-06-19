package io.github.jervenbolleman.gbif.parquet.rdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.util.ArrayList;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

public class AwsOpenDataLocationsTest {
	private String xml = """
					<?xml version="1.0" encoding="UTF-8"?>
					<ListBucketResult xmlns="http://s3.amazonaws.com/doc/2006-03-01/"><Name>gbif-open-data-af-south-1</Name><Prefix>occurrence/2025-06-01/</Prefix><KeyCount>2</KeyCount><MaxKeys>1000</MaxKeys><Delimiter>/</Delimiter><IsTruncated>false</IsTruncated><Contents><Key>occurrence/2025-06-01/citation.txt</Key><LastModified>2025-06-02T07:37:01.000Z</LastModified><ETag>&quot;63672849a4c5d6bf55e0711ac251a91d&quot;</ETag><ChecksumAlgorithm>CRC64NVME</ChecksumAlgorithm><ChecksumType>FULL_OBJECT</ChecksumType><Size>83</Size><StorageClass>STANDARD</StorageClass></Contents><CommonPrefixes><Prefix>occurrence/2025-06-01/occurrence.parquet/</Prefix></CommonPrefixes></ListBucketResult>
					""";

	@Test
	void xmlParse() throws ParserConfigurationException, SAXException, IOException {
		ArrayList<String> list = new ArrayList<String>();
		assertNull(AwsOpenDataLocations.parseDocument(xml, list));
		assertEquals(1, list.size());
		assertEquals("occurrence/2025-06-01/citation.txt", list.get(0));
	}

}

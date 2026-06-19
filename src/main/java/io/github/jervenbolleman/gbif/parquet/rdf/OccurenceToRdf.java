package io.github.jervenbolleman.gbif.parquet.rdf;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.PrimitiveIterator.OfInt;
import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import dev.hardwood.InputFile;
import dev.hardwood.reader.ParquetFileReader;
import dev.hardwood.reader.RowReader;
import dev.hardwood.schema.ColumnProjection;
import dev.hardwood.schema.ColumnSchema;
import dev.hardwood.schema.FileSchema;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "occurence-to-rdf", description = "Convert GBIF occurrence parquet files to RDF")
public class OccurenceToRdf implements Callable<Integer> {

	
	private static final byte[] PREFIXES = """
				PREFIX gbifid:<https://www.gbif.org/occurrence/>
			PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
			PREFIX gbif: <https://www.gbif.org/occurrence/>
			PREFIX gbifterm: <http://rs.gbif.org/terms/1.0/>
			PREFIX gbifds: <https://www.gbif.org/dataset/>
			PREFIX gbifsp: <https://www.gbif.org/species/>
			PREFIX gbifpub: <https://www.gbif.org/publisher/>
			PREFIX dwc: <http://rs.tdwg.org/dwc/terms/>
			PREFIX dwciri:<http://rs.tdwg.org/dwc/iri/>
			PREFIX geo: <http://www.opengis.net/ont/geosparql#>
			PREFIX wdt: <http://www.wikidata.org/prop/direct/>
			PREFIX nl: <https://www.example.org/namedLocation/>
			PREFIX ccby4: <https://creativecommons.org/licenses/by/4.0/>
			PREFIX ccby4nc: <https://creativecommons.org/licenses/by-nc/4.0/>
			PREFIX cc0: <https://creativecommons.org/publicdomain/zero/1.0/>
			PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
				""".getBytes(UTF_8);

	private static final Logger log = Logger.getLogger(OccurenceToRdf.class.getName());
	
	@Parameters(index = "0", description = "The bucket or directory containing the parquet files to convert")
	public String bucketOrDirectory;

	@Parameters(index = "1", description = "Where to writeto")
	public File output;

	public static void main(String[] args) {
		int exitCode = new CommandLine(new OccurenceToRdf()).execute(args);
		System.exit(exitCode);
	}

	@Override
	public Integer call() throws Exception { // your business logic goes here...
		if (bucketOrDirectory == null || bucketOrDirectory.isEmpty()) {
			log.log(Level.SEVERE, "Please provide a bucket or directory containing the parquet files to convert");
			return 1;
		}
		if (bucketOrDirectory.startsWith("s3://")) {
			log.log(Level.INFO, "Using S3 bucket: " + bucketOrDirectory);
		} else {
			return convertFiles();
		}

		return 0;
	}

	private int convertFiles() {
		try (Stream<Path> list = Files.list(Path.of(bucketOrDirectory));
				OutputStream fos = new FileOutputStream(output)) {
			printPrefixes(fos);

			Instant start = Instant.now();
			OfInt iter = list.mapToInt((f) -> convertFile(f, start, fos)).iterator();
			while (iter.hasNext()) {
				int result = iter.nextInt();
				if (result != 0) {
					return result;
				}
			}
			return 0;
		} catch (IOException e) {
			return 1;
		}
	}

	private int convertFile(Path path1, Instant start, OutputStream fos) {
		Map<KnownColumns, Integer> knownColumnsMap = new EnumMap<>(KnownColumns.class);
		try {
			while (Files.isSymbolicLink(path1)) {
				path1 = Files.readSymbolicLink(path1);
			}
		} catch (IOException e) {
			log.log(Level.SEVERE, "Error resolving symbolic link: " + path1, e);
			return 4;
		}
		
		try (ParquetFileReader reader = ParquetFileReader.open(InputFile.of(path1))) {
			Instant startFile = Instant.now();
			FileSchema schema = reader.getFileSchema();
			mapKnownColumnsToIds(knownColumnsMap, schema);
			RowReader rows = reader.buildRowReader()
					.projection(ColumnProjection.columns(
							Arrays.stream(KnownColumns.values()).map(KnownColumns::columnName).toArray(String[]::new)))
					.build();

			RowToTurtle.convertRows(rows, knownColumnsMap, fos);
			logTime(path1, start, startFile);
		} catch (IOException e) {
			log.log(Level.SEVERE, "Error reading file: " + path1, e);
			return 2;
		} catch (NoSuchAlgorithmException e) {
			log.log(Level.SEVERE, "NoSuchAlgorithmException: " + path1, e);
			return 3;
		}
		return 0;
	}

	private void logTime(Path path1, Instant start, Instant startFile) {
		Instant end = Instant.now();
		Duration forFile = Duration.between(startFile, end);
		Duration forAll = Duration.between(start, end);
		System.err.println("Converted " + path1 + " in " + forFile + " total " + forAll);
	}

	private void printPrefixes(OutputStream os) throws IOException {

		os.write(PREFIXES);
	}


	private void mapKnownColumnsToIds(Map<KnownColumns, Integer> knownColumnsMap, FileSchema schema) {
		for (int i = 0; i < schema.getColumnCount(); i++) {
			ColumnSchema column = schema.getColumn(i);
			KnownColumns kc = KnownColumns.fromColumnName(column.name());
			if (kc != null) {
				knownColumnsMap.put(kc, i);
			}
		}
	}
}

package io.github.jervenbolleman.gbif.parquet.rdf;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
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
import dev.hardwood.s3.S3Source;
import dev.hardwood.schema.ColumnProjection;
import dev.hardwood.schema.ColumnSchema;
import dev.hardwood.schema.FileSchema;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
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

	@Option(names = { "--year" }, description = "Year", required = true)
	public String year;

	@Option(names = { "--month" }, description = "Month", required = true)
	public String month;

	@Option(names = { "--output", "-o" }, description = "Where to write to")
	public File output = new File("/dev/stdout");

	@Option(names = { "-d", "--aws" }, description = "Retrieve read parquet files from AWS S3", defaultValue = "false")
	public boolean useS3 = false;

	public static void main(String[] args) {
		int exitCode = new CommandLine(new OccurenceToRdf()).execute(args);
		System.exit(exitCode);
	}

	@Override
	public Integer call() throws Exception { // your business logic goes here...
		if (year == null || year.isEmpty() || Integer.parseInt(year) < 2000 || Integer.parseInt(year) > 2100) {
			log.log(Level.SEVERE, "Year value is missing or invalid");
			return 1;
		}
		if (month == null || month.isEmpty() || Integer.parseInt(month) < 0 || Integer.parseInt(month) > 12) {
			log.log(Level.SEVERE, "Month value is missing or invalid");
			return 1;
		}
		if (useS3) {
			AwsOpenDataLocations closestS3Location = findClosestS3Location();
			log.log(Level.INFO, "Closest S3 location: " + closestS3Location.getLocation());
			log.log(Level.SEVERE, "Using S3 bucket: but that is not implemented yet");
			
			return -1;
		} else {
			try (Stream<Path> list = Files.list(Path.of("./" + year + "/" + month))) {
				return convertFiles(list);
			} catch (IOException e) {
				return 1;
			}
		}
	}

	private AwsOpenDataLocations findClosestS3Location() throws URISyntaxException, IOException, InterruptedException {
		AwsOpenDataLocations closestLocation = AwsOpenDataLocations.US_EAST_1; // default to US_EAST_1 if all fail
		Map<AwsOpenDataLocations, Long> timeToFetch = new EnumMap<>(AwsOpenDataLocations.class);
		try (HttpClient client = HttpClient.newBuilder().build()) {

			for (AwsOpenDataLocations location : AwsOpenDataLocations.values()) {
				Instant start = Instant.now();
				HttpRequest hr = HttpRequest.newBuilder(new URI(location.asHttpPrefix() + year + "/" + month + "/"))
						.HEAD().build();
				HttpResponse<Void> send = client.send(hr, BodyHandlers.discarding());
				Instant end = Instant.now();
				if (send.statusCode() == 200) {
					timeToFetch.put(location, Duration.between(start, end).getSeconds());
				}
			}
		}
		long fastestTime = Long.MAX_VALUE;
		for (Map.Entry<AwsOpenDataLocations, Long> entry : timeToFetch.entrySet()) {
			if (entry.getValue() < fastestTime) {
				fastestTime = entry.getValue();
				closestLocation = entry.getKey();
			}
		}
		return closestLocation;
	}

	private int convertFiles(Stream<Path> list) {
		try (OutputStream fos = new FileOutputStream(output)) {
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

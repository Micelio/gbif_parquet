package io.github.jervenbolleman.gbif.parquet.rdf;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
	private static final int BUFFER_SIZE = 16 * 8096;
	private static final byte[] endLiteralAndTripleBlock = "\" . \n".getBytes(UTF_8);
	private static final Logger log = Logger.getLogger(OccurenceToRdf.class.getName());
	@Parameters(index = "0", description = "The bucket or directory containing the parquet files to convert")
	public String bucketOrDirectory;

	@Parameters(index = "1", description = "Where to writeto")
	public File output;

	private static final byte[] gbifPrefix = "gbif:".getBytes(UTF_8);
	private static final byte[] isOccurence = " a dwc:Occurence ;\n gbifterm:gbifID \"".getBytes(UTF_8);
	private static final byte[] occurrenceStatus = "\";\n dwc:occurrenceStatus \"".getBytes(UTF_8);
	private static final byte[] individualCount = "\"\n;dwc:individualCount ".getBytes(UTF_8);

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
			while(iter.hasNext()) {
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

		try (ParquetFileReader reader = ParquetFileReader.open(InputFile.of(path1))) {
			Instant startFile = Instant.now();
			FileSchema schema = reader.getFileSchema();
			mapKnownColumnsToIds(knownColumnsMap, schema);
			RowReader rows = reader.buildRowReader()
					.projection(ColumnProjection.columns(
							Arrays.stream(KnownColumns.values()).map(KnownColumns::columnName).toArray(String[]::new)))
					.build();

			int gbifColumnId = knownColumnsMap.get(KnownColumns.gbifid);
			int occurenceStatusId = knownColumnsMap.get(KnownColumns.occurrencestatus);
			convert(rows, gbifColumnId, occurenceStatusId, fos);
			logTime(path1, start, startFile);
		} catch (IOException e) {
			log.log(Level.SEVERE, "Error reading file: " + path1, e);
			return 2;
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

		byte[] prefixes = """
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
				PREFIX tt: <https://www.example.org/namedTaxonomicClaim/>
				PREFIX lc: <https://www.example.org/namedLocation/>

					""".getBytes(UTF_8);
		os.write(prefixes);
	}

	private void convert(RowReader rows, Integer gbifColumnId, int occurenceStatusId, OutputStream fos) throws IOException {
		byte[] buffer = new byte[BUFFER_SIZE];
		int bufferUse = 0;
		while (rows.hasNext()) {
			rows.next();
			bufferUse = add(buffer, gbifPrefix, fos, bufferUse );
			byte[] gbifid = rows.getString(gbifColumnId).getBytes(UTF_8);
			bufferUse = add(buffer, gbifid, fos, bufferUse );
			bufferUse = add(buffer, isOccurence, fos, bufferUse );
			bufferUse = add(buffer, gbifid, fos, bufferUse );
			bufferUse = add(buffer, occurrenceStatus, fos, bufferUse );
			byte[] occurencestatus = rows.getString(occurenceStatusId).getBytes(UTF_8);
			bufferUse = add(buffer, occurencestatus, fos, bufferUse);
			bufferUse = add(buffer, endLiteralAndTripleBlock, fos, bufferUse);
		}
		fos.write(buffer, 0, bufferUse);
	}

	private int add(byte[] buffer, byte[] toAdd, OutputStream fos, int bufferUse) throws IOException {
		int nextEnd = toAdd.length + bufferUse;
		if (nextEnd > buffer.length) {
			fos.write(buffer, 0, bufferUse);
			System.arraycopy(toAdd, 0, buffer, 0, toAdd.length);
			return toAdd.length;
		} else {
			System.arraycopy(toAdd, 0, buffer, bufferUse, toAdd.length);
			return nextEnd;
		}
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

	private enum KnownColumns {
		gbifid("gbifid"), datasetkey("datasetkey"), occurrenceid("occurrenceid"), kingdom("kingdom"), phylum("phylum"),
		clazz("class"), order("order"), family("family"), genus("genus"), species("species"),
		infraspecificepithet("infraspecificepithet"), taxonrank("taxonrank"), scientificname("scientificname"),
		verbatimscientificname("verbatimscientificname"),
		verbatimscientificnameauthorship("verbatimscientificnameauthorship"), countrycode("countrycode"),
		locality("locality"), stateprovince("stateprovince"), occurrencestatus("occurrencestatus"),
		individualcount("individualcount"), publishingorgkey("publishingorgkey"), decimallongitude("decimallongitude"),
		decimallatitude("decimallatitude"), coordinateuncertaintyinmeters("coordinateuncertaintyinmeters"),
		coordinateprecision("coordinateprecision"), elevation("elevation"), elevationaccuracy("elevationaccuracy"),
		depth("depth"), depthaccuracy("depthaccuracy"), eventdate("eventdate"), day("day"), month("month"),
		year("year"), taxonkey("taxonkey"), specieskey("specieskey"), basisofrecord("basisofrecord"),
		institutioncode("institutioncode"), collectioncode("collectioncode"), catalognumber("catalognumber"),
		recordnumber("recordnumber"), identifiedby("identifiedby"), dateidentified("dateidentified"),
		license("license"), rightsholder("rightsholder"), recordedby("recordedby"), typestatus("typestatus"),
		establishmentmeans("establishmentmeans"), lastinterpreted("lastinterpreted"), mediatype("mediatype"),
		issue("issue");

		private final String columnName;

		KnownColumns(String columnName) {
			this.columnName = columnName;
		}

		public static KnownColumns fromColumnName(String columnName) {
			for (KnownColumns knownColumn : KnownColumns.values()) {
				if (knownColumn.columnName.equalsIgnoreCase(columnName)) {
					return knownColumn;
				}
			}
			return null;
		}

		public String columnName() {
			return columnName;
		}
	}
}

package io.github.jervenbolleman.gbif.parquet.rdf;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
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
		PREFIX tt: <https://www.example.org/namedTaxonomicClaim/>
		PREFIX lc: <https://www.example.org/namedLocation/>

			""".getBytes(UTF_8);
	
	private static final int BUFFER_SIZE = 16 * 8096;
	private static final byte[] countryCode = "\n; dwc:countryCode \"".getBytes(UTF_8);
	private static final byte[] closeLiteral = "\" ".getBytes(UTF_8);
	private static final byte[] closeDoubleLiteral = "\"^^xsd:double ".getBytes(UTF_8);
	private static final byte[] gbifid = "gbif:".getBytes(UTF_8);
	private static final byte[] isOccurence = " a dwc:Occurence ;\n gbifterm:gbifID \"".getBytes(UTF_8);
	private static final byte[] occurrenceStatus = "\n; dwc:occurrenceStatus \"".getBytes(UTF_8);
	private static final byte[] individualCount = "\n; dwc:individualCount ".getBytes(UTF_8);
	private static final byte[] publishingOrgKey = "\n; dwc:publishingOrgKey gbifpub:".getBytes(UTF_8);
	private static final byte[] decimalLatitude ="\n; dwc:decimalLatitude \"".getBytes(UTF_8);
	private static final byte[] decimalLongitude = "\n; dwc:decimalLongitude \"".getBytes(UTF_8);
	private static final byte[] coordinateUncertaintyInMeters = "\n; dwc:coordinateUncertaintyInMeters \"".getBytes(UTF_8);
	private static final byte[] elevation = "\n; dwc:elevation \"".getBytes(UTF_8);
	private static final byte[] elevationaccuracy  = "\n; dwc:elevationAccuracy \"".getBytes(UTF_8);
	private static final byte[] depth  = "\n; dwc:depth \"".getBytes(UTF_8);
	private static final byte[] endTripleBlock = " . \n".getBytes(UTF_8);
	
	
	private void convertRows(RowReader rows, Map<KnownColumns, Integer> knownColumnsMap, OutputStream fos)
			throws IOException {
		byte[] buffer = new byte[BUFFER_SIZE];
		int bufferUse = 0;
		int gbifColumnId = knownColumnsMap.get(KnownColumns.gbifid);
		int occurenceStatusId = knownColumnsMap.get(KnownColumns.occurrencestatus);
		int individualCountId = knownColumnsMap.get(KnownColumns.individualcount);
		int publishingorgkeyId = knownColumnsMap.get(KnownColumns.publishingorgkey);
		int countryCodeId = knownColumnsMap.get(KnownColumns.countrycode);
		int decimallatitudeId = knownColumnsMap.get(KnownColumns.decimallatitude);
		int decimalLongitudeId = knownColumnsMap.get(KnownColumns.decimallongitude);
		int coordinateUncertaintyInMetersId = knownColumnsMap.get(KnownColumns.coordinateuncertaintyinmeters);
		int elevationId = knownColumnsMap.get(KnownColumns.elevation);
		int elevationAccuracyId = knownColumnsMap.get(KnownColumns.elevationaccuracy);
		int depthId = knownColumnsMap.get(KnownColumns.depth);
		while (rows.hasNext()) {
			rows.next();
			bufferUse = addGbifId(rows, fos, buffer, bufferUse, gbifColumnId);

			bufferUse = addOccurenceStatus(rows, fos, buffer, bufferUse, occurenceStatusId);
			bufferUse = addAsInteger(rows, fos, buffer, bufferUse, individualCount, individualCountId);
			bufferUse = addAsRawString(rows, fos, buffer, bufferUse, publishingOrgKey, publishingorgkeyId);
			bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, countryCode, countryCodeId);
			bufferUse = addCoordinates(rows, fos, buffer, bufferUse, decimallatitudeId, decimalLongitudeId,
					coordinateUncertaintyInMetersId, elevationId, elevationAccuracyId, depthId);
			bufferUse = add(buffer, endTripleBlock, fos, bufferUse);
		}
		fos.write(buffer, 0, bufferUse);
	}

	private int addCoordinates(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, int decimallatitudeId,
			int decimalLongitudeId, int coordinateUncertaintyInMetersId, int elevationId, int elevationAccuracyId,
			int depthId) throws IOException {
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, decimalLatitude, decimallatitudeId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, decimalLongitude, decimalLongitudeId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, coordinateUncertaintyInMeters, coordinateUncertaintyInMetersId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, elevation, elevationId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, elevationaccuracy, elevationAccuracyId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, depth, depthId);
		return bufferUse;
	}
	
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

		try (ParquetFileReader reader = ParquetFileReader.open(InputFile.of(path1))) {
			Instant startFile = Instant.now();
			FileSchema schema = reader.getFileSchema();
			mapKnownColumnsToIds(knownColumnsMap, schema);
			RowReader rows = reader.buildRowReader()
					.projection(ColumnProjection.columns(
							Arrays.stream(KnownColumns.values()).map(KnownColumns::columnName).toArray(String[]::new)))
					.build();

			convertRows(rows, knownColumnsMap, fos);
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

		os.write(PREFIXES);
	}

	

	private int addAsRawString(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, byte[] predicate, int colId)
			throws IOException {
		if (rows.isNull(colId)) {
			return bufferUse;
		} else {
			bufferUse = add(buffer, predicate, fos, bufferUse);
			return add(buffer, rows.getString(colId).getBytes(UTF_8), fos, bufferUse);
		}
	}

	private int addAsLiteralString(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, byte[] predicate,
			int colId) throws IOException {
		if (rows.isNull(colId)) {
			return bufferUse;
		} else {
			bufferUse = add(buffer, predicate, fos, bufferUse);
			bufferUse = add(buffer, rows.getString(colId).getBytes(UTF_8), fos, bufferUse);
			return closeLiteral(buffer, fos, bufferUse);
		}
	}
	
	private int addAsDouble(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, byte[] predicate,
			int colId) throws IOException {
		if (rows.isNull(colId)) {
			return bufferUse;
		} else {
			bufferUse = add(buffer, predicate, fos, bufferUse);
			bufferUse = add(buffer, Double.toString(rows.getDouble(colId)).getBytes(UTF_8), fos, bufferUse);
			return closeDouble(buffer, fos, bufferUse);
		}
	}

	private int addOccurenceStatus(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse,
			int occurenceStatusId) throws IOException {
		bufferUse = add(buffer, occurrenceStatus, fos, bufferUse);
		byte[] occurencestatus = rows.getString(occurenceStatusId).getBytes(UTF_8);
		bufferUse = add(buffer, occurencestatus, fos, bufferUse);
		return closeLiteral(buffer, fos, bufferUse);
	}

	private int addGbifId(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, int gbifColumnId)
			throws IOException {
		bufferUse = add(buffer, gbifid, fos, bufferUse);
		byte[] gbifid = rows.getString(gbifColumnId).getBytes(UTF_8);
		bufferUse = add(buffer, gbifid, fos, bufferUse);

		bufferUse = add(buffer, isOccurence, fos, bufferUse);
		bufferUse = add(buffer, gbifid, fos, bufferUse);
		bufferUse = closeLiteral(buffer, fos, bufferUse);
		return bufferUse;
	}

	private int closeLiteral(byte[] buffer, OutputStream fos, int bufferUse) throws IOException {
		return add(buffer, closeLiteral, fos, bufferUse);
	}
	
	private int closeDouble(byte[] buffer, OutputStream fos, int bufferUse) throws IOException {
		return add(buffer, closeDoubleLiteral, fos, bufferUse);
	}

	private int addAsInteger(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse,
			byte[] predicate, int individualCountId) throws IOException {
		if (!rows.isNull(individualCountId)) {
			bufferUse = add(buffer, predicate, fos, bufferUse);
			int int1 = rows.getInt(individualCountId);
			bufferUse = add(buffer, int1, fos, bufferUse);
		}
		return bufferUse;
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

	private int add(byte[] buffer, int intToAdd, OutputStream fos, int bufferUse) throws IOException {
		byte[] toAdd = Integer.toString(intToAdd).getBytes(UTF_8);
		return add(buffer, toAdd, fos, bufferUse);
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

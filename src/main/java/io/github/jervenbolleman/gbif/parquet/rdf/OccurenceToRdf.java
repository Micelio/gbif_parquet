package io.github.jervenbolleman.gbif.parquet.rdf;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.PrimitiveIterator.OfInt;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import javax.sql.rowset.CachedRowSet;

import dev.hardwood.InputFile;
import dev.hardwood.reader.ParquetFileReader;
import dev.hardwood.reader.RowReader;
import dev.hardwood.row.StructAccessor;
import dev.hardwood.schema.ColumnProjection;
import dev.hardwood.schema.ColumnSchema;
import dev.hardwood.schema.FileSchema;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "occurence-to-rdf", description = "Convert GBIF occurrence parquet files to RDF")
public class OccurenceToRdf implements Callable<Integer> {

	private static final byte[] XSD_DATE = "\"^^xsd:date".getBytes(UTF_8);
	private static final byte[] XSD_GDAY = "\"^^xsd:gDay".getBytes(UTF_8);
	private static final byte[] XSD_GMONTH = "\"^^xsd:gMonth".getBytes(UTF_8);
	private static final byte[] XSD_GYEAR = "\"^^xsd:gYear".getBytes(UTF_8);

	private static final byte[] POINT = "; wdt:P625 \"Point(".getBytes(UTF_8);
	private static final byte[] SPACE = " ".getBytes(UTF_8);
	private static final byte[] CLOSE_POINT = ")\"^^geo:wktLiteral ".getBytes(UTF_8);
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
	private static final byte[] END_TRIPLE_BLOCK = " . \n".getBytes(UTF_8);
	private static final byte[] CLOSE_LITERAL = "\" ".getBytes(UTF_8);
	private static final byte[] CLOSE_IRI = "> ".getBytes(UTF_8);

	private static final byte[] countryCode = "; dwc:countryCode \"".getBytes(UTF_8);
	private static final byte[] closeDoubleLiteral = "\"^^xsd:double ".getBytes(UTF_8);
	private static final byte[] gbifid = "gbif:".getBytes(UTF_8);
	private static final byte[] isOccurence = " a dwc:Occurence ; gbifterm:gbifID \"".getBytes(UTF_8);
	private static final byte[] occurrenceStatus = "; dwc:occurrenceStatus \"".getBytes(UTF_8);
	private static final byte[] individualCount = "; dwc:individualCount ".getBytes(UTF_8);
	private static final byte[] publishingOrgKey = "; dwc:publishingOrgKey gbifpub:".getBytes(UTF_8);
	private static final byte[] decimalLatitude = "; dwc:decimalLatitude \"".getBytes(UTF_8);
	private static final byte[] decimalLongitude = "; dwc:decimalLongitude \"".getBytes(UTF_8);
	private static final byte[] coordinateUncertaintyInMeters = "; dwc:coordinateUncertaintyInMeters \""
			.getBytes(UTF_8);
	private static final byte[] elevation = "; dwc:elevation \"".getBytes(UTF_8);
	private static final byte[] elevationaccuracy = "; dwc:elevationAccuracy \"".getBytes(UTF_8);
	private static final byte[] depth = "; dwc:depth \"".getBytes(UTF_8);
	private static final byte[] depthaccuracy = "; dwc:depthaccuracy \"".getBytes(UTF_8);

	private static final byte[] eventDate = "; dwc:eventDate \"".getBytes(UTF_8);
	private static final byte[] day = "; dwc:day \"".getBytes(UTF_8);
	private static final byte[] month = "; dwc:month \"".getBytes(UTF_8);
	private static final byte[] year = "; dwc:year \"".getBytes(UTF_8);

	private static final byte[] basisOfRecord = "; dwc:basisOfRecord \"".getBytes(UTF_8);
	private static final byte[] institutioncode = "; dwc:institutionCode \"".getBytes(UTF_8);
	private static final byte[] collectioncode = "; dwc:collectionCode \"".getBytes(UTF_8);
	private static final byte[] catalognumber = "; dwc:catalogNumber \"".getBytes(UTF_8);
	private static final byte[] recordnumber = "; dwc:recordNumber \"".getBytes(UTF_8);

	private static final byte[] identifiedby = "; dwc:identifiedBy \"".getBytes(UTF_8);
	private static final byte[] dateidentified = "; dwc:dateIdentified \"".getBytes(UTF_8);
	private static final byte[] license = "; dwc:license <".getBytes(UTF_8);
	private static final byte[] CC_BY_4_0 = "https://creativecommons.org/licenses/by/4.0/".getBytes(UTF_8);

	private static final byte[] CC_BY_NC_4_0 = "https://creativecommons.org/licenses/by-nc/4.0/".getBytes(UTF_8);
	private static final byte[] CC0_1_0 = "https://creativecommons.org/publicdomain/zero/1.0/".getBytes(UTF_8);

	private void convertRows(RowReader rows, Map<KnownColumns, Integer> knownColumnsMap, OutputStream fos)
			throws IOException {
		byte[] buffer = new byte[BUFFER_SIZE];
		int bufferUse = 0;
		int gbifColumnId = getColumnId(knownColumnsMap, KnownColumns.gbifid);
		int occurenceStatusId = getColumnId(knownColumnsMap, KnownColumns.occurrencestatus);
		int individualCountId = getColumnId(knownColumnsMap, KnownColumns.individualcount);
		int publishingorgkeyId = getColumnId(knownColumnsMap, KnownColumns.publishingorgkey);
		int countryCodeId = getColumnId(knownColumnsMap, KnownColumns.countrycode);
		int decimallatitudeId = getColumnId(knownColumnsMap, KnownColumns.decimallatitude);
		int decimalLongitudeId = getColumnId(knownColumnsMap, KnownColumns.decimallongitude);
		int coordinateUncertaintyInMetersId = getColumnId(knownColumnsMap, KnownColumns.coordinateuncertaintyinmeters);
		int elevationId = getColumnId(knownColumnsMap, KnownColumns.elevation);
		int elevationAccuracyId = getColumnId(knownColumnsMap, KnownColumns.elevationaccuracy);
		int depthId = getColumnId(knownColumnsMap, KnownColumns.depth);
		int depthAccuracyId = getColumnId(knownColumnsMap, KnownColumns.depth);
		int eventdateId = getColumnId(knownColumnsMap, KnownColumns.eventdate);
		int dayId = getColumnId(knownColumnsMap, KnownColumns.day);
		int monthId = getColumnId(knownColumnsMap, KnownColumns.month);
		int yearId = getColumnId(knownColumnsMap, KnownColumns.year);
		int basisOfRecordId = getColumnId(knownColumnsMap, KnownColumns.basisofrecord);
		int institutioncodeId = getColumnId(knownColumnsMap, KnownColumns.institutioncode);
		int collectioncodeId = getColumnId(knownColumnsMap, KnownColumns.collectioncode);
		int catalognumberId = getColumnId(knownColumnsMap, KnownColumns.catalognumber);
		int recordnumberId = getColumnId(knownColumnsMap, KnownColumns.recordnumber);
		int identifiedbyId = getColumnId(knownColumnsMap, KnownColumns.identifiedby);
		int dateidentifiedId = getColumnId(knownColumnsMap, KnownColumns.dateidentified);
		while (rows.hasNext()) {
			rows.next();
			bufferUse = addGbifId(rows, fos, buffer, bufferUse, gbifColumnId);

			bufferUse = addOccurenceStatus(rows, fos, buffer, bufferUse, occurenceStatusId);
			bufferUse = addAsInteger(rows, fos, buffer, bufferUse, individualCount, individualCountId);
			bufferUse = addAsRawString(rows, fos, buffer, bufferUse, publishingOrgKey, publishingorgkeyId);
			bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, countryCode, countryCodeId);
			bufferUse = addCoordinates(rows, fos, buffer, bufferUse, decimallatitudeId, decimalLongitudeId,
					coordinateUncertaintyInMetersId, elevationId, elevationAccuracyId, depthId, depthAccuracyId);
			bufferUse = addDate(rows, fos, buffer, bufferUse, eventdateId, dayId, monthId, yearId);
			bufferUse = andRecordData(rows, fos, buffer, bufferUse, basisOfRecordId, institutioncodeId,
					collectioncodeId, catalognumberId, recordnumberId, identifiedbyId, dateidentifiedId);
			bufferUse = addLicense(rows, fos, buffer, bufferUse, getColumnId(knownColumnsMap, KnownColumns.license));
			bufferUse = add(buffer, END_TRIPLE_BLOCK, fos, bufferUse);
		}
		fos.write(buffer, 0, bufferUse);
	}

	private int addLicense(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, int columnId)
			throws IOException {
		if (columnId < 0 || rows.isNull(columnId)) {
			return bufferUse;
		} else {
			bufferUse = add(buffer, license, fos, bufferUse);
			switch (rows.getString(columnId)) {
			case "CC_BY_4_0":
				bufferUse = add(buffer, CC_BY_4_0, fos, bufferUse);
				break;
			case "CC_BY_NC_4_0":
				bufferUse = add(buffer, CC_BY_NC_4_0, fos, bufferUse);
				break;
			case "CC0_1_0":
				bufferUse = add(buffer, CC0_1_0, fos, bufferUse);
				break;
			default:
				throw new IOException("Unknown license: " + rows.getString(columnId));
			}

			bufferUse = add(buffer, rows.getString(columnId).getBytes(UTF_8), fos, bufferUse);
			bufferUse = closeIri(buffer, fos, bufferUse);
			return bufferUse;
		}
	}

	private int getColumnId(Map<KnownColumns, Integer> knownColumnsMap, KnownColumns kc) {
		return knownColumnsMap.getOrDefault(kc, -404);
	}

	private int andRecordData(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, int basisOfRecordId,
			int institutioncodeId, int collectioncodeId, int catalognumberId, int recordnumberId, int identifiedbyId,
			int dateidentifiedId) throws IOException {
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, basisOfRecord, basisOfRecordId);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, institutioncode, institutioncodeId);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, collectioncode, collectioncodeId);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, catalognumber, catalognumberId);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, recordnumber, recordnumberId);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, identifiedby, identifiedbyId);
		bufferUse = addAsDatatypeString(rows, fos, buffer, bufferUse, dateidentified, dateidentifiedId, XSD_DATE,
				(s) -> fromTimestampToXsdDate(s, dateidentifiedId));
		return bufferUse;
	}

	private static byte[] fromTimestampToXsdDate(StructAccessor s, int colId) {
		Instant timestamp = s.getTimestamp(colId);
		LocalDate localD = LocalDate.ofInstant(timestamp, ZoneOffset.UTC);
		return DateTimeFormatter.ISO_LOCAL_DATE.format(localD).getBytes(UTF_8);
	}

	private int addDate(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, int eventdateId, int dayId,
			int monthId, int yearId) throws IOException {
		bufferUse = addAsDatatypeString(rows, fos, buffer, bufferUse, eventDate, eventdateId, XSD_DATE,
				(s) -> fromTimestampToXsdDate(s, eventdateId));
		bufferUse = addAsDatatypeString(rows, fos, buffer, bufferUse, day, dayId, XSD_GDAY, (s) -> {

			int dc = s.getInt(dayId);
			if (dc < 10) {
				return ("---0" + dc).getBytes(UTF_8);
			} else {
				return ("---" + dc).getBytes(UTF_8);
			}
		});
		bufferUse = addAsDatatypeString(rows, fos, buffer, bufferUse, month, monthId, XSD_GMONTH, (s) -> {
			int monthInt = s.getInt(monthId);
			if (monthInt < 10) {
				return ("--0" + monthInt).getBytes(UTF_8);
			} else {
				return ("--" + monthInt).getBytes(UTF_8);
			}
		});
		bufferUse = addAsDatatypeString(rows, fos, buffer, bufferUse, year, yearId, XSD_GYEAR, (s) -> {
			return Integer.toString(s.getInt(yearId)).getBytes(UTF_8);
		});
		return bufferUse;
	}

	private int addAsDatatypeString(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, byte[] predicate,
			int colId, byte[] datatype, Function<StructAccessor, byte[]> extractor) throws IOException {
		if (colId < 0 || rows.isNull(colId)) {
			return bufferUse;
		} else {
			bufferUse = add(buffer, predicate, fos, bufferUse);
			bufferUse = add(buffer, extractor.apply(rows), fos, bufferUse);
			bufferUse = add(buffer, datatype, fos, bufferUse);
			return bufferUse;
		}
	}

	private int addCoordinates(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, int decimallatitudeId,
			int decimalLongitudeId, int coordinateUncertaintyInMetersId, int elevationId, int elevationAccuracyId,
			int depthId, int depthAccuracyId) throws IOException {
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, decimalLatitude, decimallatitudeId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, decimalLongitude, decimalLongitudeId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, coordinateUncertaintyInMeters,
				coordinateUncertaintyInMetersId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, elevation, elevationId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, elevationaccuracy, elevationAccuracyId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, depth, depthId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, depthaccuracy, depthAccuracyId);
		if (!rows.isNull(decimallatitudeId) && !rows.isNull(decimalLongitudeId)) {
			bufferUse = add(buffer, POINT, fos, bufferUse);
			bufferUse = add(buffer, Double.toString(rows.getDouble(decimalLongitudeId)).getBytes(UTF_8), fos,
					bufferUse);
			bufferUse = add(buffer, SPACE, fos, bufferUse);
			bufferUse = add(buffer, Double.toString(rows.getDouble(decimallatitudeId)).getBytes(UTF_8), fos, bufferUse);
			bufferUse = add(buffer, CLOSE_POINT, fos, bufferUse);

		}
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

	private int addAsRawString(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, byte[] predicate,
			int colId) throws IOException {
		if (rows.isNull(colId)) {
			return bufferUse;
		} else {
			bufferUse = add(buffer, predicate, fos, bufferUse);
			return add(buffer, rows.getString(colId).getBytes(UTF_8), fos, bufferUse);
		}
	}

	private int addAsLiteralString(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, byte[] predicate,
			int colId) throws IOException {
		if (colId < 0 || rows.isNull(colId)) {
			return bufferUse;
		} else {
			bufferUse = add(buffer, predicate, fos, bufferUse);
			bufferUse = add(buffer, rows.getString(colId).getBytes(UTF_8), fos, bufferUse);
			return closeLiteral(buffer, fos, bufferUse);
		}
	}

	private int addAsDouble(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, byte[] predicate, int colId)
			throws IOException {
		if (colId < 0 || rows.isNull(colId)) {
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
		return add(buffer, CLOSE_LITERAL, fos, bufferUse);
	}
	
	private int closeIri(byte[] buffer, OutputStream fos, int bufferUse) throws IOException {
		return add(buffer, CLOSE_IRI, fos, bufferUse);
	}

	private int closeDouble(byte[] buffer, OutputStream fos, int bufferUse) throws IOException {
		return add(buffer, closeDoubleLiteral, fos, bufferUse);
	}

	private int addAsInteger(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, byte[] predicate,
			int individualCountId) throws IOException {
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

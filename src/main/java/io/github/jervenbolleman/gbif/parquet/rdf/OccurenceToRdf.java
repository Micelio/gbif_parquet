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
import java.util.BitSet;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.Map;
import java.util.PrimitiveIterator.OfInt;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.roaringbitmap.buffer.MutableRoaringBitmap;

import dev.hardwood.InputFile;
import dev.hardwood.reader.ParquetFileReader;
import dev.hardwood.reader.RowReader;
import dev.hardwood.row.PqList;
import dev.hardwood.row.StructAccessor;
import dev.hardwood.schema.ColumnProjection;
import dev.hardwood.schema.ColumnSchema;
import dev.hardwood.schema.FileSchema;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "occurence-to-rdf", description = "Convert GBIF occurrence parquet files to RDF")
public class OccurenceToRdf implements Callable<Integer> {

	private static final byte[] OPEN_LITERAL = "\"".getBytes(UTF_8);
	private static final byte[] COMMA = ", ".getBytes(UTF_8);
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
			PREFIX lc: <https://www.example.org/namedLocation/>
			PREFIX ccby4: <https://creativecommons.org/licenses/by/4.0/>
			PREFIX ccby4nc: <https://creativecommons.org/licenses/by-nc/4.0/>
			PREFIX cc0: <https://creativecommons.org/publicdomain/zero/1.0/>
				""".getBytes(UTF_8);

	private static final int BUFFER_SIZE = 16 * 8096;
	private static final byte[] END_TRIPLE_BLOCK = " .\n".getBytes(UTF_8);
	private static final byte[] CLOSE_LITERAL = "\"".getBytes(UTF_8);
	private static final String PRE = ";\n  ";
	private static final byte[] subclassof = (PRE + "rdfs:subClassOf ").getBytes(UTF_8);
	private static final byte[] countryCode = (PRE + "dwc:countryCode ").getBytes(UTF_8);
	private static final byte[] closeDoubleLiteral = "\"^^xsd:double ".getBytes(UTF_8);
	private static final byte[] gbif = "gbif:".getBytes(UTF_8);
	private static final byte[] gbifsp = "gbifsp:".getBytes(UTF_8);
	private static final byte[] isOccurence = (" a dwc:Occurence " + PRE + "gbifterm:gbifID ").getBytes(UTF_8);
	private static final byte[] occurrenceStatus = (PRE + "dwc:occurrenceStatus ").getBytes(UTF_8);
	private static final byte[] individualCount = (PRE + "dwc:individualCount ").getBytes(UTF_8);
	private static final byte[] publishingOrgKey = (PRE + "dwc:publishingOrgKey gbifpub:").getBytes(UTF_8);
	private static final byte[] decimalLatitude = (PRE + "dwc:decimalLatitude ").getBytes(UTF_8);
	private static final byte[] decimalLongitude = (PRE + "dwc:decimalLongitude ").getBytes(UTF_8);
	private static final byte[] coordinateUncertaintyInMeters = (PRE + "dwc:coordinateUncertaintyInMeters ")
			.getBytes(UTF_8);
	private static final byte[] elevation = (PRE + "dwc:elevation ").getBytes(UTF_8);
	private static final byte[] elevationaccuracy = (PRE + "dwc:elevationAccuracy ").getBytes(UTF_8);
	private static final byte[] depth = (PRE + "dwc:depth ").getBytes(UTF_8);
	private static final byte[] depthaccuracy = (PRE + "dwc:depthaccuracy ").getBytes(UTF_8);

	private static final byte[] eventDate = (PRE + "dwc:eventDate ").getBytes(UTF_8);
	private static final byte[] day = (PRE + "dwc:day ").getBytes(UTF_8);
	private static final byte[] month = (PRE + "dwc:month ").getBytes(UTF_8);
	private static final byte[] year = (PRE + "dwc:year ").getBytes(UTF_8);

	private static final byte[] basisOfRecord = (PRE + " dwc:basisOfRecord ").getBytes(UTF_8);
	private static final byte[] institutioncode = (PRE + "dwc:institutionCode ").getBytes(UTF_8);
	private static final byte[] collectioncode = (PRE + "dwc:collectionCode ").getBytes(UTF_8);
	private static final byte[] catalognumber = (PRE + "dwc:catalogNumber ").getBytes(UTF_8);
	private static final byte[] recordnumber = (PRE + " dwc:recordNumber ").getBytes(UTF_8);

	private static final byte[] identifiedby = (PRE + "dwc:identifiedBy ").getBytes(UTF_8);
	private static final byte[] dateidentified = (PRE + "dwc:dateIdentified ").getBytes(UTF_8);
	private static final byte[] license = (PRE + "dwc:license ").getBytes(UTF_8);
	private static final byte[] rightsholder = (PRE + "dwc:rightsHolder ").getBytes(UTF_8);
	private static final byte[] recordedby = (PRE + "dwc:recordedBy ").getBytes(UTF_8);
	private static final byte[] typestatus = (PRE + "dwc:typeStatus ").getBytes(UTF_8);
	private static final byte[] establishmentmeans = (PRE + "dwc:establishmentMeans ").getBytes(UTF_8);
	private static final byte[] lastinterpreted = (PRE + "dwc:lastInterpreted ").getBytes(UTF_8);
	private static final byte[] mediatype = (PRE + "dwc:mediaType ").getBytes(UTF_8);
	private static final byte[] issue = (PRE + "dwc:issue ").getBytes(UTF_8);
	private static final byte[] toTaxon = (PRE + "dwciri:toTaxon gbifsp:").getBytes(UTF_8);

	private static final byte[] kingdom = (PRE + "dwc:kingdom ").getBytes(UTF_8);
	private static final byte[] phylum = (PRE + "dwc:phylum ").getBytes(UTF_8);
	private static final byte[] clazz = (PRE + "dwc:class ").getBytes(UTF_8);
	private static final byte[] order = (PRE + "dwc:order ").getBytes(UTF_8);
	private static final byte[] family = (PRE + "dwc:family ").getBytes(UTF_8);
	private static final byte[] genus = (PRE + "dwc:genus ").getBytes(UTF_8);
	private static final byte[] taxonrank = (PRE + "dwc:taxonRank ").getBytes(UTF_8);
	private static final byte[] species = (PRE + "dwc:species ").getBytes(UTF_8);
	private static final byte[] infraspecificepithet = (PRE + "dwc:infraspecificEpithet ").getBytes(UTF_8);
	private static final byte[] inDescribedPlace = (PRE + "dwciri:inDescribedPlace ").getBytes(UTF_8);
	private static final byte[] verbatimscientificnameauthorship = (PRE + " dwc:verbatimScientificNameAuthorship ")
			.getBytes(UTF_8);

	private static final byte[] CC_BY_4_0 = "ccby4: ".getBytes(UTF_8);

	private static final byte[] CC_BY_NC_4_0 = "ccby4nc: ".getBytes(UTF_8);
	private static final byte[] CC0_1_0 = "cc0: ".getBytes(UTF_8);

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
		int rightsholderId = getColumnId(knownColumnsMap, KnownColumns.rightsholder);
		int recordedbyId = getColumnId(knownColumnsMap, KnownColumns.recordedby);
		int typestatus = getColumnId(knownColumnsMap, KnownColumns.typestatus);
		int establishmentmeans = getColumnId(knownColumnsMap, KnownColumns.establishmentmeans);
		int lastinterpreted = getColumnId(knownColumnsMap, KnownColumns.lastinterpreted);
		int mediatype = getColumnId(knownColumnsMap, KnownColumns.mediatype);
		int issue = getColumnId(knownColumnsMap, KnownColumns.issue);
		int taxonkeyId = getColumnId(knownColumnsMap, KnownColumns.taxonkey);
		int speciesId = getColumnId(knownColumnsMap, KnownColumns.specieskey);
		MutableRoaringBitmap seenTaxons = new MutableRoaringBitmap();
		while (rows.hasNext()) {
			rows.next();
			bufferUse = addGbifId(rows, fos, buffer, bufferUse, gbifColumnId);

			bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, occurrenceStatus, occurenceStatusId, false);
			bufferUse = addAsInteger(rows, fos, buffer, bufferUse, individualCount, individualCountId);
			bufferUse = addAsRawString(rows, fos, buffer, bufferUse, publishingOrgKey, publishingorgkeyId);
			bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, countryCode, countryCodeId, false);
			bufferUse = addCoordinates(rows, fos, buffer, bufferUse, decimallatitudeId, decimalLongitudeId,
					coordinateUncertaintyInMetersId, elevationId, elevationAccuracyId, depthId, depthAccuracyId);
			bufferUse = addDate(rows, fos, buffer, bufferUse, eventdateId, dayId, monthId, yearId);
			bufferUse = andRecordData(rows, fos, buffer, bufferUse, basisOfRecordId, institutioncodeId,
					collectioncodeId, catalognumberId, recordnumberId, identifiedbyId, dateidentifiedId, rightsholderId,
					recordedbyId, typestatus, establishmentmeans, lastinterpreted, mediatype, issue);
			bufferUse = addLicense(rows, fos, buffer, bufferUse, getColumnId(knownColumnsMap, KnownColumns.license));

			bufferUse = addTaxon(rows, fos, buffer, bufferUse, taxonkeyId, speciesId, seenTaxons, knownColumnsMap);
//			bufferUse = addLocation(rows, fos, buffer, bufferUse, gbifColumnId, knownColumnsMap);
		}
		fos.write(buffer, 0, bufferUse);
	}

	private int addLocation(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, int gbifColumnId,
			Map<KnownColumns, Integer> knownColumnsMap) throws IOException {
		int countryCodeId = getColumnId(knownColumnsMap, KnownColumns.countrycode);
		int stateProvinceId = getColumnId(knownColumnsMap, KnownColumns.stateprovince);
		int localityCodeId = getColumnId(knownColumnsMap, KnownColumns.locality);
		if (countryCodeId < 0 && stateProvinceId < 0 && localityCodeId < 0) {
			return bufferUse;
		}
		if (! rows.isNull(countryCodeId)) {
			bufferUse = add(buffer, gbif, fos, bufferUse);
			byte[] gbifid = rows.getString(gbifColumnId).getBytes(UTF_8);
			bufferUse = add(buffer, gbifid, fos, bufferUse);
			bufferUse = add(buffer, inDescribedPlace, fos, bufferUse);
			String stateProvince = null;
			String localityCode = null;
			if (! rows.isNull(stateProvinceId)) {
				stateProvince  = escape(rows.getString(stateProvinceId));
			}
			if (! rows.isNull(localityCodeId)) {
				localityCode = escape(rows.getString(localityCodeId));
			}
		}

		return bufferUse;
	}

	private String escape(String string) {
		// TODO Auto-generated method stub
		return null;
	}

	private int addTaxon(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, int taxonkeyId, int speciesId,
			MutableRoaringBitmap seenTaxons, Map<KnownColumns, Integer> knownColumnsMap) throws IOException {
		String taxon = null;
		String species = null;
		if (taxonkeyId < 0 || !rows.isNull(taxonkeyId)) {
			taxon = rows.getString(taxonkeyId);
			bufferUse = add(buffer, toTaxon, fos, bufferUse);
			bufferUse = add(buffer, rows.getString(taxonkeyId).getBytes(UTF_8), fos, bufferUse);
		}
		if (speciesId < 0 || !rows.isNull(speciesId)) {
			species = rows.getString(speciesId);
			if (species != null && !species.equals(taxon)) {
				bufferUse = add(buffer, toTaxon, fos, bufferUse);
				bufferUse = add(buffer, rows.getString(taxonkeyId).getBytes(UTF_8), fos, bufferUse);
			}
		}
		bufferUse = add(buffer, END_TRIPLE_BLOCK, fos, bufferUse);
		bufferUse = addTaxon(rows, fos, buffer, bufferUse, seenTaxons, taxon, null, knownColumnsMap);
		if (species != null && !species.equals(taxon)) {
			bufferUse = addTaxon(rows, fos, buffer, bufferUse, seenTaxons, species, taxon, knownColumnsMap);
		}
		return bufferUse;
	}

	private int addTaxon(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse,
			MutableRoaringBitmap seenTaxons, String taxon, String taxa,
			Map<KnownColumns, Integer> knownColumnsMap) throws IOException {
		if (taxon != null) {
			int taxonInt = Integer.parseInt(taxon);
			if (!seenTaxons.contains(taxonInt)) {
				seenTaxons.add(taxonInt);
				bufferUse = add(buffer, gbifsp, fos, bufferUse);
				bufferUse = add(buffer, taxon.getBytes(UTF_8), fos, bufferUse);
				bufferUse = add(buffer, " a dwc:Taxon ".getBytes(), fos, bufferUse);

				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, kingdom,
						getColumnId(knownColumnsMap, KnownColumns.kingdom), false);
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, phylum,
						getColumnId(knownColumnsMap, KnownColumns.phylum), false);
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, clazz,
						getColumnId(knownColumnsMap, KnownColumns.clazz), false);
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, order,
						getColumnId(knownColumnsMap, KnownColumns.order), false);
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, family,
						getColumnId(knownColumnsMap, KnownColumns.family), false);
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, genus,
						getColumnId(knownColumnsMap, KnownColumns.genus), false);
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, taxonrank,
						getColumnId(knownColumnsMap, KnownColumns.taxonrank), false);
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, verbatimscientificnameauthorship,
						getColumnId(knownColumnsMap, KnownColumns.verbatimscientificnameauthorship), false);
				if (taxa!=null) {
					bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, species,
							getColumnId(knownColumnsMap, KnownColumns.species), false);
					bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, infraspecificepithet,
							getColumnId(knownColumnsMap, KnownColumns.infraspecificepithet), false);
					bufferUse = add(buffer, subclassof, fos, bufferUse);
					bufferUse = add(buffer, gbifsp, fos, bufferUse);
				}
				bufferUse = add(buffer, END_TRIPLE_BLOCK, fos, bufferUse);
				return bufferUse;
			}
		}
		return bufferUse;
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
			return bufferUse;
		}
	}

	private int getColumnId(Map<KnownColumns, Integer> knownColumnsMap, KnownColumns kc) {
		return knownColumnsMap.getOrDefault(kc, -404);
	}

	private int andRecordData(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, int basisOfRecordId,
			int institutioncodeId, int collectioncodeId, int catalognumberId, int recordnumberId, int identifiedbyId,
			int dateidentifiedId, int rightsholderId, int recordedbyId, int typestatusId, int establishmentmeansId,
			int lastinterpretedId, int mediatypeId, int issueId) throws IOException {
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, basisOfRecord, basisOfRecordId, false);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, institutioncode, institutioncodeId, false);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, collectioncode, collectioncodeId, false);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, catalognumber, catalognumberId, false);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, recordnumber, recordnumberId, true);
		bufferUse = addAsLiteralStrings(rows, fos, buffer, bufferUse, identifiedby,
				KnownColumns.identifiedby.columnName(), true);
		bufferUse = addAsDatatypeString(rows, fos, buffer, bufferUse, dateidentified, dateidentifiedId, XSD_DATE,
				(s) -> fromTimestampToXsdDate(s, dateidentifiedId));
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, rightsholder, rightsholderId, false);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, recordedby, recordedbyId, false);
		bufferUse = addAsLiteralStrings(rows, fos, buffer, bufferUse, typestatus, KnownColumns.typestatus.columnName(),
				false);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, establishmentmeans, establishmentmeansId, false);
		bufferUse = addAsDatatypeString(rows, fos, buffer, bufferUse, lastinterpreted, lastinterpretedId, XSD_DATE,
				(s) -> fromTimestampToXsdDate(s, lastinterpretedId));
		bufferUse = addAsLiteralStrings(rows, fos, buffer, bufferUse, mediatype, KnownColumns.mediatype.columnName(),
				false);
		bufferUse = addAsLiteralStrings(rows, fos, buffer, bufferUse, issue, KnownColumns.issue.columnName(), true);
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
			bufferUse = add(buffer, OPEN_LITERAL, fos, bufferUse);
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
	private static final byte[] STRING_DELIM = "\"".getBytes(UTF_8);
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
			int colId, boolean escape) throws IOException {
		if (colId < 0 || rows.isNull(colId)) {
			return bufferUse;
		} else {
			bufferUse = add(buffer, predicate, fos, bufferUse);
			bufferUse = add(buffer, STRING_DELIM, fos, bufferUse);
			if (escape) {
				String toPrint = rows.getString(colId);
				toPrint = escapeQuotes(toPrint);
				bufferUse = add(buffer, toPrint.getBytes(UTF_8), fos, bufferUse);
			} else {
				bufferUse = add(buffer, rows.getBinary(colId), fos, bufferUse);
			}
			bufferUse = add(buffer, STRING_DELIM, fos, bufferUse);
			return bufferUse;
		}
	}

	private int addAsLiteralStrings(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, byte[] predicate,
			String colId, boolean escape) throws IOException {
		PqList list = rows.getList(colId);
		if (list != null && !list.isEmpty()) {
			bufferUse = add(buffer, predicate, fos, bufferUse);
			for (Iterator<String> iterator = list.strings().iterator(); iterator.hasNext();) {
				String li = iterator.next();
				if (escape) {
					li = escapeQuotes(li);
				}
				bufferUse = add(buffer, STRING_DELIM, fos, bufferUse);
				bufferUse = add(buffer, li.getBytes(UTF_8), fos, bufferUse);
				bufferUse = add(buffer, STRING_DELIM, fos, bufferUse);
				if (iterator.hasNext()) {
					bufferUse = add(buffer, COMMA, fos, bufferUse);
				}
			}
			return bufferUse;
		} else {
			return bufferUse;
		}
	}

	private String escapeQuotes(String li) {
		li = li.replace("\"", "\\\"");
		return li;
	}

	private int addAsDouble(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, byte[] predicate, int colId)
			throws IOException {
		if (colId < 0 || rows.isNull(colId)) {
			return bufferUse;
		} else {
			bufferUse = add(buffer, predicate, fos, bufferUse);
			bufferUse = add(buffer, OPEN_LITERAL, fos, bufferUse);
			bufferUse = add(buffer, Double.toString(rows.getDouble(colId)).getBytes(UTF_8), fos, bufferUse);
			return closeDouble(buffer, fos, bufferUse);
		}
	}

	private int addGbifId(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, int gbifColumnId)
			throws IOException {
		bufferUse = add(buffer, gbif, fos, bufferUse);

		byte[] gbifid = rows.getString(gbifColumnId).getBytes(UTF_8);
		bufferUse = add(buffer, gbifid, fos, bufferUse);

		bufferUse = add(buffer, isOccurence, fos, bufferUse);
		bufferUse = add(buffer, OPEN_LITERAL, fos, bufferUse);
		bufferUse = add(buffer, gbifid, fos, bufferUse);
		bufferUse = closeLiteral(buffer, fos, bufferUse);
		return bufferUse;
	}

	private int closeLiteral(byte[] buffer, OutputStream fos, int bufferUse) throws IOException {
		return add(buffer, CLOSE_LITERAL, fos, bufferUse);
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
		if (toAdd.length > buffer.length) {
			fos.write(buffer, 0, bufferUse);
			fos.write(toAdd, 0, toAdd.length);
			return 0;
		} else if (nextEnd > buffer.length) {
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

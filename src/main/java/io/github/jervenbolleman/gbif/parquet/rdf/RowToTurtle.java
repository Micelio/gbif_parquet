package io.github.jervenbolleman.gbif.parquet.rdf;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Function;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.util.GeometricShapeFactory;
import org.roaringbitmap.buffer.MutableRoaringBitmap;

import dev.hardwood.reader.RowReader;
import dev.hardwood.row.PqList;
import dev.hardwood.row.StructAccessor;

public class RowToTurtle {
	private static final byte[] OPEN_LITERAL = "\"".getBytes(UTF_8);
	private static final byte[] COMMA = ", ".getBytes(UTF_8);
	private static final byte[] XSD_DATE = "\"^^xsd:date".getBytes(UTF_8);
	private static final byte[] XSD_GDAY = "\"^^xsd:gDay".getBytes(UTF_8);
	private static final byte[] XSD_GMONTH = "\"^^xsd:gMonth".getBytes(UTF_8);
	private static final byte[] XSD_GYEAR = "\"^^xsd:gYear".getBytes(UTF_8);

	private static final byte[] POINT = "wdt:P625 \"Point(".getBytes(UTF_8);
	private static final byte[] POLYGON = "wdt:P625 \"POLYGON((".getBytes(UTF_8);
	private static final byte[] SPACE = " ".getBytes(UTF_8);
	private static final byte[] CLOSE_POINT = ")\"^^geo:wktLiteral ".getBytes(UTF_8);
	private static final byte[] CLOSE_POLYGON = "))\"^^geo:wktLiteral ".getBytes(UTF_8);
	private static final byte[] END_TRIPLE_BLOCK = " .\n".getBytes(UTF_8);
	private static final byte[] CLOSE_LITERAL = "\"".getBytes(UTF_8);
	private static final String PRE = ";\n  ";
	private static final byte[] PREB = PRE.getBytes(UTF_8);
	private static final byte[] subclassof = ("rdfs:subClassOf ").getBytes(UTF_8);
	private static final byte[] countryCode = ("dwc:countryCode ").getBytes(UTF_8);
	private static final byte[] closeDoubleLiteral = "\"^^xsd:double ".getBytes(UTF_8);
	private static final byte[] gbif = "gbif:".getBytes(UTF_8);
	private static final byte[] gbifsp = "gbifsp:".getBytes(UTF_8);
	private static final byte[] isOccurence = (" a dwc:Occurence " + PRE + "gbifterm:gbifID ").getBytes(UTF_8);
	private static final byte[] occurrenceStatus = ("dwc:occurrenceStatus ").getBytes(UTF_8);
	private static final byte[] individualCount = ("dwc:individualCount ").getBytes(UTF_8);
	private static final byte[] publishingOrgKey = ("dwc:publishingOrgKey gbifpub:").getBytes(UTF_8);
	private static final byte[] decimalLatitude = ("dwc:decimalLatitude ").getBytes(UTF_8);
	private static final byte[] decimalLongitude = ("dwc:decimalLongitude ").getBytes(UTF_8);
	private static final byte[] coordinateUncertaintyInMeters = ("dwc:coordinateUncertaintyInMeters ").getBytes(UTF_8);
	private static final byte[] elevation = ("dwc:elevation ").getBytes(UTF_8);
	private static final byte[] elevationaccuracy = ("dwc:elevationAccuracy ").getBytes(UTF_8);
	private static final byte[] depth = ("dwc:depth ").getBytes(UTF_8);
	private static final byte[] depthaccuracy = ("dwc:depthaccuracy ").getBytes(UTF_8);

	private static final byte[] eventDate = ("dwc:eventDate ").getBytes(UTF_8);
	private static final byte[] day = ("dwc:day ").getBytes(UTF_8);
	private static final byte[] month = ("dwc:month ").getBytes(UTF_8);
	private static final byte[] year = ("dwc:year ").getBytes(UTF_8);

	private static final byte[] basisOfRecord = (" dwc:basisOfRecord ").getBytes(UTF_8);
	private static final byte[] institutioncode = ("dwc:institutionCode ").getBytes(UTF_8);
	private static final byte[] collectioncode = ("dwc:collectionCode ").getBytes(UTF_8);
	private static final byte[] catalognumber = ("dwc:catalogNumber ").getBytes(UTF_8);
	private static final byte[] recordnumber = (" dwc:recordNumber ").getBytes(UTF_8);

	private static final byte[] identifiedby = ("dwc:identifiedBy ").getBytes(UTF_8);
	private static final byte[] dateidentified = ("dwc:dateIdentified ").getBytes(UTF_8);
	private static final byte[] license = ("dwc:license ").getBytes(UTF_8);
	private static final byte[] rightsholder = ("dwc:rightsHolder ").getBytes(UTF_8);
	private static final byte[] recordedby = ("dwc:recordedBy ").getBytes(UTF_8);
	private static final byte[] typestatus = ("dwc:typeStatus ").getBytes(UTF_8);
	private static final byte[] establishmentmeans = ("dwc:establishmentMeans ").getBytes(UTF_8);
	private static final byte[] lastinterpreted = ("dwc:lastInterpreted ").getBytes(UTF_8);
	private static final byte[] mediatype = ("dwc:mediaType ").getBytes(UTF_8);
	private static final byte[] issue = ("dwc:issue ").getBytes(UTF_8);
	private static final byte[] toTaxon = ("dwciri:toTaxon gbifsp:").getBytes(UTF_8);

	private static final byte[] kingdom = ("dwc:kingdom ").getBytes(UTF_8);
	private static final byte[] phylum = ("dwc:phylum ").getBytes(UTF_8);
	private static final byte[] clazz = ("dwc:class ").getBytes(UTF_8);
	private static final byte[] order = ("dwc:order ").getBytes(UTF_8);
	private static final byte[] family = ("dwc:family ").getBytes(UTF_8);
	private static final byte[] genus = ("dwc:genus ").getBytes(UTF_8);
	private static final byte[] taxonrank = ("dwc:taxonRank ").getBytes(UTF_8);
	private static final byte[] species = ("dwc:species ").getBytes(UTF_8);
	private static final byte[] infraspecificepithet = ("dwc:infraspecificEpithet ").getBytes(UTF_8);
	private static final byte[] inDescribedPlace = (" dwciri:inDescribedPlace ").getBytes(UTF_8);
	private static final byte[] stateProvince = ("dwc:stateProvince ").getBytes(UTF_8);
	private static final byte[] localityCode = ("dwc:locality ").getBytes(UTF_8);
	private static final byte[] STRING_DELIM = "\"".getBytes(UTF_8);
	private static final byte[] verbatimscientificnameauthorship = (" dwc:verbatimScientificNameAuthorship ")
			.getBytes(UTF_8);

	private static final byte[] CC_BY_4_0 = "ccby4: ".getBytes(UTF_8);

	private static final byte[] CC_BY_NC_4_0 = "ccby4nc: ".getBytes(UTF_8);
	private static final byte[] CC0_1_0 = "cc0: ".getBytes(UTF_8);
	private static final int BUFFER_SIZE = 16 * 8096;

	static void convertRows(RowReader rows, Map<KnownColumns, Integer> knownColumnsMap, OutputStream fos, boolean taxonIsInt, boolean gbifidIsLong)
			throws IOException, NoSuchAlgorithmException {
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
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		while (rows.hasNext()) {
			rows.next();
			bufferUse = addGbifId(rows, fos, buffer, bufferUse, gbifColumnId, gbifidIsLong);

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

			bufferUse = addTaxon(rows, fos, buffer, bufferUse, taxonkeyId, speciesId, seenTaxons, knownColumnsMap, taxonIsInt);
			bufferUse = addLocation(rows, fos, buffer, bufferUse, gbifColumnId, knownColumnsMap, md);
		}
		fos.write(buffer, 0, bufferUse);
	}

	private static int addLocation(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, int gbifColumnId,
			Map<KnownColumns, Integer> knownColumnsMap, MessageDigest md) throws IOException {
		int countryCodeId = getColumnId(knownColumnsMap, KnownColumns.countrycode);
		int stateProvinceId = getColumnId(knownColumnsMap, KnownColumns.stateprovince);
		int localityCodeId = getColumnId(knownColumnsMap, KnownColumns.locality);
		if (countryCodeId < 0 && stateProvinceId < 0 && localityCodeId < 0) {
			return bufferUse;
		}
		if (!rows.isNull(countryCodeId)) {
			String cc = rows.getString(countryCodeId);
			bufferUse = add(buffer, gbif, fos, bufferUse);
			byte[] gbifid = rows.getString(gbifColumnId).getBytes(UTF_8);
			bufferUse = add(buffer, gbifid, fos, bufferUse);
			bufferUse = add(buffer, inDescribedPlace, fos, bufferUse);
			String stateProvinceS = null;
			String localityCodeS = null;
			String locIri;

			if (!rows.isNull(stateProvinceId) && !rows.isNull(localityCodeId)) {
				stateProvinceS = escape(rows.getString(stateProvinceId));
				localityCodeS = escape(rows.getString(localityCodeId));

				locIri = "nl:" + HexFormat.of()
						.formatHex(md.digest((cc + "-sp-" + stateProvinceS + "lc" + localityCodeS).getBytes(UTF_8)));
			} else if (!rows.isNull(stateProvinceId)) {
				stateProvinceS = escape(rows.getString(stateProvinceId));
				locIri = "nl:" + HexFormat.of().formatHex(md.digest((cc + "-sp-" + stateProvinceS).getBytes(UTF_8)));
			} else if (!rows.isNull(localityCodeId)) {
				localityCodeS = escape(rows.getString(localityCodeId));
				locIri = "nl:" + HexFormat.of().formatHex(md.digest((cc + "lc" + localityCodeS).getBytes(UTF_8)));
			} else {
				locIri = "nl:" + cc;
			}
			bufferUse = add(buffer, locIri.getBytes(UTF_8), fos, bufferUse);
			bufferUse = add(buffer, END_TRIPLE_BLOCK, fos, bufferUse);
			bufferUse = add(buffer, locIri.getBytes(UTF_8), fos, bufferUse);
			bufferUse = add(buffer, " a dwc:Location\n ".getBytes(UTF_8), fos, bufferUse);
			bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, countryCode, countryCodeId, true);
			if (stateProvinceS != null) {
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, stateProvince, stateProvinceId, true);
			}
			if (localityCodeS != null) {
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, localityCode, localityCodeId, true);
			}
			bufferUse = add(buffer, END_TRIPLE_BLOCK, fos, bufferUse);
		}

		return bufferUse;
	}

	private static String escape(String string) {
		return string.replace("\\", "\\\\");
	}

	private static int addTaxon(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, int taxonkeyId,
			int speciesId, MutableRoaringBitmap seenTaxons, Map<KnownColumns, Integer> knownColumnsMap, boolean taxonIsInt)
			throws IOException {
		String taxon = null;
		String species = null;
		if (taxonkeyId < 0 || !rows.isNull(taxonkeyId)) {
			if (taxonIsInt)
				taxon = Integer.toString(rows.getInt(taxonkeyId));
			else
				taxon = rows.getString(taxonkeyId);
			bufferUse = add(buffer, PREB, fos, bufferUse);
			bufferUse = add(buffer, toTaxon, fos, bufferUse);
			bufferUse = add(buffer, taxon.getBytes(UTF_8), fos, bufferUse);
		}
		if (speciesId < 0 || !rows.isNull(speciesId)) {
			if (taxonIsInt)
				species = Integer.toString(rows.getInt(speciesId));
			else
				species = rows.getString(speciesId);
			if (species != null && !species.equals(taxon)) {
				bufferUse = add(buffer, PREB, fos, bufferUse);
				bufferUse = add(buffer, toTaxon, fos, bufferUse);
				bufferUse = add(buffer, species.getBytes(UTF_8), fos, bufferUse);
			}
		}
		bufferUse = add(buffer, END_TRIPLE_BLOCK, fos, bufferUse);
		bufferUse = addTaxon(rows, fos, buffer, bufferUse, seenTaxons, taxon, null, knownColumnsMap);
		if (species != null && !species.equals(taxon)) {
			bufferUse = addTaxon(rows, fos, buffer, bufferUse, seenTaxons, species, taxon, knownColumnsMap);
		}
		return bufferUse;
	}

	private static int addTaxon(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse,
			MutableRoaringBitmap seenTaxons, String taxon, String taxa, Map<KnownColumns, Integer> knownColumnsMap)
			throws IOException {
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
						getColumnId(knownColumnsMap, KnownColumns.verbatimscientificnameauthorship), true);
				if (taxa != null) {
					bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, species,
							getColumnId(knownColumnsMap, KnownColumns.species), true);
					bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, infraspecificepithet,
							getColumnId(knownColumnsMap, KnownColumns.infraspecificepithet), true);
					bufferUse = add(buffer, PREB, fos, bufferUse);
					bufferUse = add(buffer, subclassof, fos, bufferUse);
					bufferUse = add(buffer, gbifsp, fos, bufferUse);
				}
				bufferUse = add(buffer, END_TRIPLE_BLOCK, fos, bufferUse);
				return bufferUse;
			}
		}
		return bufferUse;
	}

	private static int addLicense(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, int columnId)
			throws IOException {
		if (columnId < 0 || rows.isNull(columnId)) {
			return bufferUse;
		} else {
			bufferUse = add(buffer, PREB, fos, bufferUse);
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

	private static int getColumnId(Map<KnownColumns, Integer> knownColumnsMap, KnownColumns kc) {
		return knownColumnsMap.getOrDefault(kc, -404);
	}

	private static int andRecordData(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse,
			int basisOfRecordId, int institutioncodeId, int collectioncodeId, int catalognumberId, int recordnumberId,
			int identifiedbyId, int dateidentifiedId, int rightsholderId, int recordedbyId, int typestatusId,
			int establishmentmeansId, int lastinterpretedId, int mediatypeId, int issueId) throws IOException {
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, basisOfRecord, basisOfRecordId, false);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, institutioncode, institutioncodeId, true);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, collectioncode, collectioncodeId, true);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, catalognumber, catalognumberId, true);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, recordnumber, recordnumberId, true);
		bufferUse = addAsLiteralStrings(rows, fos, buffer, bufferUse, identifiedby,
				KnownColumns.identifiedby.columnName(), true);
		bufferUse = addAsDatatypeString(rows, fos, buffer, bufferUse, dateidentified, dateidentifiedId, XSD_DATE,
				(s) -> fromTimestampToXsdDate(s, dateidentifiedId));
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, rightsholder, rightsholderId, true);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, recordedby, recordedbyId, true);
		bufferUse = addAsLiteralStrings(rows, fos, buffer, bufferUse, typestatus, KnownColumns.typestatus.columnName(),
				true);
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
	
	private static byte[] fromVarCharToXsdDate(StructAccessor s, int eventdateId) {
		return Arrays.copyOf(s.getBinary(eventdateId), 10);
	}

	private static int addDate(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, int eventdateId,
			int dayId, int monthId, int yearId) throws IOException {
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

	

	private static int addAsDatatypeString(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse,
			byte[] predicate, int colId, byte[] datatype, Function<StructAccessor, byte[]> extractor)
			throws IOException {
		if (colId < 0 || rows.isNull(colId)) {
			return bufferUse;
		} else {
			bufferUse = add(buffer, PREB, fos, bufferUse);
			bufferUse = add(buffer, predicate, fos, bufferUse);
			bufferUse = add(buffer, OPEN_LITERAL, fos, bufferUse);
			bufferUse = add(buffer, extractor.apply(rows), fos, bufferUse);
			bufferUse = add(buffer, datatype, fos, bufferUse);
			return bufferUse;
		}
	}

	private static int addCoordinates(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse,
			int decimallatitudeId, int decimalLongitudeId, int coordinateUncertaintyInMetersId, int elevationId,
			int elevationAccuracyId, int depthId, int depthAccuracyId) throws IOException {
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, decimalLatitude, decimallatitudeId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, decimalLongitude, decimalLongitudeId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, coordinateUncertaintyInMeters,
				coordinateUncertaintyInMetersId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, elevation, elevationId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, elevationaccuracy, elevationAccuracyId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, depth, depthId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, depthaccuracy, depthAccuracyId);
		if (!rows.isNull(decimallatitudeId) && !rows.isNull(decimalLongitudeId)) {
			double longitude = rows.getDouble(decimalLongitudeId);
			double latitude = rows.getDouble(decimallatitudeId);
			bufferUse = add(buffer, PREB, fos, bufferUse);
			if (!rows.isNull(coordinateUncertaintyInMetersId)) {
				double uncertaintity = rows.getDouble(decimallatitudeId);
				bufferUse = addCircle(fos, buffer, bufferUse, longitude, latitude, uncertaintity);
			} else {
				bufferUse = addPoint(fos, buffer, bufferUse, longitude, latitude);
			}

		}
		return bufferUse;
	}

	private static int addCircle(OutputStream fos, byte[] buffer, int bufferUse, double longitude, double latitude,
			double uncertaintity) throws IOException {
		bufferUse = add(buffer, POLYGON, fos, bufferUse);
		GeometricShapeFactory shapeFactory = new GeometricShapeFactory();
	    shapeFactory.setNumPoints(32);
	    shapeFactory.setCentre(new Coordinate(longitude, latitude));
	    shapeFactory.setSize(uncertaintity * 2);
	    Polygon circle = shapeFactory.createCircle();
		Coordinate[] coordinates = circle.getCoordinates();
		for (int i = 0; i < coordinates.length; i++) {
			Coordinate coordinate = coordinates[i];
			bufferUse = add(buffer, Double.toString(coordinate.x).getBytes(UTF_8), fos, bufferUse);
			bufferUse = add(buffer, SPACE, fos, bufferUse);
			bufferUse = add(buffer, Double.toString(coordinate.y).getBytes(UTF_8), fos, bufferUse);
			if (i < coordinates.length - 1) {
				bufferUse = add(buffer, COMMA, fos, bufferUse);
			}
		}
		bufferUse = add(buffer, CLOSE_POLYGON, fos, bufferUse);
		return bufferUse;
	}

	private static int addPoint(OutputStream fos, byte[] buffer, int bufferUse, double longitude, double latitude)
			throws IOException {
		bufferUse = add(buffer, POINT, fos, bufferUse);
		bufferUse = add(buffer, Double.toString(longitude).getBytes(UTF_8), fos,
				bufferUse);
		bufferUse = add(buffer, SPACE, fos, bufferUse);
		bufferUse = add(buffer, Double.toString(latitude).getBytes(UTF_8), fos, bufferUse);
		bufferUse = add(buffer, CLOSE_POINT, fos, bufferUse);
		return bufferUse;
	}

	private static int addAsRawString(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, byte[] predicate,
			int colId) throws IOException {
		if (rows.isNull(colId)) {
			return bufferUse;
		} else {
			bufferUse = add(buffer, PREB, fos, bufferUse);
			bufferUse = add(buffer, predicate, fos, bufferUse);
			return add(buffer, rows.getString(colId).getBytes(UTF_8), fos, bufferUse);
		}
	}

	private static int addAsLiteralString(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse,
			byte[] predicate, int colId, boolean escape) throws IOException {
		if (colId < 0 || rows.isNull(colId)) {
			return bufferUse;
		} else {
			bufferUse = add(buffer, PREB, fos, bufferUse);
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

	private static int addAsLiteralStrings(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse,
			byte[] predicate, String colId, boolean escape) throws IOException {
		PqList list = rows.getList(colId);
		if (list != null && !list.isEmpty()) {
			bufferUse = add(buffer, PREB, fos, bufferUse);
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

	private static String escapeQuotes(String li) {
		li = li.replace("\\", "\\\\").replace("\"", "\\\"");
		return li;
	}

	private static int addAsDouble(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, byte[] predicate,
			int colId) throws IOException {
		if (colId < 0 || rows.isNull(colId)) {
			return bufferUse;
		} else {
			bufferUse = add(buffer, PREB, fos, bufferUse);
			bufferUse = add(buffer, predicate, fos, bufferUse);
			bufferUse = add(buffer, OPEN_LITERAL, fos, bufferUse);
			bufferUse = add(buffer, Double.toString(rows.getDouble(colId)).getBytes(UTF_8), fos, bufferUse);
			return closeDouble(buffer, fos, bufferUse);
		}
	}

	private static int addGbifId(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, int gbifColumnId, boolean gbifidIsLong)
			throws IOException {
		bufferUse = add(buffer, gbif, fos, bufferUse);

		byte[] gbifid;
		if (gbifidIsLong) {
			gbifid = Long.toString(rows.getLong(gbifColumnId)).getBytes(UTF_8);
		} else {
			gbifid = rows.getBinary(gbifColumnId);
		}
		bufferUse = add(buffer, gbifid, fos, bufferUse);

		bufferUse = add(buffer, isOccurence, fos, bufferUse);
		bufferUse = add(buffer, OPEN_LITERAL, fos, bufferUse);
		bufferUse = add(buffer, gbifid, fos, bufferUse);
		bufferUse = closeLiteral(buffer, fos, bufferUse);
		return bufferUse;
	}

	private static int closeLiteral(byte[] buffer, OutputStream fos, int bufferUse) throws IOException {
		return add(buffer, CLOSE_LITERAL, fos, bufferUse);
	}

	private static int closeDouble(byte[] buffer, OutputStream fos, int bufferUse) throws IOException {
		return add(buffer, closeDoubleLiteral, fos, bufferUse);
	}

	private static int addAsInteger(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, byte[] predicate,
			int individualCountId) throws IOException {
		if (!rows.isNull(individualCountId)) {
			bufferUse = add(buffer, PREB, fos, bufferUse);
			bufferUse = add(buffer, predicate, fos, bufferUse);
			int int1 = rows.getInt(individualCountId);
			bufferUse = add(buffer, int1, fos, bufferUse);
		}
		return bufferUse;
	}

	private static int add(byte[] buffer, int intToAdd, OutputStream fos, int bufferUse) throws IOException {
		byte[] toAdd = Integer.toString(intToAdd).getBytes(UTF_8);
		return add(buffer, toAdd, fos, bufferUse);
	}

	private static int add(byte[] buffer, byte[] toAdd, OutputStream fos, int bufferUse) throws IOException {
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
}

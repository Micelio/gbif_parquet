package io.github.jervenbolleman.gbif.parquet.rdf;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
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

public record RowToTurtle(int gbifColumnId, int occurenceStatusColId, int individualCountColId,
		int publishingorgkeyColId, int countryCodeColId, int decimallatitudeColId, int decimalLongitudeColId,
		int coordinateUncertaintyInMetersColId, int elevationColId, int elevationAccuracyColId, int depthColId,
		int depthAccuracyColId, int eventdateColId, int dayColId, int monthColId, int yearColId, int basisOfRecordColId,
		int institutioncodeColId, int collectioncodeColId, int catalognumberColId, int recordnumberColId,
		int identifiedbyColId, int dateidentifiedColId, int rightsholderColId, int recordedbyColId, int typestatusColId,
		int establishmentmeansColId, int lastinterpretedColId, int mediatypeColId, int issueColId, int taxonkeyColId,
		int speciesKeyColId, int licenseColId, int stateProvinceColId, int localityCodeColId, int kingdomColId,
		int phylumColId, int clazzColId, int orderColId, int familyColId, int genusColId, int taxonrankColId,
		int verbatimscientificnameauthorshipColId, int speciesNameColId, int infraspecificepithetColId) {
	private static final byte[] OPEN_LITERAL = "\"".getBytes(UTF_8);
	private static final byte[] COMMA = ", ".getBytes(UTF_8);
	private static final byte[] XSD_DATE = "\"^^xsd:date".getBytes(UTF_8);
	private static final byte[] XSD_GDAY = "\"^^xsd:gDay".getBytes(UTF_8);
	private static final byte[] XSD_GMONTH = "\"^^xsd:gMonth".getBytes(UTF_8);
	private static final byte[] XSD_GYEAR = "\"^^xsd:gYear".getBytes(UTF_8);

	private static final byte[] POINT = "wdt:P625 \"Point(".getBytes(UTF_8);
	private static final byte[] GEOMETRY_POLYGON = "geo:hasGeometry [ geo:asWKT \"POLYGON((".getBytes(UTF_8);
	private static final byte[] SPACE = " ".getBytes(UTF_8);
	private static final byte[] CLOSE_POINT = ")\"^^geo:wktLiteral ".getBytes(UTF_8);
	private static final byte[] CLOSE_GEOMETRY_POLYGON = "))\"^^geo:wktLiteral ] ".getBytes(UTF_8);
	private static final byte[] END_TRIPLE_BLOCK = " .\n".getBytes(UTF_8);
	private static final byte[] CLOSE_LITERAL = "\"".getBytes(UTF_8);
	private static final String PRE = ";\n  ";
	private static final byte[] PREB = PRE.getBytes(UTF_8);
	private static final byte[] subclassof = ("rdfs:subClassOf ").getBytes(UTF_8);
	private static final byte[] countryCode = ("dwc:countryCode ").getBytes(UTF_8);
	private static final byte[] closeDoubleLiteral = "\"^^xsd:double ".getBytes(UTF_8);
	private static final byte[] gbifocc = "gbifocc:".getBytes(UTF_8);
	private static final byte[] gbifsp = "gbifsp:".getBytes(UTF_8);
	private static final byte[] isOccurrence = (" a dwc:Occurrence " + PRE + "gbifterm:gbifID ").getBytes(UTF_8);
	private static final byte[] occurrenceStatus = ("dwc:occurrenceStatus ").getBytes(UTF_8);
	private static final byte[] individualCount = ("dwc:individualCount ").getBytes(UTF_8);
	private static final byte[] publishingOrgKey = ("dwc:publishingOrgKey gbifpub:").getBytes(UTF_8);
	private static final byte[] decimalLatitude = ("dwc:decimalLatitude ").getBytes(UTF_8);
	private static final byte[] decimalLongitude = ("dwc:decimalLongitude ").getBytes(UTF_8);
	private static final byte[] coordinateUncertaintyInMeters = ("dwc:coordinateUncertaintyInMeters ").getBytes(UTF_8);
	private static final byte[] elevation = ("dwc:elevation ").getBytes(UTF_8);
	private static final byte[] elevationaccuracy = ("dwc:elevationAccuracy ").getBytes(UTF_8);
	private static final byte[] depth = ("dwc:depth ").getBytes(UTF_8);
	private static final byte[] depthaccuracy = ("dwc:depthAccuracy ").getBytes(UTF_8);

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
	private static final byte[] localityLabel = ("rdfs:label ").getBytes(UTF_8);
	private static final byte[] STRING_DELIM = "\"".getBytes(UTF_8);
	private static final byte[] verbatimscientificnameauthorship = (" dwc:verbatimScientificNameAuthorship ")
			.getBytes(UTF_8);

	private static final byte[] CC_BY_4_0 = "ccby4: ".getBytes(UTF_8);

	private static final byte[] CC_BY_NC_4_0 = "ccby4nc: ".getBytes(UTF_8);
	private static final byte[] CC0_1_0 = "cc0: ".getBytes(UTF_8);
	private static final int BUFFER_SIZE = 16 * 8096;

	public RowToTurtle(RowReader rows, Map<KnownColumns, Integer> knownColumnsMap) {
		this(getColumnId(knownColumnsMap, KnownColumns.gbifid),
				getColumnId(knownColumnsMap, KnownColumns.occurrencestatus),
				getColumnId(knownColumnsMap, KnownColumns.individualcount),
				getColumnId(knownColumnsMap, KnownColumns.publishingorgkey),
				getColumnId(knownColumnsMap, KnownColumns.countrycode),
				getColumnId(knownColumnsMap, KnownColumns.decimallatitude),
				getColumnId(knownColumnsMap, KnownColumns.decimallongitude),
				getColumnId(knownColumnsMap, KnownColumns.coordinateuncertaintyinmeters),
				getColumnId(knownColumnsMap, KnownColumns.elevation),
				getColumnId(knownColumnsMap, KnownColumns.elevationaccuracy),
				getColumnId(knownColumnsMap, KnownColumns.depth),
				getColumnId(knownColumnsMap, KnownColumns.depthaccuracy),
				getColumnId(knownColumnsMap, KnownColumns.eventdate), getColumnId(knownColumnsMap, KnownColumns.day),
				getColumnId(knownColumnsMap, KnownColumns.month), getColumnId(knownColumnsMap, KnownColumns.year),
				getColumnId(knownColumnsMap, KnownColumns.basisofrecord),
				getColumnId(knownColumnsMap, KnownColumns.institutioncode),
				getColumnId(knownColumnsMap, KnownColumns.collectioncode),
				getColumnId(knownColumnsMap, KnownColumns.catalognumber),
				getColumnId(knownColumnsMap, KnownColumns.recordnumber),
				getColumnId(knownColumnsMap, KnownColumns.identifiedby),
				getColumnId(knownColumnsMap, KnownColumns.dateidentified),
				getColumnId(knownColumnsMap, KnownColumns.rightsholder),
				getColumnId(knownColumnsMap, KnownColumns.recordedby),
				getColumnId(knownColumnsMap, KnownColumns.typestatus),
				getColumnId(knownColumnsMap, KnownColumns.establishmentmeans),
				getColumnId(knownColumnsMap, KnownColumns.lastinterpreted),
				getColumnId(knownColumnsMap, KnownColumns.mediatype), getColumnId(knownColumnsMap, KnownColumns.issue),
				getColumnId(knownColumnsMap, KnownColumns.taxonkey),
				getColumnId(knownColumnsMap, KnownColumns.specieskey),
				getColumnId(knownColumnsMap, KnownColumns.license),
				getColumnId(knownColumnsMap, KnownColumns.stateprovince),
				getColumnId(knownColumnsMap, KnownColumns.locality), getColumnId(knownColumnsMap, KnownColumns.kingdom),
				getColumnId(knownColumnsMap, KnownColumns.phylum), getColumnId(knownColumnsMap, KnownColumns.clazz),
				getColumnId(knownColumnsMap, KnownColumns.order), getColumnId(knownColumnsMap, KnownColumns.family),
				getColumnId(knownColumnsMap, KnownColumns.genus), getColumnId(knownColumnsMap, KnownColumns.taxonrank),
				getColumnId(knownColumnsMap, KnownColumns.verbatimscientificnameauthorship),
				getColumnId(knownColumnsMap, KnownColumns.species),
				getColumnId(knownColumnsMap, KnownColumns.infraspecificepithet));
	}

	void convertRows(RowReader rows, OutputStream fos, boolean taxonIsInt, boolean gbifidIsLong)
			throws IOException, NoSuchAlgorithmException {
		byte[] buffer = new byte[BUFFER_SIZE];
		int bufferUse = 0;
		MutableRoaringBitmap seenTaxons = new MutableRoaringBitmap();
		
		Digester digester = new Digester();
		while (rows.hasNext()) {
			rows.next();
			bufferUse = addGbifId(rows, fos, buffer, bufferUse, gbifidIsLong);

			bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, occurrenceStatus, occurenceStatusColId, false);
			bufferUse = addAsInteger(rows, fos, buffer, bufferUse, individualCount, individualCountColId);
			bufferUse = addAsRawString(rows, fos, buffer, bufferUse, publishingOrgKey, publishingorgkeyColId);
			bufferUse = addCoordinates(rows, fos, buffer, bufferUse);
			bufferUse = addDate(rows, fos, buffer, bufferUse);
			bufferUse = andRecordData(rows, fos, buffer, bufferUse);
			bufferUse = addLicense(rows, fos, buffer, bufferUse);
			bufferUse = addTaxon(rows, fos, buffer, bufferUse, seenTaxons, taxonIsInt);
			bufferUse = addLocation(rows, fos, buffer, bufferUse, digester);
		}
		fos.write(buffer, 0, bufferUse);
	}

	private int addLocation(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, Digester digester) throws IOException {
		if (hasColumn(rows, countryCodeColId)) {
			String cc = rows.getString(countryCodeColId);
			bufferUse = add(buffer, gbifocc, fos, bufferUse);
			byte[] gbifid = rows.getString(gbifColumnId).getBytes(UTF_8);
			bufferUse = add(buffer, gbifid, fos, bufferUse);
			bufferUse = add(buffer, inDescribedPlace, fos, bufferUse);
			String stateProvinceS = null;
			String localityCodeS = null;
			byte[] locIri;
			if (hasColumn(rows, stateProvinceColId) && hasColumn(rows, localityCodeColId)) {
				stateProvinceS = escape(rows.getString(stateProvinceColId));
				localityCodeS = escape(rows.getString(localityCodeColId));
				byte[] loc = (cc + "-sp-" + stateProvinceS + "lc" + localityCodeS).getBytes(UTF_8);
				locIri = digester.digest(loc);
			} else if (!rows.isNull(stateProvinceColId)) {
				stateProvinceS = escape(rows.getString(stateProvinceColId));
				locIri = digester.digest((cc + "-sp-" + stateProvinceS).getBytes(UTF_8));
			} else if (!rows.isNull(localityCodeColId)) {
				localityCodeS = escape(rows.getString(localityCodeColId));
				locIri = digester.digest((cc + "lc" + localityCodeS).getBytes(UTF_8));
			} else {
				locIri = ("nl:" + cc).getBytes(UTF_8);
			}
			bufferUse = add(buffer, locIri, fos, bufferUse);
			bufferUse = add(buffer, END_TRIPLE_BLOCK, fos, bufferUse);
			bufferUse = add(buffer, locIri, fos, bufferUse);
			bufferUse = add(buffer, " a dwc:Location\n ".getBytes(UTF_8), fos, bufferUse);
			bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, countryCode, countryCodeColId, true);
			if (stateProvinceS != null) {
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, stateProvince, stateProvinceColId, true);
			}
			if (localityCodeS != null) {
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, localityLabel, localityCodeColId, true);
			}
			bufferUse = add(buffer, END_TRIPLE_BLOCK, fos, bufferUse);
		}
		return bufferUse;
	}

	

	static String escape(String string) {
		return string.replace("\\", "\\\\");
	}

	private int addTaxon(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse,
			MutableRoaringBitmap seenTaxons, boolean taxonIsInt) throws IOException {
		String taxon = null;
		String species = null;
		if (hasColumn(rows, taxonkeyColId)) {
			if (taxonIsInt)
				taxon = Integer.toString(rows.getInt(taxonkeyColId));
			else
				taxon = rows.getString(taxonkeyColId);
			bufferUse = add(buffer, PREB, fos, bufferUse);
			bufferUse = add(buffer, toTaxon, fos, bufferUse);
			bufferUse = add(buffer, taxon.getBytes(UTF_8), fos, bufferUse);
		}
		if (hasColumn(rows, speciesKeyColId)) {
			if (taxonIsInt)
				species = Integer.toString(rows.getInt(speciesKeyColId));
			else
				species = rows.getString(speciesKeyColId);
			if (species != null && !species.equals(taxon)) {
				bufferUse = add(buffer, PREB, fos, bufferUse);
				bufferUse = add(buffer, toTaxon, fos, bufferUse);
				bufferUse = add(buffer, species.getBytes(UTF_8), fos, bufferUse);
			}
		}
		bufferUse = add(buffer, END_TRIPLE_BLOCK, fos, bufferUse);
		bufferUse = addTaxon(rows, fos, buffer, bufferUse, seenTaxons, taxon, null);
		if (species != null && !species.equals(taxon)) {
			bufferUse = addTaxon(rows, fos, buffer, bufferUse, seenTaxons, species, taxon);
		}
		return bufferUse;
	}

	private static boolean hasColumn(RowReader rows, int colId) {
		return colId < 0 || !rows.isNull(colId);
	}

	private int addTaxon(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse,
			MutableRoaringBitmap seenTaxons, String taxon, String taxa) throws IOException {
		if (taxon != null) {
			int taxonInt = Integer.parseInt(taxon);
			if (seenTaxons.checkedAdd(taxonInt)) {
				bufferUse = add(buffer, gbifsp, fos, bufferUse);
				bufferUse = add(buffer, taxon.getBytes(UTF_8), fos, bufferUse);
				bufferUse = add(buffer, " a dwc:Taxon ".getBytes(), fos, bufferUse);

				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, kingdom, kingdomColId, false);
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, phylum, phylumColId, false);
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, clazz, clazzColId, false);
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, order, orderColId, false);
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, family, familyColId, false);
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, genus, genusColId, false);
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, taxonrank, taxonrankColId, false);
				bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, verbatimscientificnameauthorship,
						verbatimscientificnameauthorshipColId, true);
				if (taxa != null) {
					bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, species, speciesNameColId, true);
					bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, infraspecificepithet,
							infraspecificepithetColId, true);
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

	private int addLicense(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse) throws IOException {
		if (hasColumn(rows, licenseColId)) {
			return bufferUse;
		} else {
			bufferUse = add(buffer, PREB, fos, bufferUse);
			bufferUse = add(buffer, license, fos, bufferUse);
			switch (rows.getString(licenseColId)) {
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
				throw new IOException("Unknown license: " + rows.getString(licenseColId));
			}
			return bufferUse;
		}
	}

	private static int getColumnId(Map<KnownColumns, Integer> knownColumnsMap, KnownColumns kc) {
		return knownColumnsMap.getOrDefault(kc, -404);
	}

	private int andRecordData(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse) throws IOException {
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, basisOfRecord, basisOfRecordColId, false);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, institutioncode, institutioncodeColId, true);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, collectioncode, collectioncodeColId, true);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, catalognumber, catalognumberColId, true);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, recordnumber, recordnumberColId, true);
		bufferUse = addAsLiteralStrings(rows, fos, buffer, bufferUse, identifiedby, identifiedbyColId, true);
		bufferUse = addAsDatatypeString(rows, fos, buffer, bufferUse, dateidentified, dateidentifiedColId, XSD_DATE,
				(s) -> fromTimestampToXsdDate(s, dateidentifiedColId));
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, rightsholder, rightsholderColId, true);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, recordedby, recordedbyColId, true);
		bufferUse = addAsLiteralStrings(rows, fos, buffer, bufferUse, typestatus, typestatusColId, true);
		bufferUse = addAsLiteralString(rows, fos, buffer, bufferUse, establishmentmeans, establishmentmeansColId,
				false);
		bufferUse = addAsDatatypeString(rows, fos, buffer, bufferUse, lastinterpreted, lastinterpretedColId, XSD_DATE,
				(s) -> fromTimestampToXsdDate(s, lastinterpretedColId));
		bufferUse = addAsLiteralStrings(rows, fos, buffer, bufferUse, mediatype, mediatypeColId, false);
		bufferUse = addAsLiteralStrings(rows, fos, buffer, bufferUse, issue, issueColId, true);
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

	private int addDate(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse) throws IOException {
		bufferUse = addAsDatatypeString(rows, fos, buffer, bufferUse, eventDate, eventdateColId, XSD_DATE,
				(s) -> fromTimestampToXsdDate(s, eventdateColId));
		bufferUse = addAsDatatypeString(rows, fos, buffer, bufferUse, day, dayColId, XSD_GDAY, (s) -> intToGday(dayColId, s));
		bufferUse = addAsDatatypeString(rows, fos, buffer, bufferUse, month, monthColId, XSD_GMONTH,
				(s) -> intToGMonth(monthColId, s));
		bufferUse = addAsDatatypeString(rows, fos, buffer, bufferUse, year, yearColId, XSD_GYEAR, (s) -> 
			Integer.toString(s.getInt(yearColId)).getBytes(UTF_8)
		);
		return bufferUse;
	}

	private static byte[] intToGMonth(int monthId, StructAccessor s) {
		int monthInt = s.getInt(monthId);
		if (monthInt < 10) {
			return ("--0" + monthInt).getBytes(UTF_8);
		} else {
			return ("--" + monthInt).getBytes(UTF_8);
		}
	}

	private static byte[] intToGday(int dayId, StructAccessor s) {
		int dc = s.getInt(dayId);
		if (dc < 10) {
			return ("---0" + dc).getBytes(UTF_8);
		} else {
			return ("---" + dc).getBytes(UTF_8);
		}
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

	private int addCoordinates(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse) throws IOException {
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, decimalLatitude, decimallatitudeColId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, decimalLongitude, decimalLongitudeColId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, coordinateUncertaintyInMeters,
				coordinateUncertaintyInMetersColId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, elevation, elevationColId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, elevationaccuracy, elevationAccuracyColId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, depth, depthColId);
		bufferUse = addAsDouble(rows, fos, buffer, bufferUse, depthaccuracy, depthAccuracyColId);
		if (!rows.isNull(decimallatitudeColId) && !rows.isNull(decimalLongitudeColId)) {
			double longitude = rows.getDouble(decimalLongitudeColId);
			double latitude = rows.getDouble(decimallatitudeColId);
			// wdt:P625 is ALWAYS just the point, consistent with Wikidata, where a
			// location is always a point and never a more complex geometry.
			bufferUse = add(buffer, PREB, fos, bufferUse);
			bufferUse = addPoint(fos, buffer, bufferUse, longitude, latitude);
			// When a coordinate uncertainty is given, additionally express it as a
			// GeoSPARQL geometry: a circle of that radius, attached via
			// geo:hasGeometry/geo:asWKT (blank node). Radius in DEGREES (~111320 m
			// per degree). The circle is emitted only when it stays fully within the
			// valid WGS84 range; an uncertainty so large that the circle would leave
			// [-90,90] x [-180,180] is not drawn (such a continent/global-scale
			// polygon is meaningless and would produce out-of-range coordinates) --
			// the point and dwc:coordinateUncertaintyInMeters still record the
			// location and how uncertain it is.
			double radius = coordinateUncertaintyInMetersColId < 0 || rows.isNull(coordinateUncertaintyInMetersColId)
					? 0.0
					: rows.getDouble(coordinateUncertaintyInMetersColId) / 111320.0;
			if (radius > 0.0 && latitude + radius <= 90.0 && latitude - radius >= -90.0 && longitude + radius <= 180.0
					&& longitude - radius >= -180.0) {
				bufferUse = add(buffer, PREB, fos, bufferUse);
				bufferUse = addCircle(fos, buffer, bufferUse, longitude, latitude, radius);
			}
		}
		return bufferUse;
	}

	private static int addCircle(OutputStream fos, byte[] buffer, int bufferUse, double longitude, double latitude,
			double uncertaintity) throws IOException {
		bufferUse = add(buffer, GEOMETRY_POLYGON, fos, bufferUse);
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
		bufferUse = add(buffer, CLOSE_GEOMETRY_POLYGON, fos, bufferUse);
		return bufferUse;
	}

	private static int addPoint(OutputStream fos, byte[] buffer, int bufferUse, double longitude, double latitude)
			throws IOException {
		bufferUse = add(buffer, POINT, fos, bufferUse);
		bufferUse = add(buffer, Double.toString(longitude).getBytes(UTF_8), fos, bufferUse);
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
		if (hasColumn(rows, colId)) {
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
			byte[] predicate, int colId, boolean escape) throws IOException {
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

	private int addGbifId(RowReader rows, OutputStream fos, byte[] buffer, int bufferUse, boolean gbifidIsLong)
			throws IOException {
		bufferUse = add(buffer, gbifocc, fos, bufferUse);

		byte[] gbifid;
		if (gbifidIsLong) {
			gbifid = Long.toString(rows.getLong(gbifColumnId)).getBytes(UTF_8);
		} else {
			gbifid = rows.getBinary(gbifColumnId);
		}
		bufferUse = add(buffer, gbifid, fos, bufferUse);

		bufferUse = add(buffer, isOccurrence, fos, bufferUse);
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

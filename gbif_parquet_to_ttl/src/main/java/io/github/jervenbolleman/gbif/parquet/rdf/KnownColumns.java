package io.github.jervenbolleman.gbif.parquet.rdf;

enum KnownColumns {
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
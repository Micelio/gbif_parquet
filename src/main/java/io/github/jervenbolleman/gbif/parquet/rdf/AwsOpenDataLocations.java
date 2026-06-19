package io.github.jervenbolleman.gbif.parquet.rdf;

public enum AwsOpenDataLocations {
	AF_SOUTH_1("af-south-1"), AP_SOUTHEAST_2("ap-southeast-2"), EU_CENTRAL_1("eu-central-1"), SA_EAST_1("sa-east-1"),
	US_EAST_1("us-east-1");

	private final String location;

	AwsOpenDataLocations(String location) {
		this.location = location;
	}

	public String getLocation() {
		return location;
	}
	
	public String asHttpPrefix() {
		return "https://gbif-open-data-" + location + ".s3.amazonaws.com/occurrence/";
	}
}

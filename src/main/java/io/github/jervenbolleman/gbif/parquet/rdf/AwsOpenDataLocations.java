package io.github.jervenbolleman.gbif.parquet.rdf;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;


public enum AwsOpenDataLocations {

	AF_SOUTH_1("af-south-1"), AP_SOUTHEAST_2("ap-southeast-2"), EU_CENTRAL_1("eu-central-1"), SA_EAST_1("sa-east-1"),
	US_EAST_1("us-east-1");

	private static final System.Logger log = System.getLogger(AwsOpenDataLocations.class.getName());
	private final String location;

	AwsOpenDataLocations(String location) {
		this.location = location;
	}

	public String getLocation() {
		return location;
	}

	public String asHttpPrefix() {
		return "https://gbif-open-data-" + location + ".s3.amazonaws.com/";
	}

	static AwsOpenDataLocations findClosestS3Location() throws URISyntaxException, IOException, InterruptedException {
		AwsOpenDataLocations closestLocation = US_EAST_1; // default to US_EAST_1 if all fail
		Map<AwsOpenDataLocations, Long> timeToFetch = new EnumMap<>(AwsOpenDataLocations.class);
		try (HttpClient client = HttpClient.newBuilder().build()) {
	
			for (AwsOpenDataLocations location : values()) {
				Instant start = Instant.now();
				HttpRequest hr = HttpRequest.newBuilder(new URI(location.asHttpPrefix()+"index.html#"))
						.HEAD().build();
				HttpResponse<Void> send = client.send(hr, BodyHandlers.discarding());
				Instant end = Instant.now();
				if (send.statusCode() == 200) {
					timeToFetch.put(location, Duration.between(start, end).getSeconds());
				} else {
					log.log(Level.INFO, "location"+ location.asHttpPrefix() + " returned status code " + send.statusCode());
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
}

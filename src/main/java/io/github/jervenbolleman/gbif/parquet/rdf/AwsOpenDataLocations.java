package io.github.jervenbolleman.gbif.parquet.rdf;

import static java.net.URLEncoder.encode;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

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
				HttpRequest hr = HttpRequest.newBuilder(new URI(location.asHttpPrefix() + "index.html#")).HEAD()
						.build();
				HttpResponse<Void> send = client.send(hr, BodyHandlers.discarding());
				Instant end = Instant.now();
				if (send.statusCode() == 200) {
					timeToFetch.put(location, Duration.between(start, end).getSeconds());
				} else {
					log.log(Level.INFO,
							"location " + location.asHttpPrefix() + " returned status code " + send.statusCode());
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

	List<String> list(String year, String month) throws IOException, InterruptedException {
		List<String> files = new ArrayList<>();
		try (HttpClient client = HttpClient.newBuilder().build()) {
			String continuationToken = "";

			while (continuationToken != null) {
				continuationToken = listRequest(year, month, files, client, continuationToken);
			}

		} catch (URISyntaxException e) {
			log.log(Level.ERROR, e);
		} catch (ParserConfigurationException e) {
			throw new IOException("Error configuring XML parser", e);
		} catch (SAXException e) {
			throw new IOException("Error XML parsing", e);
		}
		return files;
	}

	private String listRequest(String year, String month, List<String> files, HttpClient client,
			String continuationToken)
			throws URISyntaxException, IOException, InterruptedException, ParserConfigurationException, SAXException {
		String formParams = "?list-type=2&delimiter=" + encode("/", UTF_8) + "&prefix="
				+ encode("occurrence/" + year + "-" + month + "-01/occurrence.parquet/", UTF_8);
		if (continuationToken != null && !continuationToken.isEmpty()) {
			formParams += "&continuation-token=" + encode(continuationToken, UTF_8);
		}
		HttpRequest hr = HttpRequest.newBuilder(new URI(asHttpPrefix() + formParams)).GET().build();
		HttpResponse<String> send = client.send(hr, BodyHandlers.ofString());
		if (send.statusCode() == 200) {
			return parseDocument(send.body(), files);
		} else {
			log.log(Level.INFO, "location " + asHttpPrefix() + " returned status code " + send.statusCode());
			return null;
		}
	}

	Stream<Path> download(List<String> files, String year, String month) throws IOException, InterruptedException {
		Path ym = Paths.get("./" + year + '/' + month);
		Files.createDirectories(ym);
		HttpClient client = HttpClient.newHttpClient();

		return files.stream().parallel().map(file -> {
			try {
				return downloadFile(ym, client, file);
			} catch (IOException | InterruptedException e) {
				throw new RuntimeException(e);
			}
		}).onClose(() -> client.close()).sequential();
	}

	private Path downloadFile(Path ym, HttpClient client, String file) throws IOException, InterruptedException {
		Path fp = ym.resolve(Paths.get(file).getFileName());
		log.log(Level.INFO, "Downloading: " + file + " into " + fp);
		long at = 0;
		Path tempFile = Files.createTempFile(ym, "temp", "parquet");
		if (Files.exists(fp)) {
			at = Files.size(fp);
		}
		HttpRequest.Builder rb = HttpRequest.newBuilder().uri(URI.create(asHttpPrefix() + file));
		BodyHandler<Path> handler = BodyHandlers.ofFile(tempFile, StandardOpenOption.APPEND,
				StandardOpenOption.CREATE);
		if (at > 0) {
			rb.header("Range", "bytes=" + at + "-");
		}
		HttpResponse<Path> response = client.send(rb.build(), handler);

		if (response.statusCode() == 200) {
			Files.move(tempFile, fp, StandardCopyOption.REPLACE_EXISTING);
			return fp;
		} else if (response.statusCode() == 416) {
			log.log(Level.INFO, "File already fully downloaded: " + file);
			Files.delete(tempFile);
			return fp;
		} else if (response.statusCode() == 206) {
			log.log(Level.INFO, "File already partly downloaded: " + file);
			try (OutputStream newOutputStream = Files.newOutputStream(fp, StandardOpenOption.APPEND);
					InputStream inputStream = Files.newInputStream(tempFile)) {
				inputStream.transferTo(newOutputStream);
			}
			Files.delete(tempFile);
			return fp;
		} else {
			log.log(Level.ERROR, "Failed to download: " + file + " with status code: " + response.statusCode());
			return null;
		}
	}

	static String parseDocument(String send, List<String> files)
			throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder dBuilder = factory.newDocumentBuilder();
		try (ByteArrayInputStream input = new ByteArrayInputStream(send.getBytes(UTF_8))) {
			Document doc = dBuilder.parse(input);

			doc.getDocumentElement().normalize();
			NodeList keys = doc.getElementsByTagName("Key");
			for (int i = 0; i < keys.getLength(); i++) {
				files.add(keys.item(i).getTextContent());
			}
			NodeList nct = doc.getElementsByTagName("NextContinuationToken");
			if (nct.getLength() > 0) {
				String nextContinuationToken = nct.item(0).getTextContent();
				log.log(Level.INFO, "NextContinuationToken: " + nextContinuationToken);
				return nextContinuationToken;
			} else {
				return null;
			}
		}

	}
}

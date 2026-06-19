# gbif_parquet to rdf/turtle


To first download the parquet files locally (currently from eu-west)"

```bash
 ./download_gbif.sh ${year} ${month}
```

Then convert into ttl.

```
mvn package
java --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -jar target/gbif_parquet-0.3.0-SNAPSHOT-jar-with-dependencies.jar --year 2026 --month 01 --output /dev/stdout
```
Although the two options `--enable-native-access=ALL-UNNAMED` and `--add-modules jdk.incubator.vector` are optional.


# Issues

TODO:

1. Use ping to figure out which AWS location is closest and download/stream from there
2. Scripts assume local relative paths
3. No streaming from S3 directly yet.

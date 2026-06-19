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

1. Scripts assume local relative paths
2. No streaming from S3 directly yet.
3. Occurences snapshots before 2024/02 are not readable (due to schema changes in the parquet files).

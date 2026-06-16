# gbif_parquet to rdf/turtle

To convert a whole GBIF Snapshot into rdf. This will currently stream parquet from aws eu-west.

```bash
/ontop_convert_all.sh ${year} ${month} > big_turtle_file.ttl
```
e.g.
```bash
# validate there are no errors in the generated turtle and count the raw triples
/ontop_convert_all.sh 2026 01 | pv | riot --validate --syntax turtle --count
```

To first download the parquet files locally

```bash
 ./download_gbif.sh ${year} ${month}
```


# Issues

TODO:

1. Mapping is done via rml defined in occurrence-rml.ttl. This is not optimal and help from a gbif/darwin core expert is very welcome
2. GBIFIDs are not unique in the parquet files, can't create unique index to help improve rdf generation speed
3. This is slower than it needs to be (ontop materialize 5.6.0 will improve this a lot)
4. Use ping to figure out which AWS location is closest and download/stream from there
5. Scripts assume local relative paths

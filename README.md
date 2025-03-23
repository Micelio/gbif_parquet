# gbif_parquet
## Extract subset from gbif on aws
`./extract_country.sh 2025 02 SR`

## Run onto
```gbif_parquet % ./ontop endpoint \
  --ontology=occurrence.ttl \
  --mapping=occurrence-mapping.obda \
  --properties=duckdb.properties
```

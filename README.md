# gbif_parquet
## Extract subset from gbif on aws
`./extract_country.sh 2025 02 SR`

## Read in duckdb
```duckdb suriname.duckdb
v1.2.1 8e52ec4395
Enter ".help" for usage hints.
D INSTALL parquet;
D LOAD parquet;
D 
D CREATE TABLE occurrence AS
  SELECT *
  FROM parquet_scan('occurrence_SR_2025_02.parquet');
D .quit
```
## Create ontop mapping in obda format
`vi occurrence-mapping.obda`
`vi duckdb.properties`
`vi occurrence.ttl`

## create duck

## Run onto
```gbif_parquet % ./ontop endpoint \
  --ontology=occurrence.ttl \
  --mapping=occurrence-mapping.obda \
  --properties=duckdb.properties
```

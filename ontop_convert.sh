#!/bin/bash

# === Input Parameters ===
YEAR=$1
MONTH=$2

if [ -z "$YEAR" ] || [ -z "$MONTH" ]; then
  echo "Usage: ./extract_country.sh <year> <month> <country_code>"
  echo "Example: ./extract_country.sh 2025 02"
  exit 1
fi


echo "jdbc.url = jdbc:duckdb:./view_parquet_${YEAR}_${MONTH}.duckdb" > duck_${YEAR}_${MONTH}.properties
echo "jdbc.driver = org.duckdb.DuckDBDriver" >> duck_${YEAR}_${MONTH}.properties
echo "ontop.inferDefaultDatatype=true" >> duck_${YEAR}_${MONTH}.properties

if [ -f conv_${YEAR}_${MONTH}.ttl ]
then
    rm conv_${YEAR}_${MONTH}.ttl
fi

mkfifo conv_${YEAR}_${MONTH}.ttl

ontop materialize  -m  occurrence-rml.ttl  -p duck_${YEAR}_${MONTH}.properties -f turtle -o conv_${YEAR}_${MONTH}.ttl


#!/bin/bash

# === Input Parameters ===
database=$1

if [ -z "$database" ] ; then
  echo "Usage: ./ontop_convert.sh $database"
  exit 1
fi

prop=duckdb_ontop_$database.properties
echo "jdbc.url = jdbc:duckdb:./$database" > $prop
echo "jdbc.driver = org.duckdb.DuckDBDriver" >> $prop
echo "ontop.inferDefaultDatatype=true" >> $prop

ONTOP_LOG_CONFIG=./ontop_logback.xml
export ONTOP_LOG_CONFIG
v=$( ontop --version| cut -f 3 -d ' '|sed 's|\.||g')
if [ $v -gt 560 ]
then
  ontop materialize --allow-duplicates=true -m  occurrence-rml.ttl  -p $prop -f turtle
else
  ontop materialize -m  occurrence-rml.ttl  -p $prop -f turtle
fi


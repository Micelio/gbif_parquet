#!/bin/bash

# === Input Parameters ===
YEAR=$1
MONTH=$2

if [ -z "$YEAR" ] || [ -z "$MONTH" ]; then
  echo "Usage: ./extract_country.sh <year> <month> <country_code>"
  echo "Example: ./extract_country.sh 2025 02"
  exit 1
fi

# === Zero-pad month if needed ===
MONTH=$(printf "%02d" $MONTH)
echo "[$(date "+%Y-%m-%d %H:%M:%S")] Step 1: Listing files in S3 bucket..."
# === Step 1: List files in S3 bucket ===

S3_PATH="//gbif-open-data-af-south-1.s3.af-south-1.amazonaws.com/"
S3_LIST="https:${S3_PATH}?list-type=2&delimiter=/&prefix=occurrence/${YEAR}-${MONTH}-01/occurrence.parquet/"

SQL_FILE="query_${SAFE_COUNTRYCODE}_${YEAR}_${MONTH}.sql"

files=$(curl "${S3_LIST}" | xmllint --xpath '//*[local-name()="Key"]/text()' -)

echo "[$(date "+%Y-%m-%d %H:%M:%S")]  Found ${#files} files in S3."
for file in $files
do
  echo $file
done

# === Step 2: Build DuckDB SQL ===
echo "[$(date "+%Y-%m-%d %H:%M:%S")]  Step 2: Building DuckDB SQL view \"occurence\" in $SQL_FILE."
echo "CREATE OR REPLACE VIEW \"occurence\" AS " > $SQL_FILE
echo "  SELECT gbifid, " >> $SQL_FILE
echo "          datasetkey, " >> $SQL_FILE
echo "          occurrenceid, " >> $SQL_FILE
echo "          kingdom, " >> $SQL_FILE
echo "          phylum, " >> $SQL_FILE
echo "          class, " >> $SQL_FILE
echo "          \"order\", " >> $SQL_FILE
echo "          family, " >> $SQL_FILE
echo "          genus, " >> $SQL_FILE
echo "          species, " >> $SQL_FILE
echo "          infraspecificepithet, " >> $SQL_FILE
echo "          taxonrank, " >> $SQL_FILE
echo "          scientificname, " >> $SQL_FILE
echo "          verbatimscientificname, " >> $SQL_FILE
echo "          verbatimscientificnameauthorship, " >> $SQL_FILE
echo "          countrycode, " >> $SQL_FILE
echo "          locality, " >> $SQL_FILE
echo "          CAST(" >> $SQL_FILE
echo "                  regexp_replace(" >> $SQL_FILE
echo "                      locality, " >> $SQL_FILE
echo "                      '�', " >> $SQL_FILE
echo "                      '', " >> $SQL_FILE
echo "                      'g'" >> $SQL_FILE
echo "                  ) AS VARCHAR" >> $SQL_FILE
echo "              ) AS cleaned_locality, " >> $SQL_FILE
echo "          stateprovince, " >> $SQL_FILE
echo "          occurrencestatus, " >> $SQL_FILE
echo "          individualcount, " >> $SQL_FILE
echo "          publishingorgkey, " >> $SQL_FILE
echo "          'Point('||decimallongitude || ' ' || decimallatitude|| ')' AS point, " >> $SQL_FILE
echo "          decimallongitude, " >> $SQL_FILE
echo "          decimallatitude, " >> $SQL_FILE
echo "          coordinateuncertaintyinmeters, " >> $SQL_FILE
echo "          coordinateprecision, " >> $SQL_FILE
echo "          elevation, " >> $SQL_FILE
echo "          elevationaccuracy, " >> $SQL_FILE
echo "          depth, " >> $SQL_FILE
echo "          depthaccuracy, " >> $SQL_FILE
echo "          eventdate, " >> $SQL_FILE
echo "          day, " >> $SQL_FILE
echo "          month, " >> $SQL_FILE
echo "          year, " >> $SQL_FILE
echo "          taxonkey, " >> $SQL_FILE
echo "          specieskey, " >> $SQL_FILE
echo "          basisofrecord, " >> $SQL_FILE
echo "          institutioncode, " >> $SQL_FILE
echo "          collectioncode, " >> $SQL_FILE
echo "          catalognumber, " >> $SQL_FILE
echo "          recordnumber, " >> $SQL_FILE
echo "          identifiedby, " >> $SQL_FILE
echo "          dateidentified, " >> $SQL_FILE
echo "          license, " >> $SQL_FILE
echo "          rightsholder, " >> $SQL_FILE
echo "          recordedby, " >> $SQL_FILE
echo "          typestatus, " >> $SQL_FILE
echo "          establishmentmeans, " >> $SQL_FILE
echo "          lastinterpreted, " >> $SQL_FILE
echo "          mediatype, " >> $SQL_FILE
echo "          issue, " >> $SQL_FILE
echo "          CONCAT_WS(', '," >> $SQL_FILE
echo "                  CASE WHEN kingdom IS NOT NULL AND kingdom != '' THEN 'k-' || kingdom || '-' END," >> $SQL_FILE
echo "                  CASE WHEN phylum IS NOT NULL AND phylum != '' THEN 'p-' || phylum || '-' END," >> $SQL_FILE
echo "                  CASE WHEN class IS NOT NULL AND class != '' THEN 'c-' || class || '-' END," >> $SQL_FILE
echo "                  CASE WHEN \"order\" IS NOT NULL AND \"order\" != '' THEN 'o-' || \"order\" || '-' END," >> $SQL_FILE
echo "                  CASE WHEN family IS NOT NULL AND family != '' THEN 'f-' || family || '-' END," >> $SQL_FILE
echo "                  CASE WHEN genus IS NOT NULL AND genus != '' THEN 'g-' || genus || '-' END," >> $SQL_FILE
echo "                  CASE WHEN species IS NOT NULL AND species != '' THEN 's-' || species || '-' END," >> $SQL_FILE
echo "                  CASE WHEN infraspecificepithet IS NOT NULL AND infraspecificepithet != '' THEN 'i-' || infraspecificepithet || '-' END," >> $SQL_FILE
echo "                  CASE WHEN taxonrank IS NOT NULL AND taxonrank != '' THEN 'tr-' || taxonrank END," >> $SQL_FILE
echo "                  CASE WHEN scientificname IS NOT NULL AND scientificname != '' THEN 'sn-' || scientificname || '-' END," >> $SQL_FILE
echo "                  CASE WHEN verbatimscientificname IS NOT NULL AND verbatimscientificname != '' THEN 'vsn-' || verbatimscientificname || '-' END," >> $SQL_FILE
echo "                  CASE WHEN verbatimscientificnameauthorship IS NOT NULL AND verbatimscientificnameauthorship != '' THEN 'vsa-' || verbatimscientificnameauthorship || '-' END" >> $SQL_FILE
echo "              ) AS taxon_string," >> $SQL_FILE
echo "          CONCAT_WS(', ', " >> $SQL_FILE
echo "                  CASE WHEN countrycode IS NOT NULL AND countrycode != '' THEN 'cc-' || countrycode || '-' END," >> $SQL_FILE
echo "                  CASE WHEN cleaned_locality IS NOT NULL AND cleaned_locality != '' THEN 'll-' || cleaned_locality || '-' END, " >> $SQL_FILE
echo "                  CASE WHEN stateprovince IS NOT NULL AND stateprovince != '' THEN 'tt-' || stateprovince || '-' END" >> $SQL_FILE
echo "          ) AS geography_string" >> $SQL_FILE
echo "  FROM read_parquet([" >> $SQL_FILE
# Add each file URL

for file in $files; do
   echo "    'https:${S3_PATH}${file}'," >> $SQL_FILE
done
#
# Remove trailing comma
sed -i '$ s/,$//' $SQL_FILE
#
# Close SQL
echo "  ])" >> $SQL_FILE

cat $SQL_FILE | duckdb view_parquet_${YEAR}_${MONTH}.duckdb

#!/bin/bash

# === Input Parameters ===
YEAR=$1
MONTH=$2
COUNTRYCODE=$3

if [ -z "$YEAR" ] || [ -z "$MONTH" ] || [ -z "$COUNTRYCODE" ]; then
  echo "Usage: ./extract_country.sh <year> <month> <country_code>"
  echo "Example: ./extract_country.sh 2025 02 SR"
  exit 1
fi

# === Zero-pad month if needed ===
MONTH=$(printf "%02d" $MONTH)

# === Step 1: List files in S3 bucket ===
S3_PATH="s3://gbif-open-data-us-east-1/occurrence/${YEAR}-${MONTH}-01/occurrence.parquet/"
FILES=$(aws s3 ls "$S3_PATH" --no-sign-request | awk '{print $4}')

if [ -z "$FILES" ]; then
  echo "❌ No files found for $YEAR-$MONTH!"
  exit 1
fi

# === Step 2: Build DuckDB SQL ===
SQL_FILE="query_${COUNTRYCODE}_${YEAR}_${MONTH}.sql"
PARQUET_FILE="occurrence_${COUNTRYCODE}_${YEAR}_${MONTH}.parquet"

echo "INSTALL httpfs;" > $SQL_FILE
echo "LOAD httpfs;" >> $SQL_FILE
echo "" >> $SQL_FILE
echo "COPY (" >> $SQL_FILE
echo "  SELECT *" >> $SQL_FILE
echo "  FROM parquet_scan([" >> $SQL_FILE

# Add each file URL
for FILE in $FILES; do
  echo "    'https://gbif-open-data-us-east-1.s3.amazonaws.com/occurrence/${YEAR}-${MONTH}-01/occurrence.parquet/$FILE'," >> $SQL_FILE
done

# Remove trailing comma
sed -i '' '$ s/,$//' $SQL_FILE

# Close SQL
echo "  ])" >> $SQL_FILE
echo "  WHERE lower(countrycode) = lower('${COUNTRYCODE}')" >> $SQL_FILE
echo ") TO '${PARQUET_FILE}' (FORMAT PARQUET);" >> $SQL_FILE

echo "" 
echo "✅ SQL query saved to: $SQL_FILE"
echo "✅ Output Parquet file will be: $PARQUET_FILE"
echo ""

# === Step 3: Run the query ===
echo "Running DuckDB query..."
duckdb < $SQL_FILE

echo ""
echo "🎉 Done! File saved: $PARQUET_FILE"

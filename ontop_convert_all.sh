#!/bin/bash

# === Input Parameters ===
year=$1
month=$2

if [ -z "$year" ] || [ -z "$month" ]; then
  echo "Usage: ./create_local_duckdb.sh <year> <month> <country_code>"
  echo "Example: ./create_local_duckdb.sh 2025 02"
  exit 1
fi

# === Zero-pad month if needed ===
month=$(printf "%02d" $month)
# echo "[$(date "+%Y-%m-%d %H:%M:%S")] Listing files in S3 bucket..."
source s3occurence.sh

files=$(listOccurenceFiles "https://gbif-open-data-eu-central-1.s3.eu-central-1.amazonaws.com/" $year $month)

for file in $(echo -e "$files")
do
    date=$(echo "$file" |cut -f 5 -d '/')
    year=${date:0:4}
    month=${date:5:2}
    name=$(echo "$file" |cut -f 7 -d '/')
    location="";
    if [ -e ${year}/${month}/$name ]
    then
        location="./${year}/${month}/$name"
    else
        location="${file}"
    fi
    db=view_${year}_${month}_${name}.duckdb
    ./create_view_duckdb.sh $file $db
    ./ontop_convert.sh $db
    rm $db
done

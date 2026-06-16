#!/bin/bash

year=$1
month=$2

if [ -z "$year" ] || [ -z "$month" ]; then
  echo "Usage: ./download_gbif.sh <year> <month> <country_code>"
  echo "Example: ./download_gbif.sh 2025 02"
  exit 1
fi

# === Zero-pad month if needed ===
month=$(printf "%02d" $month)
echo "[$(date "+%Y-%m-%d %H:%M:%S")] Step 1: Listing files in S3 bucket..."

source s3occurence.sh

download_urls="download_gbif_${year}_${month}.list"
#?delimiter=%2F&prefix=occurrence%2F${year}-${month}-01%2Foccurrence.parquet%2F
listOccurenceFiles "https://gbif-open-data-eu-central-1.s3.eu-central-1.amazonaws.com/" "${year}" "${month}" > $download_urls
wc -l $download_urls
echo "[$(date "+%Y-%m-%d %H:%M:%S")] Step 2: Downloading files in S3 bucket..."
wget -P ${year}/${month} -c -i "$download_urls"

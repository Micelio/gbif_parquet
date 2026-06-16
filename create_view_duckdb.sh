#!/bin/bash

# === Input Parameters ===
file=$1
db=$2
if [ -z "$file" ] || [ -z $db ] ; then
  echo "Usage: ./create_view_duckdb.sh $file $db"
  exit 1
fi

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

# === Build DuckDB view ===
duckdb $db << EOF

CREATE OR REPLACE VIEW "occurence" AS SELECT
        gbifid,
        datasetkey,
        occurrenceid,
        kingdom,
        phylum,
        class,
        "order",
        family,
        genus,
        species,
        infraspecificepithet,
        taxonrank,
        scientificname,
        verbatimscientificname,
        verbatimscientificnameauthorship,
        countrycode,
        locality,
        stateprovince,
        occurrencestatus,
        individualcount,
        publishingorgkey,
        decimallongitude,
        decimallatitude,
        coordinateuncertaintyinmeters,
        coordinateprecision,
        elevation,
        elevationaccuracy,
        depth,
        depthaccuracy,
        eventdate,
        day,
        month,
        year,
        taxonkey,
        specieskey,
        basisofrecord,
        institutioncode,
        collectioncode,
        catalognumber,
        recordnumber,
        identifiedby,
        dateidentified,
        license,
        rightsholder,
        recordedby,
        typestatus,
        establishmentmeans,
        lastinterpreted,
        mediatype,
        issue,
        (regexp_replace(locality, '�', '', 'g')) AS cleaned_locality,
        ('Point('||decimallongitude || ' ' || decimallatitude|| ')') AS point ,

        (
            CONCAT_WS(', ',
                CASE WHEN kingdom IS NOT NULL AND kingdom != '' THEN 'k-' || kingdom || '-' END,
                CASE WHEN phylum IS NOT NULL AND phylum != '' THEN 'p-' || phylum || '-' END,
                CASE WHEN class IS NOT NULL AND class != '' THEN 'c-' || class || '-' END,
                CASE WHEN "order" IS NOT NULL AND "order" != '' THEN 'o-' || "order" || '-' END,
                CASE WHEN family IS NOT NULL AND family != '' THEN 'f-' || family || '-' END,
                CASE WHEN genus IS NOT NULL AND genus != '' THEN 'g-' || genus || '-' END,
                CASE WHEN species IS NOT NULL AND species != '' THEN 's-' || species || '-' END,
                CASE WHEN infraspecificepithet IS NOT NULL AND infraspecificepithet != '' THEN 'i-' || infraspecificepithet || '-' END,
                CASE WHEN taxonrank IS NOT NULL AND taxonrank != '' THEN 'tr-' || taxonrank END,
                CASE WHEN scientificname IS NOT NULL AND scientificname != '' THEN 'sn-' || scientificname || '-' END,
                CASE WHEN verbatimscientificname IS NOT NULL AND verbatimscientificname != '' THEN 'vsn-' || verbatimscientificname || '-' END,
                CASE WHEN verbatimscientificnameauthorship IS NOT NULL AND verbatimscientificnameauthorship != '' THEN 'vsa-' || verbatimscientificnameauthorship || '-' END
                )
            ) AS taxon_string ,
        (
            CONCAT_WS(', ',
                CASE WHEN countrycode IS NOT NULL AND countrycode != '' THEN 'cc-' || countrycode || '-' END,
                CASE WHEN cleaned_locality IS NOT NULL AND cleaned_locality != '' THEN 'll-' || cleaned_locality || '-' END,
                CASE WHEN stateprovince IS NOT NULL AND stateprovince != '' THEN 'tt-' || stateprovince || '-' END
                )
            ) AS geography_string ,
        (
            SWITCH(license, MAP { 'CC_BY_NC_4_0' : 'licenses/by-nc/4.0/',
                                  'CC_BY_4_0'    : 'licenses/by/4.0/',
                                  'CC0_1_0'      : 'publicdomain/zero/1.0/' })
            ) AS license_iri
        FROM read_parquet('$location')
EOF
# INSTALL spatial;
# LOAD spatial;
# TODO
#   CASE WHEN decimallongitude IS NOT NULL THEN ('Polygon('||ST_AsText(
#            ST_Buffer(
#                ST_Point(decimallongitude,decimallatitude)
#            , CASE WHEN coordinateprecision IS NULL THEN 500 ELSE coordinateprecision END)
#         )) ELSE NULL END AS circle,

#!/bin/bash

# Step 1: Get distinct QIDs with wdt:P225 property from GraphDB
curl -s -X POST http://localhost:7200/repositories/wikidata \
  -H "Content-Type: application/sparql-query" \
  -H "Accept: application/sparql-results+json" \
  --data '
    PREFIX wdt: <http://www.wikidata.org/prop/direct/>
    SELECT DISTINCT ?qid WHERE {
      ?qid wdt:P225 ?o .
    }
  ' | jq -r '.results.bindings[].qid.value' > qids.txt

# Step 2: Load each QID URI directly into GraphDB
while read -r uri; do
  echo "Loading <$uri>..."

  curl -s -X POST http://localhost:7200/repositories/wikidata/statements \
    -H "Content-Type: application/sparql-update" \
    --data "LOAD <$uri>"
done < qids.txt
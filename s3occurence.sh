#!/usr/bin/bash

function listOccurenceFiles {
  local bucketPath="$1"
  local year="$2"
  local month="$3"
  local curl_args=""
  local next_token
  while true;
  do
      # Dynamically build the curl arguments array

      curl_args=(-s -G "$bucketPath" --data-urlencode "list-type=2" --data-urlencode "delimiter=/" --data-urlencode "prefix=occurrence/${year}-${month}-01/occurrence.parquet/")

      # If we have a token from the previous loop, append it safely
      if [ -n "$next_token" ]; then
          curl_args+=(--data-urlencode "continuation-token=$next_token")
      fi

      # Fetch the XML payload
      local response=$(curl "${curl_args[@]}")

      # [Process files] Extract and print all file keys in this batch
      for i in $(echo "$response" |xmllint --xpath "//*[local-name()='Key']/text()" -)
      do
        echo "${bucketPath}${i}"
      done

      # Check if the bucket has more items left to list
      if [ $(echo "$response" | xmllint --xpath "//*[local-name()='IsTruncated']/text()" - ) != "true" ]; then
          break
      fi

      # Extract the token for the next iteration
      next_token=$(echo "$response" | xmllint --xpath "//*[local-name()='NextContinuationToken']/text()" -)

      # Safety check: If truncated is true but token extraction failed, drop out to prevent infinite loop
      if [ -z "$next_token" ]; then
          break
      fi
  done
}

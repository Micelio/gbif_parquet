import duckdb

import duckdb

# Start an in-memory DuckDB session (no .duckdb file needed)
con = duckdb.connect()

# Use parquet_scan to query the file directly
df = con.execute("DESCRIBE SELECT * FROM parquet_scan('occurrence_MS_2025_02.parquet') LIMIT 10").fetchdf()

print(df)
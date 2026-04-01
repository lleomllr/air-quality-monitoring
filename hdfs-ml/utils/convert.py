import pyarrow as pa 
import pyarrow.parquet as pq 
import gc

input_f = "hdfs-ml/data/AirNow_2025_clean.parquet"
output_f = "hdfs-ml/data/AirNow_2025_spark.parquet"

print("Ouverture du fichier...")
pf = pq.ParquetFile(input_f)
print(f"Lignes totales: {pf.metadata.num_rows:,}")
print(f"Row groups: {pf.metadata.num_row_groups}")

#Nouveau schéma avec timestamps en microseconds
old_schema = pf.schema_arrow
new_fields = []
for f in old_schema:
    if pa.types.is_timestamp(f.type):
        new_fields.append(pa.field(f.name, pa.timestamp('us')))
    else:
        new_fields.append(f)
new_schema = pa.schema(new_fields)

print(f"\nConversion par batches de 50000 lignes...")
writer = pq.ParquetWriter(output_f, new_schema)
count = 0

for batch in pf.iter_batches(batch_size=50000):
    arrays = []
    for i, f in enumerate(old_schema):
        col = batch.column(i)
        if pa.types.is_timestamp(f.type):
            col = col.cast(pa.timestamp('us'))
        arrays.append(col)
    
    new_batch = pa.RecordBatch.from_arrays(arrays, schema=new_schema)
    writer.write_batch(new_batch)
    
    count += len(batch)
    print(f"  {count:,} lignes traitées...", end='\r')
    gc.collect()  

writer.close()
print(f"\n\nTerminé! Fichier: {output_f}")

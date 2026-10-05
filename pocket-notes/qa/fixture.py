import struct, zlib
from pathlib import Path

# Clearly labelled technical fixture; no sample content is embedded in the app.
w,h=600,800
rows=[]
for y in range(h):
 row=bytearray([0])
 for x in range(w):
  color=(220,233,244) if y<400 else (47,99,111)
  if 100<x<500 and 100<y<700: color=(255,255,255)
  if 160<x<440 and 200<y<240: color=(241,200,90)
  if 160<x<440 and 320<y<340: color=(34,100,109)
  if 160<x<390 and 380<y<400: color=(34,100,109)
  row.extend(color)
 rows.append(row)
def chunk(kind,data): return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
Path('out/reference-fixture.png').write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',w,h,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(rows)))+chunk(b'IEND',b''))

#!/usr/bin/env python3
"""Strict lossless counter-PNG decoder; no image quality or speed inference."""
from __future__ import annotations
import argparse
import binascii
import hashlib
import json
from pathlib import Path
import struct
import zlib

TAG='c7a14d32'

def read_png(path):
    data=Path(path).read_bytes()
    if data[:8]!=b'\x89PNG\r\n\x1a\n':raise ValueError('Not PNG')
    pos=8; payload=[]; header=None; ended=False
    while pos<len(data):
        if pos+12>len(data):raise ValueError('Truncated PNG')
        n=struct.unpack('>I',data[pos:pos+4])[0];kind=data[pos+4:pos+8];body=data[pos+8:pos+8+n]
        if n>64*1024*1024 or len(body)!=n or pos+n+12>len(data):raise ValueError('Oversized/truncated PNG chunk')
        crc=struct.unpack('>I',data[pos+8+n:pos+12+n])[0]
        if binascii.crc32(kind+body)&0xffffffff!=crc:raise ValueError('PNG CRC mismatch')
        if kind==b'IHDR':
            if header is not None or pos!=8 or n!=13:raise ValueError('Malformed IHDR')
            header=struct.unpack('>IIBBBBB',body)
        elif kind==b'IDAT':payload.append(body)
        elif kind==b'IEND':
            if n or pos+n+12!=len(data):raise ValueError('Malformed IEND')
            ended=True;break
        elif kind[0]&32==0:raise ValueError('Unsupported critical PNG chunk')
        pos+=n+12
    if not header or not ended:raise ValueError('Incomplete PNG')
    w,h,depth,color,compression,filter_method,interlace=header
    if not (64<=w<=8192 and 16<=h<=8192 and depth==8 and color in (2,6) and compression==filter_method==interlace==0):raise ValueError('Unsupported PNG encoding/dimensions')
    bpp=3 if color==2 else 4;stride=w*bpp;expected=(stride+1)*h
    dec=zlib.decompressobj();raw=dec.decompress(b''.join(payload),expected+1)
    if len(raw)!=expected or not dec.eof or dec.unused_data or dec.unconsumed_tail:raise ValueError('PNG decompression length/trailer mismatch')
    rows=[];previous=bytearray(stride)
    def paeth(a,b,c):
        p=a+b-c;da,db,dc=abs(p-a),abs(p-b),abs(p-c)
        return a if da<=db and da<=dc else b if db<=dc else c
    for y in range(h):
        offset=y*(stride+1);f=raw[offset];row=bytearray(raw[offset+1:offset+1+stride])
        if f>4:raise ValueError('Invalid PNG filter')
        for x in range(stride):
            a=row[x-bpp] if x>=bpp else 0;b=previous[x];c=previous[x-bpp] if x>=bpp else 0
            prediction=(0,a,b,(a+b)//2,paeth(a,b,c))[f]
            row[x]=(row[x]+prediction)&255
        if bpp==4 and any(row[x]!=255 for x in range(3,stride,4)):raise ValueError('Nonopaque screenshot')
        rows.append(bytes(row if bpp==3 else b''.join(row[x:x+3] for x in range(0,stride,4))));previous=row
    return w,h,rows

def decode_pixels(w,h,rows,receipt=None):
    if len(rows)!=h or any(len(r)!=w*3 for r in rows):raise ValueError('Malformed image rows')
    def pixel(x,y):
        if not (0<=x<w and 0<=y<h):raise ValueError('Protocol out of bounds')
        return rows[h-1-y][3*x:3*x+3] # Native PNG is top-down, GL is bottom-up.
    palette=[pixel(i,0) for i in range(16)]
    maps=[]
    for c in range(3):
        values=[p[c] for p in palette]
        if any(b-a<8 for a,b in zip(values,values[1:])):raise ValueError('Palette ambiguous/nonmonotone')
        maps.append({v:i for i,v in enumerate(values)})
    for x in range(w):
        if pixel(x,0)!=palette[x%16]:raise ValueError('Calibration row corruption/overlay')
    def nibble_pixel(x,y):
        try:return tuple(maps[c][v] for c,v in enumerate(pixel(x,y)))
        except KeyError:raise ValueError('Pixel outside calibrated palette') from None
    def byte_at(i):
        a=nibble_pixel(2*i,1);b=nibble_pixel(2*i+1,1)
        if len(set(a))!=1 or len(set(b))!=1:raise ValueError('Header channel mismatch')
        return a[0]+16*b[0]
    header=[byte_at(i) for i in range(28)]
    if header[:7]!=[199,1,1,199,161,77,50] or header[24:]!=[165,90,60,195] or sum(header[:23])%256!=header[23]:raise ValueError('Header magic/schema/source/checksum mismatch')
    nw,nh,cw,ch=[header[i]+256*header[i+1] for i in (7,9,11,13)]
    frame=header[15]+256*header[16];time=header[17]+256*header[18];tail,up,hsteps,zsteps=header[19:23]
    if (nw,nh)!=(w,h) or (cw,ch)!=(w//4,h//4) or up!=4 or tail not in (0,1) or not(1<=hsteps<=80 and 1<=zsteps<=80) or 4*cw>w or ch+8>h:raise ValueError('Unsupported/malformed native footprint')
    totals=[0,0,0];markers={};zero=0;invalid=0;maximum=max(hsteps,zsteps)
    for y in range(ch):
        for x in range(cw):
            low=nibble_pixel(4*x,y+8);high=nibble_pixel(4*x+1,y+8);extra=nibble_pixel(4*x+2,y+8)
            optical=nibble_pixel(4*x+3,y+8)
            if optical[2]!=15-optical[0]:raise ValueError('Optical digest cross-check mismatch')
            digest=optical[0]+16*optical[1]
            n,b,o=[a+16*d for a,d in zip(low,high)];marker=extra[0]+16*extra[1]
            if (n+3*b+5*o+7*marker+digest+(digest//16)+x+3*y)%16!=extra[2]:raise ValueError('Counter checksum mismatch')
            if n>maximum or b>n or o>160 or o!=2*n-b:raise ValueError('Counter saturation/nonintegral executed-order identity')
            # Known core 2/global 0 or 1 only. All unknown/unlinked/unsupported
            # markers, stale producer stamps (255), and mixed arms fail closed.
            if marker not in (128,136,137,138):raise ValueError('Unknown producer core/global/path marker')
            path=marker%8;glob=(marker-128)//8
            if (glob==0 and path!=0) or (tail==0 and path!=0) or (tail==1 and glob==1 and path not in (1,2)) or (path==0 and b):raise ValueError('Producer path/activation mismatch')
            for i,value in enumerate((n,b,o)):totals[i]+=value
            markers[marker]=markers.get(marker,0)+1
            zero+=n==0;invalid+=path==2
    if len({(m-128)//8 for m in markers})!=1:raise ValueError('Mixed producer global controls')
    metadata={'native_dimensions':[w,h],'cloud_dimensions':[cw,ch],'frame_counter_low16':frame,'world_time_low16':time,'tail_option':bool(tail),'upscaling':up,'primary_steps':[hsteps,zsteps],'source_tag':TAG,'producer_stage':'world0/deferred1.fsh','consumer_stage':'world0/final.fsh'}
    if receipt is not None:
        for key in ('archive_sha256','scene_sha256','raw_workload_sha256'):
            v=receipt.get(key)
            if not isinstance(v,str) or len(v)!=64 or any(c not in '0123456789abcdef' for c in v):raise ValueError('Missing external receipt '+key)
        for key in ('native_dimensions','source_tag','producer_stage','consumer_stage','tail_option'):
            if receipt.get(key)!=metadata[key]:raise ValueError('External receipt/header mismatch '+key)
    return {'status':'OBSERVED_COUNTERS_PENDING_EXTERNAL_RUNTIME_QUALIFICATION','metadata':metadata,'external_receipt':receipt,
      'rays':cw*ch,'zero_call_rays':zero,'invalid_input_fallback_rays':invalid,'producer_markers':markers,
      'called_cells':totals[0],'actual_order4_breaks':totals[1],'actual_executed_orders':4*totals[2],
      'eligible_call_fraction':totals[1]/totals[0] if totals[0] else None,
      'saved_order_fraction':totals[1]/(2*totals[0]) if totals[0] else None,
      'limits':'single current checkerboard phase; joint producer stamp modulo 1024 can collide; optical digest is lossy and only preserves observability; zero-call causes unknown; no GPU/image-quality inference'}

def decode_png(path,receipt=None):
    r=decode_pixels(*read_png(path),receipt=receipt);r['png_sha256']=hashlib.sha256(Path(path).read_bytes()).hexdigest();return r

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('png',type=Path);p.add_argument('--receipt',type=Path);p.add_argument('--output',type=Path);a=p.parse_args()
    r=decode_png(a.png,json.loads(a.receipt.read_text()) if a.receipt else None);s=json.dumps(r,indent=2,sort_keys=True)+'\n'
    if a.output:a.output.write_text(s)
    else:print(s,end='')

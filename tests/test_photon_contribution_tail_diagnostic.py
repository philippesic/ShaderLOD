"""Bounded source/parser/PNG protocol checks. Never launches a client."""
import binascii
import copy
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
import zlib
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'scripts'));sys.path.insert(0,str(ROOT/'tests'))
import build_photon_contribution_tail_diagnostic as diag
import build_photon_contribution_tail as normal
import decode_photon_contribution_tail_diagnostic as decode
import test_photon_contribution_tail as tailtests
expand=tailtests.expand

OUT=ROOT/'build/contribution-tail-diagnostic-source-checks'

def strip_diag(s):
    result=[];stack=[]
    for line in s.splitlines(keepends=True):
        key=line.strip()
        if key=='#ifdef '+diag.SWITCH:stack.append(False)
        elif key=='#ifndef '+diag.SWITCH:stack.append(True)
        elif key=='#else // '+diag.SWITCH:stack[-1]=not stack[-1]
        elif key=='#endif // '+diag.SWITCH:stack.pop()
        elif not stack or all(stack):result.append(line)
    if stack:raise AssertionError('Unclosed diagnostic guard')
    return re.sub(r'// Private query-free counter diagnostic; default OFF.\n(?://)?#define '+diag.SWITCH+r'\n','', ''.join(result))

def tokens(s):return re.findall(r'\w+|[^\s]',re.sub(r'/\*.*?\*/|//[^\n]*','',s,flags=re.S))

def fixture(w=80,h=32,transfer=None,tail=1,glob=1):
    # Deliberately nonlinear observed transfer; decoder must calibrate all channels.
    transfer=transfer or [round(255*(i/15)**.7) for i in range(16)]
    rows=[bytearray(w*3) for _ in range(h)]
    def set_pixel(x,y,v):rows[h-1-y][3*x:3*x+3]=bytes(transfer[i] for i in v)
    for x in range(w):set_pixel(x,0,(x%16,)*3)
    cw,ch=w//4,h//4;header=[199,1,1,199,161,77,50]
    for v in [w,h,cw,ch,73,12500]:header.extend([v%256,v//256])
    header.extend([tail,4,40,20]);header.extend([sum(header)%256,165,90,60,195])
    for i,v in enumerate(header):set_pixel(2*i,1,(v%16,)*3);set_pixel(2*i+1,1,(v//16,)*3)
    for y in range(ch):
        for x in range(cw):
            n=(x+y)%41;b=(n//2 if tail and glob else 0);o=2*n-b;marker=128+8*glob+(1 if tail and glob else 0)
            set_pixel(4*x+3,y+8,(109%16,109//16,15-109%16))
            set_pixel(4*x,y+8,(n%16,b%16,o%16));set_pixel(4*x+1,y+8,(n//16,b//16,o//16));set_pixel(4*x+2,y+8,(marker%16,marker//16,(n+3*b+5*o+7*marker+109+109//16+x+3*y)%16))
    return w,h,[bytes(r) for r in rows]

def write_png(path,dimensions):
    w,h,rows=dimensions
    def chunk(kind,body):return struct.pack('>I',len(body))+kind+body+struct.pack('>I',binascii.crc32(kind+body)&0xffffffff)
    path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(b''.join(b'\0'+r for r in rows)))+chunk(b'IEND',b''))

class DiagnosticTests(unittest.TestCase):
    def setUp(self):
        OUT.mkdir(parents=True,exist_ok=True)

    def test_decoder_roundtrip_inactive_and_real_png(self):
        for tail,glob in ((1,1),(1,0),(0,1)):
            image=fixture(tail=tail,glob=glob);r=decode.decode_pixels(*image)
            self.assertEqual(160,r['rays']);self.assertGreater(r['zero_call_rays'],0)
            self.assertEqual(8*r['called_cells']-4*r['actual_order4_breaks'],r['actual_executed_orders'])
            if not(tail and glob):self.assertEqual(0,r['actual_order4_breaks'])
        p=OUT/'synthetic-nonlinear.png';write_png(p,fixture());r=decode.decode_png(p)
        self.assertEqual(decode.decode_pixels(*fixture())['called_cells'],r['called_cells'])
        (OUT/'synthetic-roundtrip.json').write_text(json.dumps(r,indent=2)+'\n')
    def test_corruption_unknown_saturation_and_receipt_rejection(self):
        w,h,rows=fixture()
        for x,y in ((1,0),(2,1),(0,8),(2,8)):
            altered=[bytearray(r) for r in rows];offset=3*x;altered[h-1-y][offset]=1
            with self.assertRaises(ValueError):decode.decode_pixels(w,h,altered)
        for palette in ([0]*16,[i for i in range(16)]):
            with self.assertRaises(ValueError):decode.decode_pixels(*fixture(transfer=palette))
        bad=[bytearray(r) for r in rows];bad[h-1-8][:9]=bytes([255]*9)
        with self.assertRaises(ValueError):decode.decode_pixels(w,h,bad)
        with self.assertRaises(ValueError):decode.decode_pixels(w,h,rows,receipt={})
        receipt={'archive_sha256':'a'*64,'scene_sha256':'b'*64,'raw_workload_sha256':'c'*64,'native_dimensions':[w,h],'source_tag':decode.TAG,'producer_stage':'world0/deferred1.fsh','consumer_stage':'world0/final.fsh','tail_option':True}
        self.assertEqual(receipt,decode.decode_pixels(w,h,rows,receipt)['external_receipt'])
        receipt['source_tag']='wrong'
        with self.assertRaises(ValueError):decode.decode_pixels(w,h,rows,receipt)
        p=OUT/'corrupt.png';write_png(p,fixture());data=bytearray(p.read_bytes());data[-1]^=1;p.write_bytes(data)
        with self.assertRaises(ValueError):decode.read_png(p)

if __name__=='__main__':unittest.main()

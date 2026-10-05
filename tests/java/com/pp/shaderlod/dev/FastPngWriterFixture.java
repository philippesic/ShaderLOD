package com.pp.shaderlod.dev;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;
public final class FastPngWriterFixture {
 static int checks;
 static int pixel(int x,int y) {
  int r=(x*37+y*11)&255, g=(x*13+y*71)&255,b=(x*97+y*7)&255;
  int a=new int[]{0,1,127,128,254,255}[(x+y)%6];
  return a<<24|b<<16|g<<8|r;
 }
 static void check(boolean b,String why) {checks++;if(!b)throw new AssertionError(why);}
 static void verify(Path path,int w,int h) throws Exception {
  byte[] png=Files.readAllBytes(path); var in=new DataInputStream(new ByteArrayInputStream(png));
  check(Arrays.equals(in.readNBytes(8),new byte[]{(byte)137,80,78,71,13,10,26,10}),"signature");
  var compressed=new ByteArrayOutputStream();boolean ended=false;int idats=0;
  while(in.available()>0) {
   int n=in.readInt(); byte[] type=in.readNBytes(4),data=in.readNBytes(n);long crcValue=Integer.toUnsignedLong(in.readInt());
   CRC32 crc=new CRC32();crc.update(type);crc.update(data);check(crcValue==crc.getValue(),"CRC "+new String(type));
   String name=new String(type,java.nio.charset.StandardCharsets.US_ASCII);
   if(name.equals("IHDR")) { var header=new DataInputStream(new ByteArrayInputStream(data));check(n==13,"IHDR length");check(header.readInt()==w&&header.readInt()==h,"dimensions");check(header.readUnsignedByte()==8&&header.readUnsignedByte()==6,"RGBA8");check(header.readByte()==0&&header.readByte()==0&&header.readByte()==0,"methods"); }
   else if(name.equals("IDAT")) {compressed.write(data);idats++;}
   else if(name.equals("IEND")) {check(n==0,"IEND empty");ended=true;check(in.available()==0,"IEND last");}
   else throw new AssertionError("unexpected chunk "+name);
  }
  check(ended&&idats>0,"IDAT and IEND");
  byte[] raw=new InflaterInputStream(new ByteArrayInputStream(compressed.toByteArray())).readAllBytes();check(raw.length==(w*4+1)*h,"inflated size");
  int pos=0;for(int y=0;y<h;y++){check(raw[pos++]==0,"filter None");for(int x=0;x<w;x++){int v=pixel(x,y);for(int c=0;c<4;c++)check((raw[pos++]&255)==((v>>>(c*8))&255),"channel/row exact");}}
  var image=ImageIO.read(path.toFile());check(image.getWidth()==w&&image.getHeight()==h,"ImageIO dimensions");
  for(int y=0;y<h;y++)for(int x=0;x<w;x++){int abgr=pixel(x,y);int argb=(abgr&0xFF00FF00)|((abgr&255)<<16)|((abgr>>>16)&255);check(image.getRGB(x,y)==argb,"ImageIO RGBA lossless including transparent RGB");}
 }
 public static void main(String[] args) throws Exception {
  Path out=Path.of(args[0]);Files.createDirectories(out);
  for(int level=0;level<=1;level++)for(int[] dims:new int[][]{{1,1},{3,2},{128,193}}){Path p=out.resolve("roundtrip-"+level+"-"+dims[0]+"x"+dims[1]+".png");long n=FastPngWriter.write(p,dims[0],dims[1],FastPngWriterFixture::pixel,level,1<<20);check(n==Files.size(p),"byte count");verify(p,dims[0],dims[1]);}
  try{FastPngWriter.write(out.resolve("invalid-level.png"),1,1,FastPngWriterFixture::pixel,2,1000);throw new AssertionError("invalid level accepted");}catch(IllegalArgumentException expected){checks++;}
  try{FastPngWriter.write(out.resolve("invalid-size.png"),0,1,FastPngWriterFixture::pixel,0,1000);throw new AssertionError("invalid size accepted");}catch(IllegalArgumentException expected){checks++;}
  Path cap=out.resolve("retained-byte-cap.png");try{FastPngWriter.write(cap,128,193,FastPngWriterFixture::pixel,0,100);throw new AssertionError("byte cap accepted");}catch(IOException expected){check(Files.size(cap)<=100,"byte bound retained partial");}
  Path old=out.resolve("roundtrip-0-1x1.png");byte[] before=Files.readAllBytes(old);try{FastPngWriter.write(old,1,1,FastPngWriterFixture::pixel,0,1000);throw new AssertionError("overwrite accepted");}catch(FileAlreadyExistsException expected){check(Arrays.equals(before,Files.readAllBytes(old)),"existing file preserved");}
  System.out.println("PASS: 6 lossless roundtrips (levels 0/1), raw inflate rows/channel bytes + independent ImageIO decode, all chunk CRCs/dimensions, byte-count/bounds/overwrite guards; checks="+checks);
 }
}

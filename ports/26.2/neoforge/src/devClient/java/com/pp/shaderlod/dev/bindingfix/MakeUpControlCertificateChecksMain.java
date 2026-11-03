package com.pp.shaderlod.dev.bindingfix;
public final class MakeUpControlCertificateChecksMain {
 public static void main(String[] args){
  if(args.length!=2)throw new IllegalArgumentException("actual shaderpacks directory and actual generated basename required");var dir=java.nio.file.Path.of(args[0]);String original="MakeUp-UltraFast-9.5g.zip",generated=args[1];var nativeMap=com.pp.shaderlod.integration.makeupquad.MakeUpQuadEligibility.nativeOptions();var generatedMap=MakeUpControlCertificate.generatedOptions();
  if(!MakeUpControlCertificate.validate(dir,original,original,nativeMap,false).eligible())throw new AssertionError("Authentic original refused");
  if(!MakeUpControlCertificate.validate(dir,generated,original,generatedMap,true).eligible())throw new AssertionError("Authentic actual generated/state/member source refused");
  if(MakeUpControlCertificate.validate(dir,generated,original,nativeMap,true).eligible())throw new AssertionError("Wrong option map admitted");
  var wrong=new java.util.TreeMap<>(generatedMap);wrong.put("AA_TYPE","1");if(MakeUpControlCertificate.validate(dir,generated,original,wrong,true).eligible())throw new AssertionError("Changed native AA admitted");
  if(MakeUpControlCertificate.validate(dir,generated,"unknown.zip",generatedMap,true).eligible())throw new AssertionError("Unknown original admitted");
 }
}

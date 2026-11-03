import copy,sys,unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
import benchmark_mip_family_assessment as p

class ExecutedMipAssessmentTest(unittest.TestCase):
 def packet(self,active):
  images=[{'target':1,'main':{'id':10,'min_filter':9729},'alt':{'id':11,'min_filter':9729}}]
  events=[]
  for frame in (1,2):
   events.append({'kind':'frame_start','frame':frame,'images':copy.deepcopy(images)})
   if not active:events.append({'kind':'mip_return','frame':frame,'texture':{'id':10}})
   events.append({'kind':'completed_quad','frame':frame,'name':'composite15'})
   events.append({'kind':'final_return','frame':frame})
  events.append({'kind':'frame_start','frame':3,'images':copy.deepcopy(images)})
  return {'actual':{'sources':[{'name':'composite15','parsed_mips':[] if active else [1]}],'events':events}}
 def test_complete_native_and_candidate_physical_omission(self):
  result=p.assess({'native':self.packet(False),'candidate':self.packet(True)},family='solas-shader',states={n:{'effective_options':{}} for n in ('native','candidate')})
  self.assertEqual(result['arms']['native']['executed'][0]['physical_mip_returns'],1)
  self.assertEqual(result['arms']['candidate']['executed'][0]['physical_mip_returns'],0)
 def test_missing_generation_and_reset_cannot_pass(self):
  row=self.packet(False);row['actual']['events']=[e for e in row['actual']['events'] if e['kind']!='mip_return']
  with self.assertRaises(ValueError):p.assess({'native':row},family='solas-shader',states={'native':{'effective_options':{}}})
  row=self.packet(False);row['actual']['events'][-1]['images'][0]['main']['min_filter']=9987
  with self.assertRaises(ValueError):p.assess({'native':row},family='solas-shader',states={'native':{'effective_options':{}}})

if __name__=='__main__':unittest.main()

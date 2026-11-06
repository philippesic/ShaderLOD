"""Build review derivatives while GPU clients are idle; original PNGs are evidence."""
import argparse
import json
from pathlib import Path
import subprocess
import sys

from PIL import Image, ImageDraw

HERE=Path(__file__).resolve().parent;ROOT=HERE.parents[2]
sys.path.insert(0,str(ROOT/'scripts'))
import dev_client as dev
import experiment_lifecycle as life
import run_storage as storage

p=argparse.ArgumentParser();p.add_argument('reports',nargs='+');args=p.parse_args()
life.require(not dev.clients(dev.process_table()),'Review derivatives require idle GPU clients')
for name in args.reports:
    report=storage.safe_path(HERE/name);life.require(report.parent==HERE,'Explicit audit report required')
    s=json.loads((report/'summary.json').read_text());op=json.loads((report/'operation.json').read_text())
    life.require(s['evaluation']['validity']=='VALID' and s['configuration_restored'] and not s['clients_remaining']
                 and op.get('release_verified') and op['state'] in life.TERMINAL,'Valid restored released capture required')
    arms={};record={'scope':'Review derivatives only. Lossless PNGs and recorder manifests are authoritative; video compression and thumbnail scaling are not pixel-parity evidence. No visual verdict is inferred automatically.','arms':{}}
    overview=Image.new('RGB',(1800,5*405),(25,25,25));draw=ImageDraw.Draw(overview)
    for column,arm in enumerate(('native','candidate','return')):
        directory=report/('quality-'+arm);m=json.loads((directory/'manifest.json').read_text())
        frames=m['frames'];life.require(m['status']=='complete' and len(frames)==121 and m['skipped_capture_slots']==0,'Complete 121-frame sequence required')
        paths=[storage.safe_path(directory/f['file']) for f in frames]
        life.require(all(f.get('status')=='written' and path.parent==directory and path.is_file() for f,path in zip(frames,paths)),'Written owned loose PNGs required before archiving')
        montage=Image.new('RGB',(11*220,11*156),(25,25,25));md=ImageDraw.Draw(montage)
        for index,path in enumerate(paths):
            with Image.open(path) as source:
                life.require(source.size==(2560,1600),'Native image dimensions changed')
                rgb=source.convert('RGB');thumb=rgb.resize((220,138),Image.Resampling.LANCZOS)
                x=index%11*220;y=index//11*156;montage.paste(thumb,(x,y));md.text((x+3,y+140),f'{arm} {index:03d}',fill='white')
                if index in (0,30,60,90,120):
                    row=(0,30,60,90,120).index(index);overview.paste(rgb.resize((600,375),Image.Resampling.LANCZOS),(column*600,row*405+24));draw.text((column*600+5,row*405+5),f'{arm} frame {index:03d}',fill='white')
        montage_path=report/('quality-'+arm+'-all-frames.jpg');montage.save(montage_path,quality=94)
        video=report/('quality-'+arm+'-preview.mp4')
        if not video.exists():
            subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-framerate','6','-start_number','0','-i',str(directory/'frame-%04d.png'),'-frames:v','121','-c:v','libopenh264','-b:v','8M','-g','12','-threads','2','-pix_fmt','yuv420p',str(video)],check=True,timeout=240)
        probe=json.loads(subprocess.check_output(['ffprobe','-v','error','-count_frames','-select_streams','v:0','-show_entries','stream=width,height,r_frame_rate,nb_read_frames','-of','json',str(video)],text=True))['streams'][0]
        life.require(probe['width']==2560 and probe['height']==1600 and probe['nb_read_frames']=='121' and probe['r_frame_rate']=='6/1','Preview frame count/dimensions/rate differ')
        record['arms'][arm]={'manifest':str(directory/'manifest.json'),'manifest_sha256':life.digest(directory/'manifest.json'),'all_frame_montage':str(montage_path),'preview':str(video),'preview_probe':probe,'frames':len(frames)}
        print('PREPARED',name,arm,flush=True)
    overview_path=report/'quality-overview.jpg';overview.save(overview_path,quality=95)
    record['overview']=str(overview_path);(report/'quality-review-preparation.json').write_text(json.dumps(record,indent=2)+'\n')

"""Owned, untimed camera sequences through the maintained benchmark controller.

Frames and encoder stalls must never be interpreted as performance evidence.
The dev recorder retains bounded lossless native images and actual camera poses.
"""
import json
from pathlib import Path
import time

import experiment_lifecycle as life


def validate_request(args):
    public=args.candidate=='public'
    bloom=(args.candidate=='c02-bloom-vrs' and args.scene in (None,'saved')
           and not getattr(args,'feature',None) and not getattr(args,'features',None))
    native_mips=(args.candidate=='g02-native-mips' and args.scene in (None,'saved')
                 and not getattr(args,'feature',None) and not getattr(args,'features',None))
    life.require(getattr(args,'functional_check',False) and (public or bloom or native_mips)
                 and not args.dh and args.scene in (None,'saved','natural-terrain') and not args.comparison_order,
                 'Quality motion requires a separate public, finite native-mip or qualified bloom VRS check on an owned saved scene, without DH or balanced timing')
    # The runner's natural_terrain_inputs still requires the fixed native CPU
    # subset and spawn. The recorder uses that same owned saved-world state.


def capture(control, output, label):
    life.require(label.replace('_','').isalnum() and len(label)<=48, 'Bounded quality label required')
    start=control.send('status')['state']['camera']
    pose={key:start[key] for key in ('x','y','z','yaw','pitch')}
    middle=pose|{'x':pose['x']+.5,'z':pose['z']+.5,
                 'yaw':pose['yaw']+12,'pitch':min(85,max(-85,pose['pitch']+4))}
    path={'schema':1,'label':'audit_'+label,'warmupSeconds':30,'fps':6,
          # Lossless compression bounds native disk use; the recorder holds its
          # logical path for render/callback capacity instead of dropping slots.
          'pngCompressionLevel':1,'maximumInFlightImages':2,
          'rejectFullBlockCamera':True,
          'waypoints':[pose|{'seconds':0},middle|{'seconds':10},pose|{'seconds':20}]}
    destination=Path(output).resolve()/('quality-'+label)
    life.require(not destination.exists(),'Fresh quality sequence required')
    packet=control.send('quality_start',label=label,request=path)
    life.require(packet.get('success') is True,'Quality start refused: '+str(packet.get('error')))
    life.require(Path(packet['quality']['output_directory']).resolve()==destination,
                 'Quality output escaped owned operation')
    deadline=time.monotonic()+150
    while True:
        packet=control.send('quality_status')['quality']
        if packet['finalized']:break
        life.require(time.monotonic()<deadline,'Quality recorder deadline exceeded')
        control.wait(.5)
    manifest=json.loads((destination/'manifest.json').read_text())
    life.require(manifest['status']=='complete' and manifest['skipped_capture_slots']==0
                 and manifest['png_callback_successes']==121
                 and manifest['writer_pending_at_finalization'] is False,
                 'Quality sequence incomplete: '+str(manifest.get('failure',manifest['status'])))
    files=list(destination.glob('frame-*.png'))
    life.require(len(files)==121 and all(p.is_file() and p.stat().st_size>0 for p in files),
                 'Quality lossless image sequence missing')
    return {'manifest':str(destination/'manifest.json'),'images':len(files),
            'scope':'Untimed 20s logical camera path, six lossless native framebuffer samples per path second. Path holds for render/writer capacity; wall duration can be longer. Native animation/history advance. No timing or exact deterministic pixel parity claim.'}

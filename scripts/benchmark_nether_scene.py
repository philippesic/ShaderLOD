"""Finite original-world Nether view for the existing non-Overworld mip route."""
import experiment_lifecycle as life

def enter(control):
    control.send('mip_nether_pose');state=control.ready()
    control.send('mip_nether_settle');state=control.ready()
    life.require(state.get('dimension')=='minecraft:the_nether' and state.get('saved_authored_ready') is True,'Native Nether pose did not settle')
    return {'dimension':'minecraft:the_nether','camera':state.get('camera'),'scope':'Existing backed-up authored save Nether at(.5,200,.5), native pack/options/AA; no constructed geometry.'}

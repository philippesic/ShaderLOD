"""Untimed finite native albedo-domain evidence; never grants shader permission."""
import experiment_lifecycle as life


def validate(packet):
    actual = packet['actual']
    life.require(actual.get('error') == '' and actual.get('completed_frames') == 2,
                 'Complete native domain capture required')
    rows = []
    for frame in [1, 2]:
        quads = [e for e in actual['events'] if e['kind'] == 'completed_quad'
                 and e['frame'] == frame and e['name'] == 'composite2']
        life.require(len(quads) == 1, 'Unique actual albedo consumer required')
        row = quads[0].get('bliss_albedo_domain', {})
        life.require(row.get('complete') is True and row.get('tested_inputs') == 65536
                     and row.get('outside_guard') == 0 and row.get('internal_format') == 32859
                     and (row.get('width'), row.get('height')) == (2560, 1600)
                     and 0 < row.get('zeros', 0) < 65536
                     and 4e-38 <= row.get('minimum_nonzero_absolute', 0)
                     and .5 <= row.get('maximum_absolute', 0) <= 1e6
                     and len(row.get('function_source_sha256', '')) == 64,
                     'Incomplete or unsafe native albedo domain')
        rows.append(row)
    life.require(rows[0] == rows[1], 'Native domain proof changed between returned frames')
    return {'frames': rows, 'scope': 'All scalar UNORM16 inputs through attached native functions on this backend; no executed emission-branch, visual or cross-backend proof.'}

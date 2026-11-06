# Four-target native benchmark profiles

These are byte-preserved, full native option maps from the Asahi audit, now
versioned so a fresh Windows runtime can reproduce the input definitions.
`inputs.lock.json` identifies original shader ZIPs, exact option bytes and the
retained historical sources. Original ZIPs remain local dependency artifacts;
their absence cannot be repaired by substituting an arbitrary same-named download.

Use these files with the maintained runner's `--pack-options`; `.properties`
changes only the filename, not the contents or option parser. Reimagined and
Unbound are separate profiles of one family. The three `*-pbr.properties` maps
are separate parallax opportunity cases, not replacements for default/native
baseline settings. Resource packs, scene, dimension, viewport, AA, GL context,
runtime and actual activation still require their own observed evidence.

Copying these small definitions did not move or alter any frozen historical run
input. No RTX5080 measurement or compatibility approval is implied by this folder.

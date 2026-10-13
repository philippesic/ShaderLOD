# BSL primary-cloud detail bound r2

Accepted isolated source candidate for BSL10.1.8, primary Overworld volumetric
clouds, clear-weather Balanced. Two detail reads are omitted only when the coarse
noise certifies exact native density0. Native marching and feedback stay intact.

- `source.patch`: exact difference from the current assembled BSL adapter.
- `integration.patch`: recipe/helper, packaged mirrors, catalog identity and
  NeoForge26.2 certificate join. Six paths; prior identities retained.
- `receipt.json`: original/noise/member pins, all preimages/postimages and ZIP hash.
- `control/`: separately pinned full-work LOD0 control. Its integration overlay
  also starts from original product preimages; its source patch is the single
  cutoff removal relative to candidate r2. Apply only one integration overlay
  to a given source copy.

Reproduce in a new isolated output directory:

```sh
python3 scripts/prepare_bsl_cloud_detail_bound.py \
  --base dev/upstream/other-pack-research/BSL_v10.1.8.zip \
  --output /absolute/new/candidate-directory
git apply --check patches/bsl-cloud-detail-bound-r2/integration.patch
python3 -m unittest tests.test_bsl_cloud_detail_bound \
  tests.test_deep_shader_candidate_consumers -v
```

The original archive, pinned local CPU dependencies and generated consumer
fixtures are required. Do not apply this overlay to the paused sealed source
inputs. Parent already integrated and reassembled it in a fresh source copy.
Runtime adoption requires a fresh target build/inputs and actual sampler,
rendering, recovery, quality and cost evidence after authorization resumes.

[Technical recommendation, equations, risks and experiment](../../docs/evaluation/deep-shader-investigation-20261012.md).
Original BSL credits and license are retained in generated private copies;
publication/redistribution is not authorized.

package com.pp.shaderlod.experiment.bsl;

/** Root-issued joined support proof. A field equality readback, a config bit or
 * finite scene examples cannot issue this proof. Root must establish error
 * bounds for the exact native9 implicit hardware-comparison support. */
public record BslFieldReceiverSupport(BslFieldEpochs.Identity identity,
        String producerSourceSha256, String terrainSourceSha256, String driver,
        int nativeDepthFormat, int compareFunction, int minFilter, int magFilter,
        double coordinateErrorTexels, double depthComparisonError,
        String fullSupportBoundEvidenceSha256) {
    public boolean bounded() {
        return identity != null && identity.frame() >= 0 && identity.frame() < 16_777_215
            && hex(producerSourceSha256) && hex(terrainSourceSha256) && hex(fullSupportBoundEvidenceSha256)
            && driver != null && !driver.isBlank() && nativeDepthFormat > 0
            && compareFunction == 0x0203 && minFilter == 0x2601 && magFilter == 0x2601
            && Double.isFinite(coordinateErrorTexels) && coordinateErrorTexels >= 0 && coordinateErrorTexels <= 1
            && Double.isFinite(depthComparisonError) && depthComparisonError >= 0 && depthComparisonError <= 1.0/65536.0;
    }
    private static boolean hex(String s) { return s != null && s.matches("[0-9a-f]{64}"); }
}

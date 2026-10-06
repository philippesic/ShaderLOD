package com.pp.shaderlod.lod.adapter.layout;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static com.pp.shaderlod.lod.adapter.layout.LayoutValidation.Code.*;
import static com.pp.shaderlod.lod.adapter.layout.TargetDeclaration.Role.*;
import static com.pp.shaderlod.lod.adapter.layout.TargetSize.Rounding.*;
import static org.junit.jupiter.api.Assertions.*;

class AdapterLayoutTest {
    private static final TargetSize FULL = TargetSize.relative(1, 1, CEIL);
    private static final TargetSize HALF = TargetSize.relative(1, 2, CEIL);
    private static final Dimensions SURFACE = new Dimensions(1708, 960);

    private static TargetDeclaration target(String id, TargetSize size, TargetFormat format,
                                            TargetDeclaration.Role role, int channels) {
        return new TargetDeclaration("example:" + id, size, format,
                new TargetDeclaration.Quantity("example:quantity/" + id, role, channels));
    }

    private static PassDeclaration pass(TargetSize viewport, String... ids) {
        return new PassDeclaration("example:draw", List.of(), List.of(new PassDeclaration.DrawGroup(
                List.of(ids).stream().map(id -> "example:" + id).toList(), viewport)),
                PassDeclaration.FeedbackPolicy.UNSUPPORTED);
    }

    private static void has(LayoutValidation validation, LayoutValidation.Code code) {
        assertFalse(validation.valid());
        assertTrue(validation.problems().stream().anyMatch(problem -> problem.code() == code),
                () -> "Missing " + code + " in " + validation.problems());
    }

    @Test void absoluteAndAnisotropicRationalDimensionsResolveExactly() {
        assertEquals(new Dimensions(13, 7), TargetSize.absolute(13, 7).resolve(SURFACE));
        var anisotropic = new TargetSize.Relative(new TargetSize.Ratio(2, 3, FLOOR),
                new TargetSize.Ratio(3, 7, CEIL));
        assertEquals(new Dimensions(1138, 412), anisotropic.resolve(SURFACE));
        assertEquals(new Dimensions(854, 480), HALF.resolve(SURFACE));
    }

    @Test void ceilOddSpatialEdgesAndTinySurfacesRemainAllocated() {
        var odd = new Dimensions(1709, 961);
        assertEquals(new Dimensions(855, 481), HALF.resolve(odd));
        assertEquals(new Dimensions(428, 241), TargetSize.relative(1, 4, CEIL).resolve(odd));
        assertEquals(new Dimensions(1, 1), TargetSize.relative(1, 4, CEIL).resolve(new Dimensions(1, 1)));
        assertThrows(IllegalArgumentException.class, () -> TargetSize.relative(1, 4, FLOOR)
                .resolve(new Dimensions(3, 3)));
    }

    @Test void maximumProductsAndCeilAdjustmentDoNotOverflow() {
        var max = new Dimensions(Integer.MAX_VALUE, Integer.MAX_VALUE);
        assertEquals(max, TargetSize.relative(Integer.MAX_VALUE, Integer.MAX_VALUE, CEIL).resolve(max));
        assertEquals(new Dimensions(1073741824, 1073741824), HALF.resolve(max));
        assertThrows(IllegalArgumentException.class, () -> TargetSize.relative(2, 1, FLOOR).resolve(max));
        assertThrows(IllegalArgumentException.class, () -> TargetSize.relative(Integer.MAX_VALUE, 1, CEIL).resolve(max));
    }

    @Test void zeroOrOverflowAllocationIsAValidationFailureRatherThanWrappedSuccess() {
        for (var size : List.of(TargetSize.relative(1, Integer.MAX_VALUE, FLOOR),
                TargetSize.relative(Integer.MAX_VALUE, 1, CEIL))) {
            var layout = new AdapterLayout(List.of(target("invalid", size, TargetFormat.R32F, GENERAL, 1)),
                    List.of(pass(size, "invalid")));
            var result = layout.validate(SURFACE);
            has(result, SIZE_OUT_OF_RANGE);
            assertFalse(result.targetDimensions().containsKey("example:invalid"));
        }
    }

    @Test void threeMrtRegressionChecksThirdMemberAndRetainedUpstreamPair() {
        var quarter = TargetSize.relative(1, 4, FLOOR);
        var a = target("a", quarter, TargetFormat.RGBA32F, GENERAL, 4);
        var b = target("b", quarter, TargetFormat.RGBA32F, GENERAL, 4);
        var c = target("c", FULL, TargetFormat.RGBA32F, GENERAL, 4);
        has(new AdapterLayout(List.of(a, b, c), List.of(pass(quarter, "a", "b", "c")))
                .validate(SURFACE), UNEQUAL_DRAW_ATTACHMENTS);
        // The reported downstream failure: resized diagnostic image plus unchanged upstream image.
        has(new AdapterLayout(List.of(a, c), List.of(pass(FULL, "a", "c")))
                .validate(SURFACE), UNEQUAL_DRAW_ATTACHMENTS);
    }

    @Test void viewportMustCoverEveryAttachmentWithNeitherUnwrittenTailNorOverflow() {
        var full = target("full", FULL, TargetFormat.RGBA32F, GENERAL, 4);
        has(new AdapterLayout(List.of(full), List.of(pass(HALF, "full"))).validate(SURFACE), VIEWPORT_UNDERSIZED);
        has(new AdapterLayout(List.of(full), List.of(pass(TargetSize.relative(2, 1, CEIL), "full")))
                .validate(SURFACE), VIEWPORT_OVERSIZED);
        var mixed = TargetSize.absolute(1707, 961);
        var result = new AdapterLayout(List.of(full), List.of(pass(mixed, "full"))).validate(SURFACE);
        has(result, VIEWPORT_UNDERSIZED);
        has(result, VIEWPORT_OVERSIZED);
    }

    @Test void allDiagnosticPagesAndUnmodifiedDownstreamImagesFormValidExplicitGraph() {
        var targets = List.of(target("page0", FULL, TargetFormat.RGBA32F, GENERAL, 4),
                target("page1", FULL, TargetFormat.RGBA32F, GENERAL, 4),
                target("page2", FULL, TargetFormat.RGBA32F, GENERAL, 4));
        var layout = new AdapterLayout(targets, List.of(pass(FULL, "page0", "page1", "page2"),
                new PassDeclaration("example:downstream", List.of(), List.of(
                        new PassDeclaration.DrawGroup(List.of("example:page1", "example:page2"), FULL)),
                        PassDeclaration.FeedbackPolicy.UNSUPPORTED)));
        assertTrue(layout.validate(SURFACE).valid());
    }

    @Test void distinctDrawGroupsMayHaveDifferentSizesButEachGroupMustBeCoherent() {
        var half = target("half", HALF, TargetFormat.RGBA32F, GENERAL, 4);
        var full = target("full", FULL, TargetFormat.RGBA32F, GENERAL, 4);
        var separate = new PassDeclaration("example:separate", List.of(), List.of(
                new PassDeclaration.DrawGroup(List.of(half.id()), HALF),
                new PassDeclaration.DrawGroup(List.of(full.id()), FULL)),
                PassDeclaration.FeedbackPolicy.UNSUPPORTED);
        assertTrue(new AdapterLayout(List.of(half, full), List.of(separate)).validate(SURFACE).valid());
    }

    @Test void explicitSpatialCoefficientAndCertificateProducersThenFullConsumerValidateAtOddSize() {
        var targets = new ArrayList<TargetDeclaration>();
        var passes = new ArrayList<PassDeclaration>();
        var reads = new ArrayList<PassDeclaration.Read>();
        for (int side : List.of(2, 4)) {
            var size = TargetSize.relative(1, side, CEIL);
            var coefficient = target("coefficient" + side, size, TargetFormat.RGBA16F, LINEAR_QUANTITY, 4);
            var certificate = target("certificate" + side, size, TargetFormat.RGBA32I, EXACT_SIGNED_INT32_METADATA, 4);
            var distance = target("distance" + side, size, TargetFormat.R32F, BINARY32_MINIMUM_DISTANCE, 1);
            targets.addAll(List.of(coefficient, certificate, distance));
            passes.add(new PassDeclaration("example:producer" + side, List.of(), List.of(
                    new PassDeclaration.DrawGroup(List.of(coefficient.id(), certificate.id(), distance.id()), size)),
                    PassDeclaration.FeedbackPolicy.UNSUPPORTED));
            reads.add(new PassDeclaration.Read(coefficient.id(), PassDeclaration.Fetch.FLOAT_OR_NORMALIZED));
            reads.add(new PassDeclaration.Read(certificate.id(), PassDeclaration.Fetch.SIGNED_INTEGER));
            reads.add(new PassDeclaration.Read(distance.id(), PassDeclaration.Fetch.FLOAT_TEXEL_FETCH));
        }
        targets.add(target("composition", FULL, TargetFormat.RGBA16F, GENERAL, 4));
        passes.add(new PassDeclaration("example:consumer", reads, List.of(
                new PassDeclaration.DrawGroup(List.of("example:composition"), FULL)),
                PassDeclaration.FeedbackPolicy.UNSUPPORTED));
        var result = new AdapterLayout(targets, passes).validate(new Dimensions(1709, 961));
        assertTrue(result.valid(), () -> result.problems().toString());
        assertEquals(new Dimensions(855, 481), result.targetDimensions().get("example:certificate2"));
        assertEquals(new Dimensions(428, 241), result.targetDimensions().get("example:coefficient4"));
    }

    @Test void exactCertificateRejectsFloatHalfFloatNarrowIntegerAndUnsignedReinterpretation() {
        for (var format : List.of(TargetFormat.RGBA32F, TargetFormat.RGBA16F,
                TargetFormat.RGBA16I, TargetFormat.RGBA32UI, TargetFormat.RGBA8)) {
            var metadata = target("metadata", FULL, format, EXACT_SIGNED_INT32_METADATA, 4);
            has(new AdapterLayout(List.of(metadata), List.of(pass(FULL, "metadata")))
                    .validate(SURFACE), INCOMPATIBLE_STORAGE);
        }
        var unsigned = target("unsigned", FULL, TargetFormat.RGBA32UI, EXACT_UNSIGNED_INT32_METADATA, 4);
        assertTrue(new AdapterLayout(List.of(unsigned), List.of(pass(FULL, "unsigned"))).validate(SURFACE).valid());
    }

    @Test void float32DistanceBitsCanBeStoredAsExactIntsButHalfFloatDistanceCannot() {
        var bits = target("bits", HALF, TargetFormat.RGBA32I, EXACT_SIGNED_INT32_METADATA, 4);
        assertTrue(new AdapterLayout(List.of(bits), List.of(pass(HALF, "bits"))).validate(SURFACE).valid());
        var approximate = target("distance", HALF, TargetFormat.RGBA16F, BINARY32_MINIMUM_DISTANCE, 1);
        has(new AdapterLayout(List.of(approximate), List.of(pass(HALF, "distance")))
                .validate(SURFACE), INCOMPATIBLE_STORAGE);
        var tooFew = target("bits", HALF, TargetFormat.R32I, EXACT_SIGNED_INT32_METADATA, 4);
        has(new AdapterLayout(List.of(tooFew), List.of(pass(HALF, "bits")))
                .validate(SURFACE), INCOMPATIBLE_STORAGE);
    }

    @Test void integerStorageNeedsMatchingIntegerFetch() {
        var metadata = target("metadata", HALF, TargetFormat.RGBA32I, EXACT_SIGNED_INT32_METADATA, 4);
        var output = target("output", FULL, TargetFormat.RGBA16F, GENERAL, 4);
        for (var fetch : PassDeclaration.Fetch.values()) {
            var consumer = new PassDeclaration("example:consumer", List.of(new PassDeclaration.Read(metadata.id(), fetch)),
                    List.of(new PassDeclaration.DrawGroup(List.of(output.id()), FULL)),
                    PassDeclaration.FeedbackPolicy.UNSUPPORTED);
            var validation = new AdapterLayout(List.of(metadata, output), List.of(consumer)).validate(SURFACE);
            if (fetch == PassDeclaration.Fetch.SIGNED_INTEGER) assertTrue(validation.valid());
            else has(validation, INCOMPATIBLE_FETCH);
        }
    }

    @Test void feedbackFailsUnlessDistinctImageProofIsExplicitlyDeclared() {
        var image = target("image", FULL, TargetFormat.RGBA32F, GENERAL, 4);
        for (var policy : PassDeclaration.FeedbackPolicy.values()) {
            var draw = new PassDeclaration("example:feedback", List.of(new PassDeclaration.Read(image.id(),
                    PassDeclaration.Fetch.FLOAT_OR_NORMALIZED)), List.of(
                    new PassDeclaration.DrawGroup(List.of(image.id()), FULL)), policy);
            var result = new AdapterLayout(List.of(image), List.of(draw)).validate(SURFACE);
            if (policy == PassDeclaration.FeedbackPolicy.UNSUPPORTED) has(result, UNSUPPORTED_FEEDBACK);
            else assertTrue(result.valid());
        }
    }

    @Test void fullSizeInterleavedLinearAndBoundedCertificateLayoutIsExplicitAndFetchSafe() {
        var interleaved = new TargetDeclaration("example:interleaved", FULL, TargetFormat.RGBA32F, List.of(
                new TargetDeclaration.Quantity("example:linear_basis", LINEAR_QUANTITY, 4),
                new TargetDeclaration.Quantity("example:certificate", EXACT_BINARY32_BOUNDED_INTEGER_METADATA,
                        3, 1L << 24),
                new TargetDeclaration.Quantity("example:minimum_distance", BINARY32_MINIMUM_DISTANCE, 1)));
        var output = target("output", FULL, TargetFormat.RGBA16F, GENERAL, 4);
        for (var fetch : PassDeclaration.Fetch.values()) {
            var consumer = new PassDeclaration("example:consumer", List.of(new PassDeclaration.Read(interleaved.id(), fetch)),
                    List.of(new PassDeclaration.DrawGroup(List.of(output.id()), FULL)),
                    PassDeclaration.FeedbackPolicy.UNSUPPORTED);
            var result = new AdapterLayout(List.of(interleaved, output), List.of(
                    pass(FULL, "interleaved"), consumer)).validate(new Dimensions(1709, 961));
            if (fetch == PassDeclaration.Fetch.FLOAT_TEXEL_FETCH) assertTrue(result.valid());
            else has(result, INCOMPATIBLE_FETCH);
        }
    }

    @Test void boundedIntegerDeclarationRejectsUnprovedDomainsAndInexactStorage() {
        for (long bound : new long[] {0, -1, (1L << 24) + 1, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new TargetDeclaration.Quantity("example:certificate",
                    EXACT_BINARY32_BOUNDED_INTEGER_METADATA, 4, bound));
        }
        for (var format : List.of(TargetFormat.RGBA16F, TargetFormat.RGBA8, TargetFormat.RGBA32I)) {
            var target = new TargetDeclaration("example:metadata", FULL, format,
                    new TargetDeclaration.Quantity("example:certificate", EXACT_BINARY32_BOUNDED_INTEGER_METADATA,
                            4, 1L << 24));
            has(new AdapterLayout(List.of(target), List.of(pass(FULL, "metadata"))).validate(SURFACE),
                    INCOMPATIBLE_STORAGE);
        }
        assertEquals(16777216f, (float) (1L << 24));
        assertEquals((float) (1L << 24), (float) ((1L << 24) + 1));
    }

    @Test void packedFloatingFormatReportsItsActualUnsignedRgbPrecision() {
        var packed = TargetFormat.R11F_G11F_B10F;
        assertEquals(3, packed.channels());
        assertEquals(TargetFormat.NumericClass.FLOAT, packed.numericClass());
        assertEquals(10, packed.componentBits());
        assertEquals(List.of(11, 11, 10), List.of(packed.componentBits(0), packed.componentBits(1), packed.componentBits(2)));
        assertFalse(packed.hasFloatingSignBit());
        assertEquals(5, packed.floatingExponentBits());
        assertEquals(List.of(6, 6, 5), List.of(packed.floatingFractionBits(0), packed.floatingFractionBits(1),
                packed.floatingFractionBits(2)));
        assertThrows(IndexOutOfBoundsException.class, () -> packed.componentBits(3));
        assertEquals(32, TargetFormat.RGBA32F.componentBits());
        assertEquals(23, TargetFormat.RGBA32F.floatingFractionBits(0));
        assertTrue(TargetFormat.RGBA32F.hasFloatingSignBit());
        assertEquals(10, TargetFormat.RGBA16F.floatingFractionBits(0));
        assertThrows(IllegalStateException.class, () -> TargetFormat.RGBA32I.floatingFractionBits(0));
    }

    @Test void packedFloatingTargetPermitsThreeChannelQuantityAndCoherentMixedFormatMrt() {
        var sky = target("sky", FULL, TargetFormat.R11F_G11F_B10F, LINEAR_QUANTITY, 3);
        var cache = target("cache", FULL, TargetFormat.RGBA32F, GENERAL, 4);
        var result = new AdapterLayout(List.of(sky, cache), List.of(pass(FULL, "sky", "cache"))).validate(SURFACE);
        assertTrue(result.valid(), () -> result.problems().toString());
        var producer = new PassDeclaration("example:producer", List.of(new PassDeclaration.Read(sky.id(),
                PassDeclaration.Fetch.FLOAT_OR_NORMALIZED)), List.of(
                new PassDeclaration.DrawGroup(List.of(sky.id(), cache.id()), FULL)),
                PassDeclaration.FeedbackPolicy.DISTINCT_IMAGES_EXTERNALLY_VERIFIED);
        assertTrue(new AdapterLayout(List.of(sky, cache), List.of(producer)).validate(SURFACE).valid());
    }

    @Test void packedFloatingTargetRejectsAlphaAndAllExactCertificateRoles() {
        var packed = TargetFormat.R11F_G11F_B10F;
        var rgba = target("rgba", FULL, packed, LINEAR_QUANTITY, 4);
        has(new AdapterLayout(List.of(rgba), List.of(pass(FULL, "rgba"))).validate(SURFACE), INCOMPATIBLE_STORAGE);
        for (var role : List.of(EXACT_SIGNED_INT32_METADATA, EXACT_UNSIGNED_INT32_METADATA,
                BINARY32_MINIMUM_DISTANCE, EXACT_BINARY32_BOUNDED_INTEGER_METADATA)) {
            var quantity = role == EXACT_BINARY32_BOUNDED_INTEGER_METADATA
                    ? new TargetDeclaration.Quantity("example:metadata", role, 1, 1L << 24)
                    : new TargetDeclaration.Quantity("example:metadata", role, 1);
            var metadata = new TargetDeclaration("example:metadata", FULL, packed, quantity);
            has(new AdapterLayout(List.of(metadata), List.of(pass(FULL, "metadata")))
                    .validate(SURFACE), INCOMPATIBLE_STORAGE);
        }
    }

    @Test void unknownReadsAndAttachmentsFailWithoutHidingOtherFailures() {
        var known = target("known", FULL, TargetFormat.RGBA32F, GENERAL, 4);
        var pass = new PassDeclaration("example:unknown", List.of(new PassDeclaration.Read("example:absent",
                PassDeclaration.Fetch.SIGNED_INTEGER)), List.of(
                new PassDeclaration.DrawGroup(List.of("example:absent", known.id()), HALF)),
                PassDeclaration.FeedbackPolicy.UNSUPPORTED);
        var result = new AdapterLayout(List.of(known), List.of(pass)).validate(SURFACE);
        assertEquals(2, result.problems().stream().filter(p -> p.code() == UNKNOWN_TARGET).count());
        has(result, VIEWPORT_UNDERSIZED);
    }

    @Test void declarationsAreDefensivelyCopiedAndMalformedSchemasFailEarly() {
        var image = target("image", FULL, TargetFormat.RGBA32F, GENERAL, 4);
        var ids = new ArrayList<>(List.of(image.id()));
        var group = new PassDeclaration.DrawGroup(ids, FULL);
        ids.clear();
        assertEquals(List.of(image.id()), group.attachments());
        var targets = new ArrayList<>(List.of(image));
        var passes = new ArrayList<>(List.of(pass(FULL, "image")));
        var layout = new AdapterLayout(targets, passes);
        targets.clear(); passes.clear();
        assertTrue(layout.validate(SURFACE).valid());
        assertThrows(UnsupportedOperationException.class, () -> layout.targets().clear());
        assertThrows(UnsupportedOperationException.class, () -> group.attachments().clear());
        var result = layout.validate(SURFACE);
        assertThrows(UnsupportedOperationException.class, () -> result.targetDimensions().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.problems().clear());
        assertThrows(IllegalArgumentException.class, () -> new AdapterLayout(List.of(image, image), layout.passes()));
        assertThrows(IllegalArgumentException.class, () -> new AdapterLayout(layout.targets(),
                List.of(layout.passes().getFirst(), layout.passes().getFirst())));
        assertThrows(IllegalArgumentException.class, () -> new PassDeclaration("example:empty", List.of(),
                List.of(), PassDeclaration.FeedbackPolicy.UNSUPPORTED));
        assertThrows(IllegalArgumentException.class, () -> new PassDeclaration.DrawGroup(List.of(image.id(), image.id()), FULL));
        assertThrows(IllegalArgumentException.class, () -> new TargetSize.Ratio(0, 1, CEIL));
        assertThrows(IllegalArgumentException.class, () -> new Dimensions(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> target("bad key", FULL, TargetFormat.R32F, GENERAL, 1));
    }

}

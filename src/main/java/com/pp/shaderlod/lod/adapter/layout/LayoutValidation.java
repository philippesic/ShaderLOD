package com.pp.shaderlod.lod.adapter.layout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded structural checks over declarations; validity is not driver or shader acceptance. */
public record LayoutValidation(Map<String, Dimensions> targetDimensions, List<Problem> problems) {
    public enum Code {
        SIZE_OUT_OF_RANGE, UNKNOWN_TARGET, INCOMPATIBLE_STORAGE, INCOMPATIBLE_FETCH,
        UNEQUAL_DRAW_ATTACHMENTS, VIEWPORT_UNDERSIZED, VIEWPORT_OVERSIZED, UNSUPPORTED_FEEDBACK
    }
    public record Problem(Code code, String location, String detail) {}

    public LayoutValidation {
        targetDimensions = Map.copyOf(targetDimensions);
        problems = List.copyOf(problems);
    }

    public boolean valid() { return problems.isEmpty(); }

    static LayoutValidation validate(AdapterLayout layout, Dimensions surface) {
        Objects.requireNonNull(surface, "renderedSurface");
        var dimensions = new LinkedHashMap<String, Dimensions>();
        var targets = new LinkedHashMap<String, TargetDeclaration>();
        var problems = new ArrayList<Problem>();
        for (var target : layout.targets()) {
            targets.put(target.id(), target);
            resolve(target.size(), surface, target.id(), dimensions, problems);
            if (!target.quantities().stream().allMatch(quantity -> compatibleStorage(target.format(), quantity))) {
                problems.add(new Problem(Code.INCOMPATIBLE_STORAGE, target.id(),
                        target.format() + " cannot store " + target.quantities()));
            }
        }
        for (var pass : layout.passes()) {
            for (var read : pass.reads()) {
                var target = targets.get(read.target());
                if (target == null) {
                    problems.add(new Problem(Code.UNKNOWN_TARGET, pass.id(), read.target()));
                } else if (!compatibleFetch(target, read.fetch())) {
                    problems.add(new Problem(Code.INCOMPATIBLE_FETCH, pass.id(), read.toString()));
                }
            }
            for (int index = 0; index < pass.draws().size(); index++) {
                var draw = pass.draws().get(index);
                String location = pass.id() + "/draw/" + index;
                var viewportMap = new LinkedHashMap<String, Dimensions>();
                resolve(draw.viewport(), surface, location, viewportMap, problems);
                var viewport = viewportMap.get(location);
                Dimensions first = null;
                for (var attachment : draw.attachments()) {
                    if (!targets.containsKey(attachment)) {
                        problems.add(new Problem(Code.UNKNOWN_TARGET, location, attachment));
                        continue;
                    }
                    if (pass.feedbackPolicy() == PassDeclaration.FeedbackPolicy.UNSUPPORTED
                            && pass.reads().stream().anyMatch(read -> read.target().equals(attachment))) {
                        problems.add(new Problem(Code.UNSUPPORTED_FEEDBACK, location, attachment));
                    }
                    var current = dimensions.get(attachment);
                    if (current == null) continue;
                    if (first == null) first = current;
                    else if (!first.equals(current)) {
                        problems.add(new Problem(Code.UNEQUAL_DRAW_ATTACHMENTS, location,
                                attachment + "=" + current + ", first=" + first));
                    }
                    if (viewport != null) {
                        if (viewport.width() < current.width() || viewport.height() < current.height()) {
                            problems.add(new Problem(Code.VIEWPORT_UNDERSIZED, location, attachment));
                        }
                        if (viewport.width() > current.width() || viewport.height() > current.height()) {
                            problems.add(new Problem(Code.VIEWPORT_OVERSIZED, location, attachment));
                        }
                    }
                }
            }
        }
        return new LayoutValidation(dimensions, problems);
    }

    private static void resolve(TargetSize size, Dimensions surface, String location,
                                Map<String, Dimensions> dimensions, List<Problem> problems) {
        try {
            dimensions.put(location, size.resolve(surface));
        } catch (IllegalArgumentException failure) {
            problems.add(new Problem(Code.SIZE_OUT_OF_RANGE, location, failure.getMessage()));
        }
    }

    private static boolean compatibleStorage(TargetFormat format, TargetDeclaration.Quantity quantity) {
        if (format.channels() < quantity.channels()) return false;
        return switch (quantity.role()) {
            case GENERAL -> true;
            case LINEAR_QUANTITY -> format.numericClass() == TargetFormat.NumericClass.FLOAT;
            case EXACT_SIGNED_INT32_METADATA -> format.numericClass() == TargetFormat.NumericClass.SIGNED_INTEGER
                    && format.componentBits() == 32;
            case EXACT_UNSIGNED_INT32_METADATA -> format.numericClass() == TargetFormat.NumericClass.UNSIGNED_INTEGER
                    && format.componentBits() == 32;
            case EXACT_BINARY32_BOUNDED_INTEGER_METADATA, BINARY32_MINIMUM_DISTANCE ->
                    format.numericClass() == TargetFormat.NumericClass.FLOAT
                    && format.componentBits() == 32;
        };
    }

    private static boolean compatibleFetch(TargetDeclaration target, PassDeclaration.Fetch fetch) {
        if (target.quantities().stream().anyMatch(quantity ->
                quantity.role() == TargetDeclaration.Role.EXACT_BINARY32_BOUNDED_INTEGER_METADATA
                        || quantity.role() == TargetDeclaration.Role.BINARY32_MINIMUM_DISTANCE)
                && fetch != PassDeclaration.Fetch.FLOAT_TEXEL_FETCH) return false;
        return switch (target.format().numericClass()) {
            case FLOAT, NORMALIZED -> fetch == PassDeclaration.Fetch.FLOAT_OR_NORMALIZED
                    || fetch == PassDeclaration.Fetch.FLOAT_TEXEL_FETCH;
            case SIGNED_INTEGER -> fetch == PassDeclaration.Fetch.SIGNED_INTEGER;
            case UNSIGNED_INTEGER -> fetch == PassDeclaration.Fetch.UNSIGNED_INTEGER;
        };
    }
}

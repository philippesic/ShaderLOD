package com.pp.shaderlod.fabric;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Catch stripped nested-library discovery before attempting an expensive world launch. */
class IrisBundledRuntimeTest {
    @Test void allPinnedIrisLibraryEntryClassesLinkOnTheOrdinaryRuntimeClasspath() throws Exception {
        for (String name : new String[]{"org.anarres.cpp.LexerException", "org.anarres.cpp.Preprocessor",
                "org.antlr.v4.runtime.Lexer", "io.github.douira.glsl_transformer.ast.transform.ASTTransformer"}) {
            var linked = Class.forName(name);
            assertNotNull(linked);
            System.out.println(name + " -> " + linked.getProtectionDomain().getCodeSource().getLocation());
        }
    }
}

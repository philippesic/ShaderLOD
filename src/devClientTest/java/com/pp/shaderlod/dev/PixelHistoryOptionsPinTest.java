package com.pp.shaderlod.dev;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PixelHistoryOptionsPinTest {
    private static String digest(TreeMap<String,String> values) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(new GsonBuilder().disableHtmlEscaping().create().toJson(values).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void completeActualMapPinHasNoGuessedCountAndRejectsAnyUnknownKeyDrift() throws Exception {
        var options=new JsonObject(); options.addProperty("z_unknown","<x>"); options.addProperty("a_option","true");
        var sorted=new TreeMap<String,String>();sorted.put("z_unknown","<x>");sorted.put("a_option","true");String hash=digest(sorted);
        assertDoesNotThrow(()->PixelHistorySession.requireOptionsPin(options,hash,hash));
        options.addProperty("new_helper_guard","true");
        assertThrows(IllegalStateException.class,()->PixelHistorySession.requireOptionsPin(options,hash,hash));
        options.remove("new_helper_guard");
        assertThrows(IllegalStateException.class,()->PixelHistorySession.requireOptionsPin(options,hash,"0".repeat(64)));
        assertThrows(IllegalStateException.class,()->PixelHistorySession.requireOptionsPin(options,"not a hash",hash));
        options.addProperty("a_option",true);
        assertThrows(IllegalStateException.class,()->PixelHistorySession.requireOptionsPin(options,hash,hash));
    }
    @Test void blankPinRetainsExactlyTheOriginal368OptionCount() {
        var options=new JsonObject();for(int i=0;i<368;i++)options.addProperty("option"+i,"true");
        assertDoesNotThrow(()->PixelHistorySession.requireOptionsPin(options,"","legacy producer hash"));
        options.addProperty("extra_guard","true");
        assertThrows(IllegalStateException.class,()->PixelHistorySession.requireOptionsPin(options,"","legacy producer hash"));
    }
}

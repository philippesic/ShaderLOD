package com.pp.shaderlod.optimization.bsl;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** Exact native API combinations captured from the ten real loader classpaths. No shader/source admission. */
public final class BslResourceApiPins {
    private static final boolean EXACT=check();
    private BslResourceApiPins() {}
    public static boolean exactApi() {return EXACT;}
    private static boolean check() {
        try(var input=BslResourceApiPins.class.getResourceAsStream("/assets/shaderlod/adapters/qualification/bsl-resource-api.json")) {
            if(input==null)return false;byte[] all=input.readAllBytes();if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(all)).equals("c3416d874870afb89c70284978bb7c23781ac9bbf0522dc0beae2e6fefa6e6af"))return false;var rows=JsonParser.parseString(new String(all,StandardCharsets.UTF_8)).getAsJsonArray();
            for(var row:rows) {boolean match=true;
                for(var pin:row.getAsJsonObject().getAsJsonObject("classes").entrySet()) {
                    try(var bytes=BslResourceApiPins.class.getClassLoader().getResourceAsStream(pin.getKey())) {
                        if(bytes==null || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes())).equals(pin.getValue().getAsString())) {match=false;break;}
                    }
                }
                if(match)return true;
            }
        }catch(Exception|LinkageError unavailable) {}return false;
    }
}

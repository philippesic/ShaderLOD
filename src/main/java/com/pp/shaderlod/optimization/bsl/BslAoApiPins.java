package com.pp.shaderlod.optimization.bsl;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
/** Exact native API combinations captured from the ten real loader classpaths. No shader/source admission. */
public final class BslAoApiPins {
    private static final String BACKEND=check();
    private BslAoApiPins() {}
    public static boolean exactApi() {return !BACKEND.isEmpty();}
    public static String backend(){return BACKEND;}
    private static String check() {
        try(var input=BslAoApiPins.class.getResourceAsStream("/assets/shaderlod/adapters/qualification/bsl-ao-api.json")) {
            if(input==null)return "";byte[] all=input.readAllBytes();if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(all)).equals("82351e739d7586d25b2cac08f9c6734db8ea4df6a2546c9e980dc1142e29ca08"))return "";var rows=JsonParser.parseString(new String(all,StandardCharsets.UTF_8)).getAsJsonArray();
            for(var row:rows) {boolean match=true;
                for(var pin:row.getAsJsonObject().getAsJsonObject("classes").entrySet()) {
                    try(var bytes=BslAoApiPins.class.getClassLoader().getResourceAsStream(pin.getKey())) {
                        if(bytes==null || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.readAllBytes())).equals(pin.getValue().getAsString())) {match=false;break;}
                    }
                }
                if(match)return row.getAsJsonObject().get("backend").getAsString();
            }
        }catch(Exception|LinkageError unavailable) {}return "";
    }
}

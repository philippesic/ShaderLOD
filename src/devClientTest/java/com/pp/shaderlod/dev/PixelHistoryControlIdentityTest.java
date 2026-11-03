package com.pp.shaderlod.dev;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PixelHistoryControlIdentityTest {
    @TempDir Path directory;
    @Test void contentAndMtimeMutationsCannotHideBehindTheSameCameraOrSize() throws Exception {
        Path file=directory.resolve("control.json"); Files.writeString(file,"{\"enabled\":0}");
        var first=PixelHistorySession.readControlIdentity(file);
        assertEquals(first,PixelHistorySession.readControlIdentity(file));
        Files.writeString(file,"{\"enabled\":1}"); Files.setLastModifiedTime(file,FileTime.fromMillis(first.modifiedMillis()));
        var changed=PixelHistorySession.readControlIdentity(file);
        assertEquals(first.bytes(),changed.bytes()); assertEquals(first.modifiedMillis(),changed.modifiedMillis());
        assertNotEquals(first,changed);
        Files.writeString(file,"{\"enabled\":0}"); Files.setLastModifiedTime(file,FileTime.fromMillis(first.modifiedMillis()+5000));
        assertNotEquals(first,PixelHistorySession.readControlIdentity(file));
    }
    @Test void missingSymlinkAndOversizedControlsFail() throws Exception {
        Path missing=directory.resolve("missing.json"); assertThrows(IllegalStateException.class,()->PixelHistorySession.readControlIdentity(missing));
        Path file=directory.resolve("control.json"); Files.write(file,new byte[65537]);
        assertThrows(IllegalStateException.class,()->PixelHistorySession.readControlIdentity(file));
        Path link=directory.resolve("link.json"); Files.createSymbolicLink(link,file);
        assertThrows(IllegalStateException.class,()->PixelHistorySession.readControlIdentity(link));
    }
}

package com.pp.shaderlod.packs;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Loader-independent source recognition. No entry is runtime-supported yet. */
public final class SupportedPackCatalog {
    private SupportedPackCatalog() {}
    public enum Stage { RECOGNIZED_ORIGINAL, ASSEMBLED_ADAPTER }
    public record Pack(String id, String name, String version, String primaryPage,
            String licenseId, String originalSourceDigest, String adapterSourceDigest) {
        public boolean integrated() { return adapterSourceDigest != null; }
        public boolean runtimeValidated() { return false; }
        public boolean supported() { return runtimeValidated(); }
        /** Observed local failures are separate from source integration and general support. */
        public String localRuntimeBoundary() {
            return switch (id) {
                case "solas-shader" -> "Original and adapter hit Iris GlImage/ARBClearTexture failure on the development Mac";
                case "astralex" -> "Original and adapter hit a native Apple shader-linker crash on the development Mac";
                default -> "Broad runtime, DH, PBR and platform validation remains pending";
            };
        }
    }
    public record Recognition(Pack pack, Stage stage) {}
    private static final List<Pack> PACKS = List.of(
        new Pack("photon", "Photon", "v1.3b", "https://modrinth.com/shader/photon-shader",
            "LicenseRef-Photon", "ff61637361dbac1430c56d183276a1f343787b1517c7fb6487dc07be367221d9", "a65bfda59e34d2d6bfb972ff6c241736eb741aa6156ca3ad9f6f75d7fbc870ea"),
        new Pack("astralex", "AstraLex Shaders", "93.0", "https://modrinth.com/shader/astralex",
            "LicenseRef-All-Rights-Reserved", "e89fe2686d5a73d18ae9aae5104da24f6cb23ae79f1012f95b79aa78e655485a", "5d681abe1c8ae6040ff01fcdc2dec140c83ed65a6da0716ca01ec33dd3ff3456"),
        new Pack("bliss-shader", "Bliss Shaders", "2.1.2", "https://modrinth.com/shader/bliss-shader",
            "LicenseRef-All-Rights-Reserved", "dc7ef8dd62fb9db1ca500c1d300f206891035e5129dab81bf19c901f526ac2e4", "e198a1d6a560ac1d131817ef5d84ac2163db7578a1f200bf26731e670d5491e4"),
        new Pack("bsl-shaders", "BSL Shaders", "10.1.8", "https://modrinth.com/shader/bsl-shaders",
            "LicenseRef-All-Rights-Reserved", "198235a39479dee0964f272104844ff2ee32fd4438309a54d1421278986066bb", "c9bc559a7f2455d7ea5c316d36f92919821cfd614d27d00f9478de93d64eef72"),
        new Pack("complementary-reimagined", "Complementary Shaders - Reimagined", "r5.9.3", "https://modrinth.com/shader/complementary-reimagined",
            "LicenseRef-Custom", "95e2a1eabb73a72b01422d06a6f636de85d4ee136358e16a1c762a55633cfef0", "ccee665d9abeebf0d2f08fd3be96c52d17857e1160480062b530ecb31a4bcfa7"),
        new Pack("complementary-unbound", "Complementary Shaders - Unbound", "r5.9.3", "https://modrinth.com/shader/complementary-unbound",
            "LicenseRef-Custom", "2212a1f3d796c70a337eabae6cd7e0309917f95434a5aaa31d8e77ebe6a5dc39", "c0d104b0f7b5094292e67bd693e625d8aa8b936a815695715044f2a6cb0f4930"),
        new Pack("makeup-ultra-fast-shaders", "MakeUp - Ultra Fast", "9.5g", "https://modrinth.com/shader/makeup-ultra-fast-shaders",
            "LGPL-3.0-or-later", "5077d90851e1f07aa9bc2551eadcc1d9b270694c9f3761924fd800188bebbf32", "3be1ceaf03d99e03bfe54ef814cc7cfab26cc709bea666a6e11139fa4ad9de70"),
        new Pack("sildurs-vibrant-shaders", "Sildur's Vibrant Shaders", "2.02", "https://modrinth.com/shader/sildurs-vibrant-shaders",
            "LicenseRef-All-Rights-Reserved", "bcd7fa0121e9b6184d4613ffced7c634f09c6b33e25e7bcfa0088eb06cb275ea", "81ac02a283d074496e6dee3110f9d873197cf1d588e8fe3da03a9e523520d80d"),
        new Pack("solas-shader", "Solas Shader", "3.7b", "https://modrinth.com/shader/solas-shader",
            "LicenseRef-All-Rights-Reserved", "f38d0fb61e858690ce3b40b0dfc8a0bd3212b4f3dbd42d0aa1c354dcdda35640", "0e35b446c0d3a1c64fa06651581f4c3f4a0d5ad70a13b7e9bfafd3fe3ab3efb5")
    );
    public static List<Pack> inventory() { return PACKS; }

    private static final List<String> SOURCE_EXTENSIONS = List.of(".glsl", ".fsh", ".vsh", ".gsh",
        ".csh", ".tcs", ".tes", ".vert", ".frag", ".geom", ".comp", ".tesc", ".tese");

    /** Used by archive scanners to skip textures while hashing every executable/control member. */
    public static boolean isSourceMember(String name) {
        Objects.requireNonNull(name, "name");
        return SOURCE_EXTENSIONS.stream().anyMatch(name::endsWith)
            || (name.startsWith("shaders/") && name.endsWith(".properties"));
    }

    // Retained exact historical derivatives remain recognizable; current recipe/state guards still apply.
    private static final Map<String,List<String>> LEGACY_ADAPTER_DIGESTS = Map.ofEntries(
        Map.entry("photon", List.of("5bd3e75d890eee470a1aecd35999689d9b774d6768807c5aee074f626efc775a", "d351a22f2766f0297206df478e5ec190172a4ce673423970b6b813ec7f3dd3c6")),
        Map.entry("sildurs-vibrant-shaders", List.of("b4d5964d0f019f1c64b80523c87ea2d6cab9d64ef4a7e1e3bd02539b53b7c777")),
        Map.entry("bliss-shader", List.of("cd3e678a6ecb529f59e13421b1cd27bcdbb86012c4c482e570e2eb86abb2e9f4")),
        Map.entry("complementary-reimagined", List.of("18fcf18e20d753dac8e24309abe4b5e0f773b5c73c0bbe1bb18ae3b8f290f46b", "a342b424364b2b1d59185b584b6097648e3c11d9e499b2035012a345bdc3ed7f", "920212803e992b80ba58da1e00a54bc45b4ad5ddac1edae1b392002c90ee2cc7", "94a04324f6a93cba53889df0638be00768ad7c5d87f11f2c23e535ed0f0894cb")),
        Map.entry("complementary-unbound", List.of("cd60c29d8d3f2e1437b965f9c32ec0e41c95df0f25f47d18e7fb7b45377476fc", "4e6cd8da53dfb1a31504b31a82027ccac4eb8b3c99449640b8f8ac77a173be8a", "9118b112be859fda810582c78dd1409d0273cbe8fa9ed72cc32a22c9ddc09ed6")),
        Map.entry("makeup-ultra-fast-shaders", List.of("ee4d9d08fc9e071cdffb1d63c920368cc7c5b1b05623d3f5a82c27505bdb4a51", "e62be2858dc69faf7a24dd5ebc3806e697302c3c8c8d46391051ba71c8ab7d9d", "52f14a1d31e0be6e24a3052578089305735f5c88cd9930fa6dd518839953ca58", "956e3ab528c0ee9504bbc307306f269c9a85d3613a0dc5b49cfb80472ea4e5c4", "9a6c5ebb5dc6ab2b99c4fd8ea75fdcd19af468a75ee0a81ee99516a7886bb38a", "1978eed85ffdbc53d65c687253295e3c200016a75c84045a2f5240d3443ff0b5")),
        Map.entry("bsl-shaders", List.of("37aa37afaea8fbcbdf4adbc49dba3681280f66a94f25296f9be90546ef9e4ae8", "80dfa11232b5c04cd075fb36ff4dffc8b55fbe1cd64d4cdb707ad8bff254f140", "f386c864e7a8e39752982a332f963af8093dbb326094255a8428ae663bc728e1", "5be3da36fc1812c220f5ecf00b904a783c0286fa629630038074abaf8073d4ba")),
        Map.entry("solas-shader", List.of("7e5171e0c1e780bc33e57401a8561d296d73b826d3bfd0d0d21965080e213a6d", "469a65f1654793dd5e9dca70c91746e98aef2df240a601f8da3d5cc5934f6cf5", "8b96d3a6b8cfc4580ae47604ad8fd114de802e79ac75206374d2f5c97b129480", "8e7021d657d17584e186c26a6d908bf467e0e0d44fd7daaf89b0dc44e1c614c6")),
        Map.entry("astralex", List.of("5512a3f003fcb881903d5d1db6d230903c4259967962423de745d49bb75f6c7e", "64074489150514b0e4b180f1ff59986712dd9984a0d5978051e88677d371944f", "7a59f0be267d52c977371e9cf621e401a93030a7107f6b26410599e80ed4ae91", "06ddd92b746a100e417ee98b690e4dedc104b4395c614fc951da80912eb40197")));

    /** Input hashes are lowercase SHA-256 of actual members, never filenames/options/metadata. */
    public static Optional<Recognition> recognize(Map<String, String> memberHashes) {
        String digest = shaderSourceDigest(memberHashes);
        for (Pack pack : PACKS) {
            if (digest.equals(pack.originalSourceDigest()))
                return Optional.of(new Recognition(pack, Stage.RECOGNIZED_ORIGINAL));
            if (digest.equals(pack.adapterSourceDigest()) || LEGACY_ADAPTER_DIGESTS.getOrDefault(pack.id(), List.of()).contains(digest))
                return Optional.of(new Recognition(pack, Stage.ASSEMBLED_ADAPTER));
        }
        return Optional.empty();
    }

    /** Archive-independent identity of executable source and option profile; ignores README/lang/ZIP metadata. */
    public static String shaderSourceDigest(Map<String, String> memberHashes) {
        Objects.requireNonNull(memberHashes, "memberHashes");
        StringBuilder identity = new StringBuilder();
        new TreeMap<>(memberHashes).forEach((name, hash) -> {
            if (isSourceMember(name)) {
                if (name.startsWith("/") || name.contains("\\") || List.of(name.split("/")).contains("..")
                        || name.indexOf(0) >= 0 || name.contains("\n") || name.contains("\r")
                        || hash == null || !hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid source member/hash");
                identity.append(name).append('\0').append(hash).append('\n');
            }
        });
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(identity.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
}

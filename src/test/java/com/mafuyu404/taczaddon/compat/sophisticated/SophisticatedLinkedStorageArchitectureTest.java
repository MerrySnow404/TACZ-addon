package com.mafuyu404.taczaddon.compat.sophisticated;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SophisticatedLinkedStorageArchitectureTest {
    private static final Path COMPAT = Path.of("src/main/java/com/mafuyu404/taczaddon/compat/sophisticated");

    private static String source(String name) throws Exception {
        return Files.readString(COMPAT.resolve(name + ".java")).replaceAll("\\s+", "");
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature.replaceAll("\\s+", ""));
        assertTrue(start >= 0, signature);
        int open = source.indexOf('{', start);
        int depth = 1;
        int end = open + 1;
        while (depth > 0) {
            char c = source.charAt(end++);
            if (c == '{') depth++;
            if (c == '}') depth--;
        }
        return source.substring(open, end);
    }

    @Test
    void newApiReferencesAreIsolatedAndOldGenerationLoadsSilently() throws Exception {
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                for (String forbidden : new String[] {"RequestLinkedStorageBackpackContentsPayload",
                        "LinkedStorageBackpackContentsPayload", "ModCoreDataComponents.LINKED_STORAGE_ENDPOINT",
                        "PacketDistributor.sendToServer", "RegistryFriendlyByteBuf"}) {
                    assertFalse(text.contains(forbidden), file.toString());
                }
                if (file.getFileName().toString().equals("SophisticatedLinkedStorageCompat326.java")) continue;
                if (file.getFileName().toString().equals("SophisticatedBlockStorageCompatInner.java")) continue;
                for (String line : text.lines().filter(l -> l.startsWith("import ")).toList()) {
                    assertFalse(line.contains("sophisticatedcore.linkedstorage"), file.toString());
                    assertFalse(line.contains("LinkedStorageBackpackContentsMessage"), file.toString());
                }
            }
        }
        String facade = source("SophisticatedLinkedStorageCompat");
        assertTrue(facade.contains("Class.forName(IMPL_CLASS,true,loader)"));
        /*
         * Generation detection must run before the optional backend class is
         * named, and an ordinary-only generation must stay a silent no-op so
         * ordinary backpack support is never disabled by it.
         */
        assertTrue(facade.contains("SophisticatedBackpackGeneration.detect("));
        assertTrue(facade.contains("generation.linkedStorageSupported()"));
        assertTrue(facade.contains("returnNoopBridge.INSTANCE"));
        assertTrue(facade.indexOf("linkedStorageSupported")
                < facade.indexOf("Class.forName(IMPL_CLASS"));
        String noop = facade.substring(
                facade.indexOf("if(!generation.linkedStorageSupported())"),
                facade.indexOf("ClassLoaderloader="));
        assertTrue(noop.contains("returnNoopBridge.INSTANCE"));
        assertFalse(noop.contains("Class.forName"));
        assertFalse(facade.contains("breakLinkage"));
        assertFalse(facade.contains("catch(Throwable"));
        for (String signature : new String[] {"static EndpointResolution resolve(",
                "static void requestSnapshot(", "static void sendSnapshot("}) {
            assertTrue(method(facade, signature).contains("catch(LinkageErrorerror)"));
        }
    }

    @Test
    void bootstrapDeduplicatesIndependentNamespacesAndMalformedEndpointsCannotFallThrough() throws Exception {
        String sync = method(source("SophisticatedBackpacksCompatInner"), "public static void syncAllBackpack(");
        assertTrue(sync.contains("Set<UUID>requestedStorageUuids=newHashSet<>()"));
        assertTrue(sync.contains("Set<UUID>requestedLinkedGroups=newHashSet<>()"));
        assertTrue(sync.contains("(backpack,handlerName,identifier,slot)->"));
        assertTrue(sync.indexOf("resolve(backpack)") < sync.indexOf("newBackpackContext.Item("));
        String linked = method(sync, "if (linked.linked())");
        assertTrue(linked.contains("groupIdOptional()"));
        assertTrue(linked.contains(".filter(requestedLinkedGroups::add)"));
        assertTrue(linked.contains(".requestLocatedSnapshot(groupId,handlerName,identifier,slot,false)"));
        assertTrue(linked.contains("returnfalse;"));
        assertFalse(linked.contains("RequestBackpackInventoryContentsMessage"));
        String ordinary = sync.substring(sync.indexOf("newBackpackContext.Item("));
        assertTrue(ordinary.contains(".filter(requestedStorageUuids::add)"));
        assertTrue(ordinary.contains("SophisticatedBackpackContentsRequest.create(uuid,handlerName,identifier,slot)"));
        assertFalse(ordinary.contains("requestSnapshot"));
        String impl = source("SophisticatedLinkedStorageCompat326");
        assertTrue(impl.contains("SophisticatedLinkedStorageCompatNetwork.request(groupId,-1L)"));
        assertTrue(impl.contains("endpoint==null||endpoint.groupId()==null"));
        assertTrue(impl.contains("EndpointResolution.malformedLinked()"));
    }

    @Test
    void unknownGenerationsFailClosedInEveryStoragePath() throws Exception {
        String inner = source("SophisticatedBackpacksCompatInner");
        /*
         * The inner class must not depend on one exact future concrete wrapper
         * class name; classification is delegated to the structural
         * classifier.
         */
        assertFalse(inner.contains("LinkedStorageBackpackWrapper"));
        assertFalse(inner.contains("LINKED_WRAPPER_CLASS"));
        assertTrue(inner.contains(
                "privatestaticSophisticatedBackpackClassifier.AccessaccessFor("
        ));
        /*
         * Ammo query and ammo mutation must agree: both consult the same access
         * policy, so the HUD can never count ammo the mutation path refuses.
         */
        for (String signature : new String[] {
                "private static InventoryHandler getFreshInventoryHandler(",
                "public static void syncAllBackpack(",
                "private static void syncBackpackContents(",
                "public static boolean mutateInventoryBackpacks("
        }) {
            String body = method(inner, signature);
            assertTrue(
                    body.contains("resolveAccess(")
                            || body.contains("accessFor(")
                            || body.contains("ordinaryContents()"),
                    signature + " must gate on the access policy"
            );
        }
        String query = method(
                inner,
                "public static boolean visitInventoryBackpacks("
        );
        assertTrue(
                query.contains("readable()"),
                "the ammo query path must use the same policy as mutation"
        );
        String mutation = method(
                inner,
                "public static boolean mutateInventoryBackpacks("
        );
        assertTrue(
                mutation.contains("mutationAllowed()"),
                "the mutation path must use the mutation policy"
        );
        String classifier = source("SophisticatedBackpackClassifier");
        assertTrue(classifier.contains("UNCLASSIFIED"));
        assertTrue(classifier.contains("LINKED_TYPE_MARKER"));
        assertTrue(classifier.contains("linkedstorage"));
        for (String access : new String[] {
                "ORDINARY",
                "KNOWN_LINKED",
                "MALFORMED_LINKED",
                "UNKNOWN"
        }) {
            assertTrue(
                    classifier.contains(access),
                    "access policy must distinguish " + access
            );
        }
    }

    @Test
    void mutationAndFreshnessUseOnlyTheirOwnStorageFamily() throws Exception {
        String inner = source("SophisticatedBackpacksCompatInner");
        String mutation = method(inner, "private static void syncBackpackContents(");
        String linked = method(mutation, "if (linked.linked())");
        assertTrue(linked.contains("groupIdOptional().ifPresent"));
        assertTrue(linked.contains("SophisticatedLinkedStorageCompat.sendSnapshot(player,groupId)"));
        assertTrue(linked.contains("return;"));
        assertFalse(linked.contains("newBackpackContentsMessage"));
        assertTrue(mutation.indexOf("return;") < mutation.indexOf("wrapper.getContentsUuid()"));
        assertTrue(mutation.contains("newBackpackContentsMessage("));
        assertTrue(source("SophisticatedLinkedStorageCompat326").contains(
                "SophisticatedLinkedStorageCompatNetwork.sendSnapshot(player.serverLevel(),player,groupId)"));
        String freshness = method(inner, "private static InventoryHandler getFreshInventoryHandler(");
        assertTrue(freshness.contains("resolve(wrapper.getBackpack())"));
        assertTrue(method(freshness, "if (linked.linked())").contains("returnhandler;"));
        assertTrue(freshness.indexOf("if(linked.linked())") < freshness.indexOf("wrapper.getContentsUuid()"));
        assertTrue(freshness.contains("needsClientInventoryRefresh("));
        assertTrue(freshness.contains("getOrCreateBackpackContents(uuid)"));
    }

    @Test
    void metadataAndMixinMatchJava17Forge1201() throws Exception {
        String props = Files.readString(Path.of("gradle.properties"));
        /*
         * Authoritative baseline: Minecraft 1.20.1, Forge 47.4.20 with a
         * supported range [47.3.19,), loader range [47,48), mod version 1.1.8.3. Every build
         * artifact derives from these single declarations, so the test pins the
         * declared constants and checks that mods.toml and the archive name
         * consume them rather than restating a second copy that can drift.
         */
        String forgeVersion = "47.4.20";
        String minecraftRange = "[1.20.1]";
        String forgeRange = "[47.3.19,)";
        String loaderRange = "[47,48)";
        String modVersion = "1.1.8.3";
        for (String declaration : new String[] {
                "minecraft_version=1.20.1",
                "minecraft_version_range=" + minecraftRange,
                "forge_version=" + forgeVersion,
                "forge_version_range=" + forgeRange,
                "loader_version_range=" + loaderRange,
                "mod_version=" + modVersion
        }) {
            assertTrue(props.contains(declaration), declaration);
        }
        /*
         * Guard against silently weakening this test: the declarations must
         * still be the pinned baseline, not arbitrary values.
         */
        assertFalse(props.contains("forge_version_range=[47.4.20,)"));
        assertFalse(props.contains("loader_version_range=[47,)"));
        String mods = Files.readString(Path.of("src/main/resources/META-INF/mods.toml"));
        assertTrue(mods.contains("versionRange=\"[1.1.8-hotfix],[1.1.8-hotfix2],[1.2.0]\""));
        assertTrue(mods.contains("versionRange=\"${forge_version_range}\""));
        assertTrue(mods.contains("loaderVersion=\"${loader_version_range}\""));
        assertTrue(mods.contains("version=\"${mod_version}\""));
        assertFalse(mods.contains("mod_version="));
        assertTrue(Files.readString(Path.of("build.gradle")).contains(
                "archivesName = \"${mod_id}-${mod_version}-forge-${minecraft_version}\""));
        assertTrue(Files.readString(Path.of("README.md"))
                .contains("1.1.8-hotfix"));
        assertTrue(Files.readString(Path.of("src/main/resources/taczaddon.mixins.json"))
                .replaceAll("\\s+", "").contains("\"compatibilityLevel\":\"JAVA_17\""));
    }
}

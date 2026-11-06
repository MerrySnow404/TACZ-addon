package com.mafuyu404.taczaddon.compat.tacz;

import com.mafuyu404.taczaddon.compat.tacz.contract.TaczFeature;

import com.mafuyu404.taczaddon.testutil.CompatibilityFixtures;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import java.util.stream.Stream;

import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.*;

class TaczHotfixFixtureTest {
    @TestFactory
    Stream<DynamicTest> publishedReleasesSatisfyEveryAdapterContract() {
        return Stream.of("hotfix", "hotfix2", "1.2.0").map(generation -> DynamicTest.dynamicTest(
                generation, () -> verifyRelease(generation)));
    }

    private void verifyRelease(String generation) throws Exception {
        var path = CompatibilityFixtures.jar("tacz", generation, "tacz");
        String version = generation.equals("1.2.0") ? generation : "1.1.8-" + generation;
        var profile = TaczVersionReader.profileForVersion(version);
        CompatibilityFixtures.requireVersion(path, version, "TaCZ " + generation);
        try (var jar = new JarFile(path.toFile())) {
            int checked = 0;
            for (var feature : TaczFeature.values()) {
                var contract = TaczContractRegistry.contractFor(feature, profile);
                if (contract == null || contract.classes().isEmpty()) continue;
                var result = TaczBinaryProbe.inspect(contract, name -> {
                    String resource = name.replace('.', '/') + ".class";
                    var entry = jar.getJarEntry(resource);
                    try (var input = entry != null ? jar.getInputStream(entry)
                            : resource.startsWith("com/tacz/") ? null
                            : getClass().getClassLoader().getResourceAsStream(resource)) {
                        return input == null ? null : input.readAllBytes();
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                });
                assertTrue(result.passed(), generation + " / " + feature + ": " + result.detail());
                checked++;
            }
            assertTrue(checked > 20, "Must cover the version-bound adapter contracts");
        }
    }
}

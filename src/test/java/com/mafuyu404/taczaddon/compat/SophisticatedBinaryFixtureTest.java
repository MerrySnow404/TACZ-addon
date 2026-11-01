package com.mafuyu404.taczaddon.compat;

import com.mafuyu404.taczaddon.testutil.CompatibilityFixtureGate;
import com.mafuyu404.taczaddon.testutil.CompatibilityFixtures;
import net.minecraftforge.forgespi.language.MavenVersionAdapter;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Binary smoke test against real upstream Sophisticated Backpacks generations.
 *
 * <p>Earlier coverage simulated an old generation by hiding the
 * {@code sophisticatedcore.linkedstorage} package from a class loader while
 * every other API still came from the 3.26 compile jar. That only proved
 * "3.26 minus the linked package". This test instead reads the actual upstream
 * jars and verifies every Sophisticated class and member descriptor the
 * compatibility code references against them.
 *
 * <p>The fixtures are materialized by the Gradle
 * {@code resolveCompatibilityFixtures} task and indexed by
 * {@code build/compatibility-fixtures/fixtures.properties}. Missing fixtures
 * are verification failures by default; skipping them requires the explicit
 * local-development system property
 * {@code -Dtaczaddon.compat.allowMissingFixtures=true}.
 */
class SophisticatedBinaryFixtureTest {
    private static final String BACKPACKS_PREFIX =
            "net/p3pp3rf1y/sophisticatedbackpacks/";
    private static final String CORE_PREFIX =
            "net/p3pp3rf1y/sophisticatedcore/";
    private static final String LINKED_MARKER = "linkedstorage";

    private static final String ORDINARY_INNER =
            "com/mafuyu404/taczaddon/compat/"
                    + "SophisticatedBackpacksCompatInner.class";
    private static final String LINKED_BACKEND =
            "com/mafuyu404/taczaddon/compat/"
                    + "SophisticatedLinkedStorageCompat326.class";

    /**
     * 3.25.x does not exist for the Minecraft 1.20.1 Forge upstream line.
     *
     * <p>The ordinary-only 3.24.x generation is represented by an older
     * verified release plus its terminal upstream release immediately before
     * 3.26.x. The 3.26 fixtures cover both the first linked-storage release and
     * the current verified release.
     *
     * <p>Each generation names the exact artifact pair resolved by
     * {@code resolveCompatibilityFixtures}; the tests additionally verify that
     * the paired Sophisticated Core version satisfies the dependency range
     * declared by the corresponding Sophisticated Backpacks jar.
     */
    private static final List<Fixture> FIXTURES_BY_GENERATION = List.of(
            new Fixture("3.26.19.2239", "3.26.latest",
                    SophisticatedBackpackGeneration.LINKED_STORAGE),
            new Fixture("3.26.4.2172", "3.26.4",
                    SophisticatedBackpackGeneration.LINKED_STORAGE),
            new Fixture(
                    "3.24.18.1488",
                    "3.24.18",
                    SophisticatedBackpackGeneration.ORDINARY_ONLY
            ),
            new Fixture(
                    "3.24.67.2109",
                    "3.24.67",
                    SophisticatedBackpackGeneration.ORDINARY_ONLY
            ),
            new Fixture(
                    "3.26.0.2119",
                    "3.26.0",
                    SophisticatedBackpackGeneration.LINKED_STORAGE
            ),
            new Fixture(
                    "3.26.3.2157",
                    "3.26.3",
                    SophisticatedBackpackGeneration.LINKED_STORAGE
            ),
            new Fixture(
                    "3.26.6.2176",
                    "3.26.current",
                    SophisticatedBackpackGeneration.LINKED_STORAGE
            )
    );

    private record Fixture(
            String label,
            String generation,
            SophisticatedBackpackGeneration expected
    ) {
        Path backpacks() {
            return CompatibilityFixtures.jar(
                    "sophisticated",
                    this.generation,
                    "backpacks"
            );
        }

        Path core() {
            return CompatibilityFixtures.jar(
                    "sophisticated",
                    this.generation,
                    "core"
            );
        }

        String backpacksVersion() {
            return CompatibilityFixtures.version(
                    "sophisticated",
                    this.generation,
                    "backpacks"
            );
        }

        String coreVersion() {
            return CompatibilityFixtures.version(
                    "sophisticated",
                    this.generation,
                    "core"
            );
        }
    }

    /**
     * Pins both artifacts of every generation and proves the jars really are
     * the declared upstream releases, paired the way the addon verified them.
     */
    @Test
    void pinnedGenerationsArePresentAndVersionChecked() {
        for (Fixture fixture : FIXTURES_BY_GENERATION) {
            Path backpacks = fixture.backpacks();
            Path core = fixture.core();
            CompatibilityFixtures.requireVersion(
                    backpacks,
                    fixture.backpacksVersion(),
                    "Sophisticated Backpacks " + fixture.label()
            );
            CompatibilityFixtures.requireVersion(
                    core,
                    fixture.coreVersion(),
                    "Sophisticated Core paired with "
                            + fixture.label()
            );
            /*
             * The pairing is not assumed: the Backpacks jar itself must declare
             * its Sophisticated Core dependency.
             */
            assertTrue(
                    CompatibilityFixtures.declaresDependency(
                            backpacks,
                            "sophisticatedcore"
                    ),
                    () -> "Sophisticated Backpacks " + fixture.label()
                            + " (" + backpacks.getFileName()
                            + ") does not declare a sophisticatedcore "
                            + "dependency"
            );
            /*
             * The pinned Core must also satisfy the range the Backpacks jar
             * itself declares, so a pairing can never be "some core version".
             */
            String declaredRange = CompatibilityFixtures.dependencyVersionRange(
                    backpacks,
                    "sophisticatedcore"
            );
            assertNotNull(
                    declaredRange,
                    () -> "Sophisticated Backpacks " + fixture.label()
                            + " declares no sophisticatedcore version range"
            );
            assertTrue(
                    MavenVersionAdapter.createFromVersionSpec(declaredRange)
                            .containsVersion(new DefaultArtifactVersion(
                                    fixture.coreVersion()
                            )),
                    () -> "paired Sophisticated Core "
                            + fixture.coreVersion()
                            + " does not satisfy the range "
                            + declaredRange + " declared by Sophisticated "
                            + "Backpacks " + fixture.label()
            );
        }
    }

    @TestFactory
    Stream<DynamicTest> referencedApiExistsInRealUpstreamGenerations() {
        return FIXTURES_BY_GENERATION.stream().map(fixture ->
                DynamicTest.dynamicTest(
                        fixture.label(),
                        () -> verifyGeneration(fixture)
                )
        );
    }

    private void verifyGeneration(Fixture fixture) throws Exception {
        List<Path> jars = List.of(
                fixture.backpacks(),
                fixture.core()
        );
        JarApi source = new JarApi(jars);
        assertEquals(fixture.expected() == SophisticatedBackpackGeneration.LINKED_STORAGE,
                SophisticatedBlockStorageCompat.matches(source),
                "Block endpoint bridge must recognize actual historical Core APIs");

        assertEquals(
                fixture.expected(),
                SophisticatedBackpackGeneration.detect(source),
                () -> "Sophisticated Backpacks " + fixture.label()
                        + " + Core " + fixture.coreVersion()
                        + ": generation detection failed"
        );
        assertTrue(
                SophisticatedBackpackGeneration
                        .matchesVerifiedOrdinaryContract(source),
                () -> "Sophisticated Backpacks " + fixture.label()
                        + " + Core " + fixture.coreVersion()
                        + ": the ordinary backpack contract is not satisfied"
        );

        Set<String> absentClasses = new TreeSet<>();
        Map<String, Set<String>> absentMembers = new TreeMap<>();
        verifyClass(
                ORDINARY_INNER,
                source,
                absentClasses,
                absentMembers
        );
        assertEquals(
                Collections.emptySet(),
                absentClasses,
                () -> describe(fixture, absentClasses, absentMembers)
        );
        assertEquals(
                Collections.emptyMap(),
                absentMembers,
                () -> describe(fixture, absentClasses, absentMembers)
        );

        Set<Reference> ordinaryReferences = referencesOf(
                ORDINARY_INNER
        );
        assertTrue(
                ordinaryReferences.stream().noneMatch(
                        reference -> reference.owner()
                                .toLowerCase(Locale.ROOT)
                                .contains(LINKED_MARKER)
                ),
                "ordinary compatibility must not touch linked-storage "
                        + "classes on " + fixture.label()
        );

        if (fixture.expected()
                == SophisticatedBackpackGeneration.LINKED_STORAGE) {
            assertEquals(
                    (fixture.generation().equals("3.26.current")
                            || fixture.generation().equals("3.26.latest")
                            || fixture.generation().equals("3.26.4"))
                            ? SophisticatedLinkedStorageCompatNetwork.Protocol.CORE
                            : SophisticatedLinkedStorageCompatNetwork.Protocol.BACKPACKS,
                    SophisticatedLinkedStorageCompatNetwork.Protocol.detect(source),
                    "The complete reflected network contract must match " + fixture.label()
            );
            Set<String> backendAbsentClasses = new TreeSet<>();
            Map<String, Set<String>> backendAbsentMembers =
                    new TreeMap<>();
            verifyClass(
                    LINKED_BACKEND,
                    source,
                    backendAbsentClasses,
                    backendAbsentMembers
            );
            verifyClass(
                    "com/mafuyu404/taczaddon/compat/SophisticatedBlockStorageCompatInner.class",
                    source, backendAbsentClasses, backendAbsentMembers
            );
            assertEquals(
                    Collections.emptySet(),
                    backendAbsentClasses,
                    () -> describe(
                            fixture,
                            backendAbsentClasses,
                            backendAbsentMembers
                    )
            );
            assertEquals(
                    Collections.emptyMap(),
                    backendAbsentMembers,
                    () -> describe(
                            fixture,
                            backendAbsentClasses,
                            backendAbsentMembers
                    )
            );
        }
    }

    /**
     * Actionable ABI drift report: which upstream mod/version is missing which
     * class or which exact member descriptor.
     */
    private static String describe(
            Fixture fixture,
            Set<String> absentClasses,
            Map<String, Set<String>> absentMembers
    ) {
        StringBuilder report = new StringBuilder();
        report.append("Sophisticated Backpacks ")
                .append(fixture.label())
                .append(" + Core ")
                .append(fixture.coreVersion())
                .append(" (")
                .append(CompatibilityFixtures.artifact(
                        "sophisticated",
                        fixture.generation(),
                        "backpacks"
                ))
                .append(" / ")
                .append(CompatibilityFixtures.artifact(
                        "sophisticated",
                        fixture.generation(),
                        "core"
                ))
                .append("): ABI drift detected");
        if (!absentClasses.isEmpty()) {
            report.append("\n  missing classes:");
            for (String owner : absentClasses) {
                report.append("\n    ").append(owner);
            }
        }
        if (!absentMembers.isEmpty()) {
            report.append("\n  missing members:");
            for (Map.Entry<String, Set<String>> entry
                    : absentMembers.entrySet()) {
                for (String member : entry.getValue()) {
                    report.append("\n    ")
                            .append(entry.getKey().replace('/', '.'))
                            .append('#')
                            .append(member);
                }
            }
        }
        return report.toString();
    }

    /**
     * Verifies every Sophisticated-owned class/member reference of one compiled
     * addon class against the fixture jars.
     */
    private static void verifyClass(
            String resource,
            JarApi source,
            Set<String> absentClasses,
            Map<String, Set<String>> absentMembers
    ) throws IOException {
        for (Reference reference : referencesOf(resource)) {
            if (!source.present(reference.owner())) {
                absentClasses.add(reference.owner());
                continue;
            }
            if (reference.member() == null) {
                continue;
            }
            MemberLookup lookup = source.lookup(
                    reference.owner(),
                    reference.member(),
                    reference.descriptor(),
                    reference.method()
            );
            if (lookup == MemberLookup.ABSENT) {
                absentMembers
                        .computeIfAbsent(
                                reference.owner(),
                                ignored -> new TreeSet<>()
                        )
                        .add(reference.member()
                                + reference.descriptor());
            }
        }
    }

    private static Set<Reference> referencesOf(String resource)
            throws IOException {
        ClassNode node = new ClassNode();
        try (InputStream input = SophisticatedBinaryFixtureTest.class
                .getClassLoader()
                .getResourceAsStream(resource)) {
            assertNotNull(input, resource);
            new ClassReader(input).accept(node, 0);
        }

        Set<Reference> references = new TreeSet<>();
        addInternalType(references, node.superName);
        if (node.interfaces != null) {
            for (String declared : node.interfaces) {
                addInternalType(references, declared);
            }
        }
        for (FieldNode field : node.fields) {
            addDescriptorTypes(references, field.desc);
        }
        for (MethodNode method : node.methods) {
            addDescriptorTypes(references, method.desc);
            if (method.exceptions != null) {
                for (String declared : method.exceptions) {
                    addInternalType(references, declared);
                }
            }
            for (AbstractInsnNode instruction
                    : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    addMember(
                            references,
                            call.owner,
                            call.name,
                            call.desc,
                            true
                    );
                } else if (instruction instanceof FieldInsnNode access) {
                    addMember(
                            references,
                            access.owner,
                            access.name,
                            access.desc,
                            false
                    );
                } else if (instruction instanceof TypeInsnNode type) {
                    addInternalType(references, type.desc);
                } else if (instruction
                        instanceof MultiANewArrayInsnNode array) {
                    addDescriptorTypes(references, array.desc);
                } else if (instruction instanceof LdcInsnNode ldc
                        && ldc.cst instanceof Type constant) {
                    addDescriptorTypes(
                            references,
                            constant.getDescriptor()
                    );
                }
            }
        }
        return references;
    }

    private static void addMember(
            Set<Reference> references,
            String owner,
            String name,
            String descriptor,
            boolean method
    ) {
        if (!isSophisticated(owner)) {
            return;
        }
        references.add(new Reference(
                owner,
                name,
                descriptor,
                method
        ));
        addDescriptorTypes(references, descriptor);
    }

    private static void addDescriptorTypes(
            Set<Reference> references,
            String descriptor
    ) {
        if (descriptor == null) {
            return;
        }
        try {
            if (descriptor.startsWith("(")) {
                addDescriptorTypes(
                        references,
                        Type.getReturnType(descriptor)
                );
                for (Type argument
                        : Type.getArgumentTypes(descriptor)) {
                    addDescriptorTypes(references, argument);
                }
                return;
            }
            addDescriptorTypes(references, Type.getType(descriptor));
        } catch (RuntimeException notAType) {
            // Not a descriptor; nothing to verify.
        }
    }

    private static void addDescriptorTypes(
            Set<Reference> references,
            Type type
    ) {
        if (type == null) {
            return;
        }
        if (type.getSort() == Type.ARRAY) {
            addDescriptorTypes(references, type.getElementType());
            return;
        }
        if (type.getSort() != Type.OBJECT) {
            return;
        }
        addInternalType(references, type.getInternalName());
    }

    private static void addInternalType(
            Set<Reference> references,
            String internalName
    ) {
        if (isSophisticated(internalName)) {
            references.add(new Reference(
                    internalName,
                    null,
                    null,
                    false
            ));
        }
    }

    private static boolean isSophisticated(String internalName) {
        return internalName != null
                && (internalName.startsWith(BACKPACKS_PREFIX)
                || internalName.startsWith(CORE_PREFIX));
    }

    /**
     * Reads classes from the fixture jars and resolves members the way the JVM
     * does: through the superclass chain and the implemented interfaces, not
     * only the declaring class.
     */
    private static final class JarApi
            implements ApiShapeProbe.ClassBytes {
        private final List<Path> jars;

        JarApi(List<Path> jars) {
            this.jars = jars;
        }

        boolean present(String binaryName) {
            return read(binaryName) != null;
        }

        /**
         * {@link MemberLookup#UNVERIFIABLE} means the lookup had to leave the
         * fixture jars (typically {@code net.minecraftforge.items.IItemHandler}
         * or another external supertype). Those members are resolved by the
         * real runtime classpath and are therefore out of fixture scope.
         */
        MemberLookup lookup(
                String owner,
                String name,
                String descriptor,
                boolean method
        ) {
            Set<String> visited = new HashSet<>();
            Deque<String> pending = new ArrayDeque<>();
            pending.add(owner);
            boolean external = false;
            while (!pending.isEmpty()) {
                String current = pending.poll();
                if (current == null
                        || !visited.add(current)
                        || current.startsWith("java.")) {
                    continue;
                }
                ApiShapeProbe.Shape shape =
                        ApiShapeProbe.inspect(this, current);
                if (!shape.present() || !shape.readable()) {
                    /*
                     * Forge, Minecraft, Sophisticated Storage and other mods
                     * are not part of the fixture jars.
                     */
                    external = true;
                    continue;
                }
                boolean declared = method
                        ? shape.hasMethod(name, descriptor)
                        : shape.hasField(name, descriptor);
                if (declared) {
                    return MemberLookup.PRESENT;
                }
                if (shape.superName() != null) {
                    pending.add(shape.superName());
                }
                pending.addAll(shape.directInterfaces());
            }
            return external
                    ? MemberLookup.UNVERIFIABLE
                    : MemberLookup.ABSENT;
        }

        @Override
        public byte[] read(String binaryName) {
            String entryName = binaryName.replace('.', '/')
                    + ".class";
            for (Path jar : this.jars) {
                try (JarFile file = new JarFile(jar.toFile())) {
                    JarEntry entry = file.getJarEntry(entryName);
                    if (entry == null) {
                        continue;
                    }
                    try (InputStream input = file.getInputStream(entry)) {
                        return input.readAllBytes();
                    }
                } catch (IOException unreadable) {
                    return null;
                }
            }
            return null;
        }
    }

    private record Reference(
            String owner,
            String member,
            String descriptor,
            boolean method
    ) implements Comparable<Reference> {
        @Override
        public int compareTo(Reference other) {
            int ownerOrder = this.owner.compareTo(other.owner);
            if (ownerOrder != 0) {
                return ownerOrder;
            }
            String left = String.valueOf(this.member)
                    + this.descriptor;
            String right = String.valueOf(other.member)
                    + other.descriptor;
            return left.compareTo(right);
        }
    }

    private enum MemberLookup {
        PRESENT,
        ABSENT,
        UNVERIFIABLE
    }
}

package com.mafuyu404.taczaddon.compat;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Structural (byte level) class shape probe for optional integration ABI
 * generation detection.
 *
 * <p>Reading class files as bytes never links or initializes the optional API
 * classes. That keeps generation detection possible while the optional mod is
 * absent or ABI-incompatible, and it keeps the facade classes free of optional
 * type references.
 *
 * <p>The probe is intentionally small: generation detection only needs class
 * presence plus exact member name/descriptor pairs.
 */
public final class ApiShapeProbe {
    private ApiShapeProbe() {
    }

    /** Reads raw class bytes. Returning {@code null} means "not present". */
    @FunctionalInterface
    public interface ClassBytes {
        byte[] read(String binaryName);
    }

    /**
     * Observed shape of one class. {@code readable == false} means the class
     * resource exists but could not be parsed, which callers must treat as an
     * unknown ABI instead of a satisfied contract.
     */
    public record Shape(
            boolean present,
            boolean readable,
            String superName,
            Set<String> directInterfaces,
            Set<String> methods,
            Set<String> fields
    ) {
        public boolean hasMethod(String name, String descriptor) {
            return this.methods.contains(methodKey(name, descriptor));
        }

        public boolean hasField(String name, String descriptor) {
            return this.fields.contains(fieldKey(name, descriptor));
        }

        public boolean implementsDirectly(String binaryName) {
            return this.directInterfaces.contains(binaryName);
        }
    }

    public static ClassBytes loaderSource(ClassLoader loader) {
        return binaryName -> readResource(loader, binaryName);
    }

    /**
     * Source backed by the class loader that defined {@code anchor}. Callers
     * must pass the optional facade itself so the probe observes the same
     * loader that will later link the optional backend.
     */
    public static ClassBytes sourceFor(Class<?> anchor) {
        return loaderSource(anchor.getClassLoader());
    }

    public static Shape inspect(ClassBytes source, String binaryName) {
        byte[] bytes = source.read(binaryName);
        if (bytes == null) {
            return absent();
        }
        ClassNode node = new ClassNode();
        try {
            new ClassReader(bytes).accept(
                    node,
                    ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
            );
        } catch (RuntimeException unreadable) {
            return new Shape(
                    true,
                    false,
                    null,
                    Set.of(),
                    Set.of(),
                    Set.of()
            );
        }

        Set<String> methods = new LinkedHashSet<>();
        for (MethodNode method : node.methods) {
            methods.add(methodKey(method.name, method.desc));
        }
        Set<String> fields = new LinkedHashSet<>();
        for (FieldNode field : node.fields) {
            fields.add(fieldKey(field.name, field.desc));
        }
        Set<String> interfaces = new LinkedHashSet<>();
        if (node.interfaces != null) {
            for (String declared : node.interfaces) {
                interfaces.add(declared.replace('/', '.'));
            }
        }
        return new Shape(
                true,
                true,
                node.superName == null
                        ? null
                        : node.superName.replace('/', '.'),
                Set.copyOf(interfaces),
                Set.copyOf(methods),
                Set.copyOf(fields)
        );
    }

    public static boolean hasClass(ClassBytes source, String binaryName) {
        return inspect(source, binaryName).present();
    }

    public static boolean hasMethod(
            ClassBytes source,
            String owner,
            String name,
            String descriptor
    ) {
        return inspect(source, owner).hasMethod(name, descriptor);
    }

    public static boolean hasField(
            ClassBytes source,
            String owner,
            String name,
            String descriptor
    ) {
        return inspect(source, owner).hasField(name, descriptor);
    }

    /**
     * True only when every requested member is present on a readable class.
     *
     * <p>Members are resolved through the class hierarchy (superclasses and
     * implemented interfaces) exactly like the JVM resolves them, so an
     * upstream refactor that moves a method to a supertype does not look like
     * an ABI break.
     */
    public static boolean satisfies(
            ClassBytes source,
            String owner,
            List<Member> members
    ) {
        Shape shape = inspect(source, owner);
        if (!shape.present() || !shape.readable()) {
            return false;
        }
        for (Member member : members) {
            if (!hasMemberInHierarchy(source, owner, member)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Hierarchy-aware member lookup.
     *
     * <p>Types outside the probed class path (Forge, Minecraft, other mods)
     * terminate that branch: their members are provided by the runtime and are
     * not part of the probed contract.
     */
    private static boolean hasMemberInHierarchy(
            ClassBytes source,
            String owner,
            Member member
    ) {
        Set<String> visited = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(owner);
        while (!pending.isEmpty()) {
            String current = pending.poll();
            if (current == null || !visited.add(current)) {
                continue;
            }
            Shape shape = inspect(source, current);
            if (!shape.present() || !shape.readable()) {
                continue;
            }
            if (member.presentIn(shape)) {
                return true;
            }
            if (shape.superName() != null) {
                pending.add(shape.superName());
            }
            pending.addAll(shape.directInterfaces());
        }
        return false;
    }

    public static String methodKey(String name, String descriptor) {
        return name + descriptor;
    }

    public static String fieldKey(String name, String descriptor) {
        return name + ':' + descriptor;
    }

    public static Member method(String name, String descriptor) {
        return new Member(true, name, descriptor);
    }

    public static Member field(String name, String descriptor) {
        return new Member(false, name, descriptor);
    }

    /** One exact member name/descriptor pair of an optional API contract. */
    public record Member(boolean method, String name, String descriptor) {
        boolean presentIn(Shape shape) {
            return this.method
                    ? shape.hasMethod(this.name, this.descriptor)
                    : shape.hasField(this.name, this.descriptor);
        }
    }

    private static Shape absent() {
        return new Shape(
                false,
                false,
                null,
                Set.of(),
                Set.of(),
                Set.of()
        );
    }

    private static byte[] readResource(
            ClassLoader loader,
            String binaryName
    ) {
        if (loader == null || binaryName == null) {
            return null;
        }
        String resource = binaryName.replace('.', '/') + ".class";
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) {
                return null;
            }
            return stream.readAllBytes();
        } catch (IOException | RuntimeException unreadable) {
            return null;
        }
    }
}

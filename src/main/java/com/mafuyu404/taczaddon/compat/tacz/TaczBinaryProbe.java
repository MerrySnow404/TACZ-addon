package com.mafuyu404.taczaddon.compat.tacz;

import com.mafuyu404.taczaddon.compat.tacz.contract.ClassContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.FeatureContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.FieldAccessContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.FieldContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.InvokeContract;
import com.mafuyu404.taczaddon.compat.tacz.contract.MethodContract;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class TaczBinaryProbe {
    private TaczBinaryProbe() {
    }

    public static ProbeResult inspect(FeatureContract contract) {
        return inspect(contract, TaczBinaryProbe::readClassBytes);
    }

    static ProbeResult inspect(
            FeatureContract contract,
            ClassByteSource classSource
    ) {
        List<String> failures = new ArrayList<>();
        for (ClassContract classContract : contract.classes()) {
            inspectClass(classContract, failures, classSource);
        }
        return new ProbeResult(
                failures.isEmpty(),
                failures,
                failures.isEmpty()
                        ? "contract satisfied"
                        : String.join("; ", failures)
        );
    }

    private static void inspectClass(
            ClassContract classContract,
            List<String> failures,
            ClassByteSource classSource
    ) {
        byte[] bytes = classSource.read(classContract.className());
        if (bytes == null) {
            failures.add(
                    "missing class " + classContract.className()
            );
            return;
        }

        ClassNode node = new ClassNode();
        try {
            new ClassReader(bytes).accept(
                    node,
                    ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES
            );
        } catch (RuntimeException exception) {
            failures.add(
                    "unreadable class "
                            + classContract.className()
                            + ": "
                            + exception.getMessage()
            );
            return;
        }

        for (MethodContract method : classContract.methods()) {
            MethodNode methodNode = findMethod(
                    node,
                    method
            );
            if (methodNode == null) {
                failures.add(
                        "missing method "
                                + classContract.className()
                                + "."
                                + method.name()
                                + method.descriptor()
                );
                continue;
            }

            if (!matchesMethodName(method, methodNode.name)) {
                failures.add(
                        "method name alias mismatch "
                                + classContract.className()
                                + "."
                                + methodNode.name
                                + method.descriptor()
                );
                continue;
            }

            for (InvokeContract invoke : method.invocations()) {
                int count = countInvocation(methodNode, invoke);
                if (count < invoke.minimum()
                        || count > invoke.maximum()) {
                    failures.add(
                            "bad invocation count "
                                    + count
                                    + " for "
                                    + invoke.owner()
                                    + "."
                                    + invoke.name()
                                    + invoke.descriptor()
                                    + " in "
                                    + classContract.className()
                                    + "."
                                    + method.name()
                                    + method.descriptor()
                    );
                }
            }

            for (FieldAccessContract access : method.fieldAccesses()) {
                int count = countFieldAccess(methodNode, access);
                if (count < access.minimum()
                        || count > access.maximum()) {
                    failures.add(
                            "bad field access count "
                                    + count
                                    + " for "
                                    + access.owner()
                                    + "."
                                    + access.name()
                                    + ":"
                                    + access.descriptor()
                                    + " in "
                                    + classContract.className()
                                    + "."
                                    + method.name()
                                    + method.descriptor()
                    );
                }
            }
        }

        for (FieldContract field : classContract.fields()) {
            boolean found = false;
            for (FieldNode fieldNode : node.fields) {
                if (field.name().equals(fieldNode.name)
                        && field.descriptor().equals(fieldNode.desc)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                failures.add(
                        "missing field "
                                + classContract.className()
                                + "."
                                + field.name()
                                + ":"
                                + field.descriptor()
                );
            }
        }
    }

    private static MethodNode findMethod(
            ClassNode node,
            MethodContract contract
    ) {
        for (MethodNode method : node.methods) {
            if (contract.descriptor().equals(method.desc)
                    && matchesMethodName(contract, method.name)) {
                return method;
            }
        }
        return null;
    }

    private static boolean matchesMethodName(
            MethodContract contract,
            String actualName
    ) {
        return contract.name().equals(actualName)
                || contract.nameAliases().contains(actualName);
    }

    private static int countInvocation(
            MethodNode method,
            InvokeContract contract
    ) {
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof MethodInsnNode methodInsn)) {
                continue;
            }
            if (!contract.owner().equals(methodInsn.owner)
                    || !contract.descriptor().equals(methodInsn.desc)
                    || !contract.matchesName(methodInsn.name)) {
                continue;
            }
            count++;
        }
        return count;
    }

    private static int countFieldAccess(
            MethodNode method,
            FieldAccessContract contract
    ) {
        int count = 0;
        for (AbstractInsnNode instruction = method.instructions.getFirst();
             instruction != null;
             instruction = instruction.getNext()) {
            if (!(instruction instanceof FieldInsnNode fieldInsn)) {
                continue;
            }
            if (contract.opcode() != fieldInsn.getOpcode()
                    || !contract.owner().equals(fieldInsn.owner)
                    || !contract.name().equals(fieldInsn.name)
                    || !contract.descriptor().equals(fieldInsn.desc)) {
                continue;
            }
            count++;
        }
        return count;
    }

    private static byte[] readClassBytes(String className) {
        String resource = className.replace('.', '/') + ".class";
        ClassLoader loader = TaczBinaryProbe.class.getClassLoader();
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) {
                return null;
            }
            return stream.readAllBytes();
        } catch (IOException exception) {
            return null;
        }
    }

    @FunctionalInterface
    interface ClassByteSource {
        byte[] read(String className);
    }

    public record ProbeResult(
            boolean passed,
            List<String> failures,
            String detail
    ) {
    }
}

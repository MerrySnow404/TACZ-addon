package com.mafuyu404.taczaddon.compat.contract;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/** Inspects dependency bytes without loading optional target classes. */
public final class BinaryContracts {
    private static final Logger LOGGER = LoggerFactory.getLogger(BinaryContracts.class);

    private BinaryContracts() {}

    public static boolean hasMethod(String owner, String name, String descriptor) {
        byte[] bytes = readClassBytes(owner);
        if (bytes == null) return false;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_CODE);
        return node.methods.stream()
                .anyMatch(method -> method.name.equals(name) && method.desc.equals(descriptor));
    }

    public static boolean hasAppliedBackpackPayloadHook(ClassNode targetClass) {
        if (targetClass == null || targetClass.methods == null) {
            return false;
        }

        for (MethodNode method : targetClass.methods) {
            if (!"handlePayload".equals(method.name)
                    || !ContractVisitor.HANDLE_PAYLOAD_DESCRIPTOR.equals(method.desc)) {
                continue;
            }

            if (method.instructions == null) {
                return false;
            }

            for (var instruction = method.instructions.getFirst();
                    instruction != null;
                    instruction = instruction.getNext()) {
                if (!(instruction instanceof MethodInsnNode invocation)) {
                    continue;
                }

                if (invocation.getOpcode() != Opcodes.INVOKESTATIC) {
                    continue;
                }

                /*
                 * Injection handlers are merged into the target itself.
                 */
                if (!targetClass.name.equals(invocation.owner)) {
                    continue;
                }

                /*
                 * Mixin may rename the handler to something like
                 * handler$...$taczaddon$afterBackpackContentsReceived.
                 */
                if (invocation.name.contains("taczaddon$afterBackpackContentsReceived")) {
                    return true;
                }
            }

            /*
             * The expected handlePayload overload exists but contains no call to
             * our injected response handler.
             */
            return false;
        }

        return false;
    }

    public static boolean verifyContract(
            String classInternalName, String className, ContractKind kind, String disabledFeature) {
        byte[] classBytes = readClassBytes(classInternalName);

        if (classBytes == null) {
            LOGGER.error(
                    "[taczaddon] Compatibility gate: could not read {} "
                            + "from the dependency classpath. Expected binary "
                            + "contract: {}. Disabled: {}.",
                    className,
                    kind.expectedDescription,
                    disabledFeature);
            return false;
        }

        boolean valid;

        try {
            ContractVisitor visitor = new ContractVisitor(kind);
            new ClassReader(classBytes)
                    .accept(
                            visitor,
                            ClassReader.SKIP_CODE
                                    | ClassReader.SKIP_DEBUG
                                    | ClassReader.SKIP_FRAMES);
            valid = visitor.matches();
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "[taczaddon] Compatibility gate: failed to inspect {}. "
                            + "Expected binary contract: {}. Disabled: {}.",
                    className,
                    kind.expectedDescription,
                    disabledFeature,
                    exception);
            return false;
        }

        if (!valid) {
            LOGGER.error(
                    "[taczaddon] Compatibility gate: {} does not match the "
                            + "verified contract (expected {}). Disabled: "
                            + "{}; startup continues.",
                    className,
                    kind.expectedDescription,
                    disabledFeature);
        }

        return valid;
    }

    public static byte[] readClassBytes(String classInternalName) {
        String resourcePath = classInternalName + ".class";
        ClassLoader loader = BinaryContracts.class.getClassLoader();

        try (InputStream stream =
                loader == null
                        ? ClassLoader.getSystemResourceAsStream(resourcePath)
                        : loader.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                return null;
            }
            return stream.readAllBytes();
        } catch (IOException exception) {
            return null;
        }
    }

    public enum ContractKind {
        UNLOAD_PACKET(
                "static handle(ClientMessageUnloadAttachment, "
                        + "IPayloadContext)V, non-static gunSlotIndex:I, "
                        + "non-static attachmentType:AttachmentType"),
        REFIT_SCREEN(
                "non-static addAttachmentTypeButtons()V, "
                        + "non-static addInventoryAttachmentButtons()V, "
                        + "non-static non-final currentPage:I"),
        BACKPACK_CONTENTS_PAYLOAD(
                "static handlePayload(BackpackContentsPayload, "
                        + "IPayloadContext)V, accessor "
                        + "backpackUuid()Ljava/util/UUID;, accessor "
                        + "backpackContents()Lnet/minecraft/nbt/CompoundTag;");

        private final String expectedDescription;

        ContractKind(String expectedDescription) {
            this.expectedDescription = expectedDescription;
        }
    }

    private static final class ContractVisitor extends ClassVisitor {

        private static final String HANDLE_DESCRIPTOR =
                "(Lcom/tacz/guns/network/message/"
                        + "ClientMessageUnloadAttachment;"
                        + "Lnet/neoforged/neoforge/network/handling/"
                        + "IPayloadContext;)V";

        private static final String ATTACHMENT_TYPE_DESCRIPTOR =
                "Lcom/tacz/guns/api/item/attachment/" + "AttachmentType;";

        private static final String HANDLE_PAYLOAD_DESCRIPTOR =
                "(Lnet/p3pp3rf1y/sophisticatedbackpacks/network/"
                        + "BackpackContentsPayload;"
                        + "Lnet/neoforged/neoforge/network/handling/"
                        + "IPayloadContext;)V";

        private static final String UUID_DESCRIPTOR = "Ljava/util/UUID;";

        private static final String COMPOUND_TAG_DESCRIPTOR = "Lnet/minecraft/nbt/CompoundTag;";

        private final ContractKind kind;

        private boolean staticHandle;
        private boolean gunSlotIndexField;
        private boolean attachmentTypeField;
        private boolean addAttachmentTypeButtons;
        private boolean addInventoryAttachmentButtons;
        private boolean currentPageField;
        private boolean staticHandlePayload;
        private boolean backpackUuidAccessor;
        private boolean backpackContentsAccessor;

        ContractVisitor(ContractKind kind) {
            super(Opcodes.ASM9);
            this.kind = kind;
        }

        @Override
        public FieldVisitor visitField(
                int access, String name, String descriptor, String signature, Object value) {
            boolean nonStatic = (access & Opcodes.ACC_STATIC) == 0;
            boolean nonFinal = (access & Opcodes.ACC_FINAL) == 0;

            if ("gunSlotIndex".equals(name) && "I".equals(descriptor) && nonStatic) {
                gunSlotIndexField = true;
            }

            if ("attachmentType".equals(name)
                    && ATTACHMENT_TYPE_DESCRIPTOR.equals(descriptor)
                    && nonStatic) {
                attachmentTypeField = true;
            }

            if ("currentPage".equals(name) && "I".equals(descriptor) && nonStatic && nonFinal) {
                currentPageField = true;
            }

            return null;
        }

        @Override
        public MethodVisitor visitMethod(
                int access, String name, String descriptor, String signature, String[] exceptions) {
            boolean staticMethod = (access & Opcodes.ACC_STATIC) != 0;

            if ("handle".equals(name) && HANDLE_DESCRIPTOR.equals(descriptor) && staticMethod) {
                staticHandle = true;
            }

            if ("addAttachmentTypeButtons".equals(name)
                    && "()V".equals(descriptor)
                    && !staticMethod) {
                addAttachmentTypeButtons = true;
            }

            if ("addInventoryAttachmentButtons".equals(name)
                    && "()V".equals(descriptor)
                    && !staticMethod) {
                addInventoryAttachmentButtons = true;
            }

            if ("handlePayload".equals(name)
                    && HANDLE_PAYLOAD_DESCRIPTOR.equals(descriptor)
                    && staticMethod) {
                staticHandlePayload = true;
            }

            if ("backpackUuid".equals(name)
                    && UUID_DESCRIPTOR.equals(descriptor)
                    && !staticMethod) {
                backpackUuidAccessor = true;
            }

            if ("backpackContents".equals(name)
                    && COMPOUND_TAG_DESCRIPTOR.equals(descriptor)
                    && !staticMethod) {
                backpackContentsAccessor = true;
            }

            return null;
        }

        boolean matches() {
            return switch (kind) {
                case UNLOAD_PACKET -> staticHandle && gunSlotIndexField && attachmentTypeField;
                case REFIT_SCREEN ->
                        addAttachmentTypeButtons
                                && addInventoryAttachmentButtons
                                && currentPageField;
                case BACKPACK_CONTENTS_PAYLOAD ->
                        staticHandlePayload && backpackUuidAccessor && backpackContentsAccessor;
            };
        }
    }
}

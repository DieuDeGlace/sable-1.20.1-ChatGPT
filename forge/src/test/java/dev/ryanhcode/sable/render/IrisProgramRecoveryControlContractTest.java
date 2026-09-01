package dev.ryanhcode.sable.render;

import dev.ryanhcode.sable.compatibility.SableIrisProgramRecovery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisProgramRecoveryControlContractTest {
    private static final String RECOVERY_CLASS =
            "dev/ryanhcode/sable/mixin/compatibility/iris/ExtendedShaderMixin.class";
    private static final String RECOVERY_CONTROL =
            "dev/ryanhcode/sable/compatibility/SableIrisProgramRecovery";
    private static final String GL20C = "org/lwjgl/opengl/GL20C";
    private static final String PROGRAM_MANAGER = "com/mojang/blaze3d/shaders/ProgramManager";

    @AfterEach
    void restoreDefaults() {
        SableIrisProgramRecovery.setConfiguredEnabled(true);
        SableIrisProgramRecovery.setMode(SableIrisProgramRecovery.Mode.CONFIGURED);
    }

    @Test
    void sessionOverrideDoesNotMutateTheConfiguredValue() {
        SableIrisProgramRecovery.setConfiguredEnabled(false);
        SableIrisProgramRecovery.setMode(SableIrisProgramRecovery.Mode.CONFIGURED);
        assertFalse(SableIrisProgramRecovery.isEnabled());

        SableIrisProgramRecovery.setMode(SableIrisProgramRecovery.Mode.ENABLED);
        assertTrue(SableIrisProgramRecovery.isEnabled());
        assertFalse(SableIrisProgramRecovery.configuredEnabled());

        SableIrisProgramRecovery.setMode(SableIrisProgramRecovery.Mode.DISABLED);
        assertFalse(SableIrisProgramRecovery.isEnabled());
    }

    @Test
    void compiledRecoveryKeepsHeadGateAndConditionalCanonicalNativeOrder() throws Exception {
        final ClassNode owner = new ClassNode();
        try (InputStream bytecode = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(RECOVERY_CLASS)) {
            assertNotNull(bytecode, "Compiled ExtendedShaderMixin was not found");
            new ClassReader(bytecode).accept(owner, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        assertClassMixinContract(owner);

        final MethodNode recovery = owner.methods.stream()
                .filter(method -> method.name.equals("sable$restoreProgramCacheCoherence"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Compiled recovery handler was not found"));
        assertHeadInjectionContract(recovery);

        final List<AbstractInsnNode> code = List.of(recovery.instructions.toArray()).stream()
                .filter(instruction -> instruction.getOpcode() >= 0)
                .toList();
        assertEquals(25, code.size(), "Recovery handler gained unreviewed executable instructions");
        assertCall(code.get(0), Opcodes.INVOKESTATIC, RECOVERY_CONTROL, "isEnabled", "()Z");
        assertJump(code.get(1), Opcodes.IFNE, code.get(3));
        assertOpcode(code.get(2), Opcodes.RETURN);
        assertVar(code.get(3), Opcodes.ALOAD, 0);
        assertType(code.get(4), Opcodes.CHECKCAST, "net/minecraft/client/renderer/ShaderInstance");
        assertVar(code.get(5), Opcodes.ASTORE, 2);
        assertVar(code.get(6), Opcodes.ALOAD, 2);
        assertCall(code.get(7), Opcodes.INVOKEVIRTUAL,
                "net/minecraft/client/renderer/ShaderInstance", "getId", "()I");
        assertVar(code.get(8), Opcodes.ISTORE, 3);
        assertIntConstant(code.get(9), 35725);
        assertCall(code.get(10), Opcodes.INVOKESTATIC, GL20C, "glGetInteger", "(I)I");
        assertVar(code.get(11), Opcodes.ISTORE, 4);
        assertVar(code.get(12), Opcodes.ILOAD, 4);
        assertVar(code.get(13), Opcodes.ILOAD, 3);
        assertJump(code.get(14), Opcodes.IF_ICMPNE, code.get(16));
        assertOpcode(code.get(15), Opcodes.RETURN);
        assertVar(code.get(16), Opcodes.ILOAD, 3);
        assertCall(code.get(17), Opcodes.INVOKESTATIC, PROGRAM_MANAGER, "glUseProgram", "(I)V");
        assertIntConstant(code.get(18), 35725);
        assertCall(code.get(19), Opcodes.INVOKESTATIC, GL20C, "glGetInteger", "(I)I");
        assertVar(code.get(20), Opcodes.ILOAD, 3);
        assertJump(code.get(21), Opcodes.IF_ICMPEQ, code.get(24));
        assertVar(code.get(22), Opcodes.ILOAD, 3);
        assertCall(code.get(23), Opcodes.INVOKESTATIC, GL20C, "glUseProgram", "(I)V");
        assertOpcode(code.get(24), Opcodes.RETURN);

        final Path forgeRoot = Path.of(System.getProperty("user.dir")).normalize();
        final String source = Files.readString(forgeRoot.resolve(
                "src/port/java/dev/ryanhcode/sable/admitted/ExtendedShaderMixin.java"));
        assertFalse(source.contains("GlProgramStateTrace"));
        assertFalse(source.contains("glIsProgram"));
        assertFalse(source.contains("PROGRAM_RECOVERY_TRACE"));
    }

    @Test
    void clientCommandIsSessionOnlyAndConfigDefaultsOn() throws Exception {
        final Path forgeRoot = Path.of(System.getProperty("user.dir")).normalize();
        final String command = Files.readString(forgeRoot.resolve(
                "src/port/java/dev/ryanhcode/sable/forge/client/SableIrisProgramRecoveryCommands.java"));
        final String config = Files.readString(forgeRoot.resolve(
                "../common/src/main/java/dev/ryanhcode/sable/SableClientConfig.java"));

        assertTrue(command.contains("literal(\"on\")"));
        assertTrue(command.contains("literal(\"off\")"));
        assertTrue(command.contains("literal(\"configured\")"));
        assertTrue(command.contains("literal(\"status\")"));
        assertFalse(command.contains("allChanged"));
        assertFalse(command.contains("rebuild"));
        assertFalse(command.contains("reload"));
        assertTrue(config.contains("define(\"iris_program_cache_recovery\", true)"));
    }

    private static void assertHeadInjectionContract(final MethodNode method) {
        final AnnotationNode inject = findAnnotation(method.visibleAnnotations, method.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/injection/Inject;",
                "Recovery handler has no @Inject annotation");
        final Object targets = annotationValue(inject, "method");
        assertTrue(targets instanceof List<?> && ((List<?>) targets).contains("m_173363_"));
        assertEquals(Boolean.FALSE, annotationValue(inject, "remap"));

        final Object atValues = annotationValue(inject, "at");
        assertTrue(atValues instanceof List<?> && ((List<?>) atValues).size() == 1);
        final Object at = ((List<?>) atValues).get(0);
        assertTrue(at instanceof AnnotationNode);
        assertEquals("HEAD", annotationValue((AnnotationNode) at, "value"));
    }

    private static void assertClassMixinContract(final ClassNode owner) {
        findAnnotation(owner.visibleAnnotations, owner.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Pseudo;",
                "Oculus adapter must remain optional through @Pseudo");
        final AnnotationNode mixin = findAnnotation(owner.visibleAnnotations, owner.invisibleAnnotations,
                "Lorg/spongepowered/asm/mixin/Mixin;", "Oculus adapter has no @Mixin annotation");
        final Object targets = annotationValue(mixin, "targets");
        assertEquals(List.of("net.irisshaders.iris.pipeline.programs.ExtendedShader"), targets);
        assertEquals(Boolean.FALSE, annotationValue(mixin, "remap"));
    }

    private static AnnotationNode findAnnotation(final List<AnnotationNode> visible,
                                                 final List<AnnotationNode> invisible,
                                                 final String descriptor, final String message) {
        for (List<AnnotationNode> annotations : java.util.Arrays.asList(visible, invisible)) {
            if (annotations == null) {
                continue;
            }
            for (AnnotationNode annotation : annotations) {
                if (annotation.desc.equals(descriptor)) {
                    return annotation;
                }
            }
        }
        throw new AssertionError(message);
    }

    private static Object annotationValue(final AnnotationNode annotation, final String key) {
        assertNotNull(annotation.values, () -> annotation.desc + " has no values");
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (key.equals(annotation.values.get(index))) {
                return annotation.values.get(index + 1);
            }
        }
        return null;
    }

    private static void assertOpcode(final AbstractInsnNode instruction, final int opcode) {
        assertEquals(opcode, instruction.getOpcode());
    }

    private static void assertVar(final AbstractInsnNode instruction, final int opcode, final int slot) {
        assertTrue(instruction instanceof VarInsnNode);
        assertEquals(opcode, instruction.getOpcode());
        assertEquals(slot, ((VarInsnNode) instruction).var);
    }

    private static void assertType(final AbstractInsnNode instruction, final int opcode, final String type) {
        assertTrue(instruction instanceof TypeInsnNode);
        assertEquals(opcode, instruction.getOpcode());
        assertEquals(type, ((TypeInsnNode) instruction).desc);
    }

    private static void assertIntConstant(final AbstractInsnNode instruction, final int value) {
        assertTrue(instruction instanceof LdcInsnNode);
        assertEquals(value, ((LdcInsnNode) instruction).cst);
    }

    private static void assertCall(final AbstractInsnNode instruction, final int opcode,
                                   final String owner, final String name, final String descriptor) {
        assertTrue(instruction instanceof MethodInsnNode);
        final MethodInsnNode call = (MethodInsnNode) instruction;
        assertEquals(opcode, call.getOpcode());
        assertEquals(owner, call.owner);
        assertEquals(name, call.name);
        assertEquals(descriptor, call.desc);
    }

    private static void assertJump(final AbstractInsnNode instruction, final int opcode,
                                   final AbstractInsnNode expectedTarget) {
        assertTrue(instruction instanceof JumpInsnNode);
        final JumpInsnNode jump = (JumpInsnNode) instruction;
        assertEquals(opcode, jump.getOpcode());
        AbstractInsnNode target = jump.label;
        while (target != null && target.getOpcode() < 0) {
            target = target.getNext();
        }
        assertSame(expectedTarget, target);
    }
}

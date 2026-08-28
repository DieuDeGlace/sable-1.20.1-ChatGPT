package dev.ryanhcode.sable.neoforge.compatibility.create;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedstoneContactTickerBytecodeTest {
    private static final String MIXIN_CLASS =
            "dev/ryanhcode/sable/neoforge/mixin/compatibility/create/redstone_contacts/RedstoneContactBlockMixin.class";
    private static final String IBE = "com/simibubi/create/foundation/block/IBE";
    private static final String SMART_TICKER =
            "com/simibubi/create/foundation/blockEntity/SmartBlockEntityTicker";

    @Test
    void tickerDoesNotUseInterfaceDefaultSuperAfterMixinMerge() throws IOException {
        final AtomicBoolean foundTickerMethod = new AtomicBoolean();
        final AtomicBoolean foundInterfaceDefaultSuperCall = new AtomicBoolean();
        final AtomicBoolean constructsSmartTicker = new AtomicBoolean();

        try (InputStream bytecode = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(MIXIN_CLASS)) {
            assertNotNull(bytecode, "Compiled RedstoneContactBlockMixin was not found");
            new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(final int access, final String name, final String descriptor,
                                                 final String signature, final String[] exceptions) {
                    if (!name.equals("getTicker")) {
                        return null;
                    }
                    foundTickerMethod.set(true);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(final int opcode, final String owner, final String methodName,
                                                    final String methodDescriptor, final boolean isInterface) {
                            if (opcode == Opcodes.INVOKESPECIAL && owner.equals(IBE)
                                    && (methodName.equals("getTicker") || methodName.equals("m_142354_"))) {
                                foundInterfaceDefaultSuperCall.set(true);
                            }
                        }

                        @Override
                        public void visitTypeInsn(final int opcode, final String type) {
                            if (opcode == Opcodes.NEW && type.equals(SMART_TICKER)) {
                                constructsSmartTicker.set(true);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }

        assertTrue(foundTickerMethod.get(), "getTicker method was not present in the compiled mixin");
        assertFalse(foundInterfaceDefaultSuperCall.get(),
                "IBE.super.getTicker compiles to an InterfaceMethodref that is invalid after mixin merge");
        assertTrue(constructsSmartTicker.get(),
                "Target Create 6.0.8 semantics require a SmartBlockEntityTicker on the server");
    }
}

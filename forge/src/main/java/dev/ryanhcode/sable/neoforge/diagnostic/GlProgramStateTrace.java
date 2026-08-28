package dev.ryanhcode.sable.neoforge.diagnostic;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.ShaderInstance;
import org.lwjgl.opengl.GL20C;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded, behavior-neutral trace of the OpenGL program state.
 *
 * <p>The trace remains in memory during normal rendering and is emitted only
 * when an upload is observed with no active program, or when the OpenGL debug
 * callback reports that exact fault. It deliberately never binds a program or
 * changes any other rendering state.</p>
 */
public final class GlProgramStateTrace {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int RING_CAPACITY = 512;
    private static final int FAULT_DUMP_LIMIT = 4;
    private static final String[] EVENTS = new String[RING_CAPACITY];
    private static final AtomicInteger FAULT_DUMPS = new AtomicInteger();
    private static final AtomicInteger PROGRAM_QUERY_FAILURES = new AtomicInteger();
    private static final ThreadLocal<ArrayDeque<String>> CONTEXT =
            ThreadLocal.withInitial(ArrayDeque::new);
    private static volatile boolean oculusProgramFieldResolved;
    private static volatile Field oculusProgramField;
    private static volatile String lastZeroProgramRequest = "<none>";
    private static volatile String lastDirectZeroBoundary = "<none>";
    private static long nextSequence;

    private GlProgramStateTrace() {
    }

    public static void enterShaderClear(final String kind, final ShaderInstance shader) {
        final String label = kind + " shader=" + shaderDescription(shader);
        CONTEXT.get().push(label);
        checkpoint("clear-head " + label, shader, false);
    }

    public static void exitShaderClear(final String kind, final ShaderInstance shader) {
        checkpoint("clear-tail " + kind + " shader=" + shaderDescription(shader), shader, false);
        final ArrayDeque<String> contexts = CONTEXT.get();
        if (!contexts.isEmpty()) {
            contexts.pop();
        }
    }

    public static void beforeProgramManagerCall(final int requestedProgram) {
        final int activeBefore = currentProgram();
        final String context = requestedProgram == 0 ? zeroProgramCaller() : currentContext();
        append("program-manager-head requested=" + requestedProgram + " active=" + activeBefore
                + " oculusCache=" + oculusCachedProgram() + " source=" + context);
    }

    public static void afterProgramManagerCall(final int requestedProgram) {
        final int activeAfter = currentProgram();
        append("program-manager-tail requested=" + requestedProgram + " active=" + activeAfter
                + " oculusCache=" + oculusCachedProgram() + " source=" + currentContext());
        if (activeAfter != requestedProgram) {
            dumpFault("ProgramManager request did not reach OpenGL: requested=" + requestedProgram,
                    null, null);
        }
    }

    public static void beforeGlStateManagerCall(final int requestedProgram) {
        final String source = requestedProgram == 0 ? zeroProgramCaller() : currentContext();
        if (requestedProgram == 0) {
            lastZeroProgramRequest = "activeBefore=" + currentProgram() + " oculusCache="
                    + oculusCachedProgram() + " source=" + source;
        }
        append("gl-state-manager-head requested=" + requestedProgram + " active=" + currentProgram()
                + " oculusCache=" + oculusCachedProgram()
                + " source=" + source);
    }

    public static void afterGlStateManagerCall(final int requestedProgram) {
        append("gl-state-manager-tail requested=" + requestedProgram + " active=" + currentProgram()
                + " oculusCache=" + oculusCachedProgram());
    }

    public static void directProgramBoundary(final String stage) {
        final int activeProgram = currentProgram();
        final String caller = activeProgram == 0 ? directBoundaryCaller() : "";
        if (activeProgram == 0 && stage.endsWith("-tail")) {
            lastDirectZeroBoundary = "stage=" + stage + " oculusCache=" + oculusCachedProgram()
                    + " caller=" + caller;
        }
        append("direct-program-boundary=" + stage + " active=" + activeProgram
                + " oculusCache=" + oculusCachedProgram()
                + " renderSystemShader=" + renderSystemShaderDescription()
                + (activeProgram == 0 ? " caller=" + caller : ""));
    }

    public static void checkpoint(final String stage, final ShaderInstance expectedShader,
                                  final boolean dumpIfMismatched) {
        final int activeProgram = currentProgram();
        observedCheckpoint(stage, expectedShader, activeProgram, dumpIfMismatched);
    }

    public static void observedCheckpoint(final String stage, final ShaderInstance expectedShader,
                                          final int activeProgram, final boolean dumpIfMismatched) {
        append("checkpoint=" + stage + " expected=" + shaderDescription(expectedShader)
                + " active=" + activeProgram + " oculusCache=" + oculusCachedProgram()
                + " renderSystemShader=" + renderSystemShaderDescription());
        if (dumpIfMismatched && expectedShader != null && activeProgram != shaderProgramId(expectedShader)) {
            dumpFault("checkpoint " + stage, expectedShader, null);
        }
    }

    public static void nativeNoActiveProgram(final String message) {
        dumpFault("OpenGL debug callback", null, message);
    }

    private static void dumpFault(final String source, final ShaderInstance expectedShader,
                                  final String nativeMessage) {
        final int dumpNumber = FAULT_DUMPS.incrementAndGet();
        if (dumpNumber > FAULT_DUMP_LIMIT) {
            return;
        }

        final StringBuilder trace = new StringBuilder(16_384);
        trace.append("SABLE-GL-PROGRAM-TRACE fault=").append(dumpNumber)
                .append(" source=").append(source)
                .append(" thread=").append(Thread.currentThread().getName())
                .append(" active=").append(currentProgram())
                .append(" oculusCache=").append(oculusCachedProgram())
                .append(" expected=").append(shaderDescription(expectedShader))
                .append(" renderSystemShader=").append(renderSystemShaderDescription());
        if (nativeMessage != null) {
            trace.append(" nativeMessage=").append(nativeMessage);
        }
        trace.append("\nSticky last zero request: ").append(lastZeroProgramRequest)
                .append("\nSticky last direct zero boundary: ").append(lastDirectZeroBoundary);
        trace.append("\nRecent program-state events (oldest to newest):");

        synchronized (EVENTS) {
            final long firstSequence = Math.max(0L, nextSequence - RING_CAPACITY);
            for (long sequence = firstSequence; sequence < nextSequence; sequence++) {
                final String event = EVENTS[(int) (sequence % RING_CAPACITY)];
                if (event != null) {
                    trace.append("\n  ").append(event);
                }
            }
        }
        LOGGER.error(trace.toString());
    }

    private static void append(final String event) {
        synchronized (EVENTS) {
            final long sequence = nextSequence++;
            EVENTS[(int) (sequence % RING_CAPACITY)] = "#" + sequence + " " + event;
        }
    }

    private static int currentProgram() {
        if (!RenderSystem.isOnRenderThread()) {
            return Integer.MIN_VALUE;
        }
        try {
            return GL20C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        } catch (final Throwable throwable) {
            if (PROGRAM_QUERY_FAILURES.incrementAndGet() <= 4) {
                append("gl-current-program-query-failed type=" + throwable.getClass().getName()
                        + " message=" + throwable.getMessage());
            }
            return Integer.MIN_VALUE;
        }
    }

    private static String renderSystemShaderDescription() {
        if (!RenderSystem.isOnRenderThread()) {
            return "<off-render-thread>";
        }
        try {
            return shaderDescription(RenderSystem.getShader());
        } catch (final Throwable throwable) {
            return "<unavailable:" + throwable.getClass().getSimpleName() + ">";
        }
    }

    private static String shaderDescription(final ShaderInstance shader) {
        if (shader == null) {
            return "<null>";
        }
        try {
            return shader.getName() + "#" + System.identityHashCode(shader) + "[program=" + shader.getId() + "]";
        } catch (final Throwable throwable) {
            return shader.getClass().getName() + "#" + System.identityHashCode(shader)
                    + "[unavailable:" + throwable.getClass().getSimpleName() + "]";
        }
    }

    private static int shaderProgramId(final ShaderInstance shader) {
        try {
            return shader.getId();
        } catch (final Throwable ignored) {
            return Integer.MIN_VALUE;
        }
    }

    private static String oculusCachedProgram() {
        try {
            Field field = oculusProgramField;
            if (!oculusProgramFieldResolved) {
                synchronized (GlProgramStateTrace.class) {
                    if (!oculusProgramFieldResolved) {
                        try {
                            field = com.mojang.blaze3d.platform.GlStateManager.class
                                    .getDeclaredField("iris$program");
                            field.setAccessible(true);
                            oculusProgramField = field;
                        } catch (final ReflectiveOperationException ignored) {
                            oculusProgramField = null;
                        }
                        oculusProgramFieldResolved = true;
                    }
                }
            }
            field = oculusProgramField;
            return field == null ? "<absent>" : Integer.toString(field.getInt(null));
        } catch (final Throwable throwable) {
            return "<unavailable:" + throwable.getClass().getSimpleName() + ">";
        }
    }

    private static String currentContext() {
        final ArrayDeque<String> contexts = CONTEXT.get();
        return contexts.isEmpty() ? "<none>" : contexts.peek();
    }

    private static String zeroProgramCaller() {
        final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        final StringBuilder callers = new StringBuilder();
        int retained = 0;
        for (final StackTraceElement frame : stack) {
            final String className = frame.getClassName();
            if (className.equals(Thread.class.getName())
                    || className.equals(GlProgramStateTrace.class.getName())
                    || className.contains("GlStateManagerProgramStateDiagnosticMixin")
                    || className.contains("ProgramManagerProgramStateDiagnosticMixin")
                    || className.equals("com.mojang.blaze3d.shaders.ProgramManager")
                    || className.equals("com.mojang.blaze3d.platform.GlStateManager")) {
                continue;
            }
            if (retained++ > 0) {
                callers.append(" <- ");
            }
            callers.append(frame);
            if (retained == 8) {
                break;
            }
        }
        if (callers.length() == 0) {
            callers.append("<unknown>");
        }
        return callers + " context=" + currentContext();
    }

    private static String directBoundaryCaller() {
        final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        final StringBuilder callers = new StringBuilder();
        int retained = 0;
        for (final StackTraceElement frame : stack) {
            final String className = frame.getClassName();
            if (className.equals(Thread.class.getName())
                    || className.equals(GlProgramStateTrace.class.getName())
                    || className.contains("ProgramStateDiagnosticMixin")) {
                continue;
            }
            if (retained++ > 0) {
                callers.append(" <- ");
            }
            callers.append(frame);
            if (retained == 8) {
                break;
            }
        }
        return callers.length() == 0 ? "<unknown>" : callers.toString();
    }
}

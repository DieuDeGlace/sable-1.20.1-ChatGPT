package dev.ryanhcode.sable.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards the standalone five-argument path-type query from evaluator lifecycle state. */
class WalkNodeEvaluatorMobContractTest {

    @Test
    void explicitMobQueryDoesNotDependOnPreparedEvaluatorState() throws IOException {
        final Path source = Path.of(System.getProperty("user.dir"))
                .resolve("../common/src/main/java/dev/ryanhcode/sable/mixin/entity/entity_pathfinding/WalkNodeEvaluatorMixin.java")
                .normalize();
        final String method = methodBody(Files.readString(source),
                "private BlockPos sable$redirectPathTypeMobPosition(final Mob mob)");

        assertTrue(method.contains("Sable.HELPER.getTrackingSubLevel(mob)"),
                "the standalone query must use its explicit Mob argument");
        assertTrue(method.contains("return mob.blockPosition()"));
        assertTrue(method.contains("transformPositionInverse(mob.position())"));
        assertFalse(method.contains("this.mob"),
                "the evaluator field is null before prepare and after done");
        assertFalse(method.contains("sable$getTrackingSubLevel()"),
                "the zero-argument helper reads the lifecycle-dependent evaluator field");
    }

    private static String methodBody(final String source, final String signature) {
        final int signatureStart = source.indexOf(signature);
        assertTrue(signatureStart >= 0, "path-type redirect is missing");
        final int bodyStart = source.indexOf('{', signatureStart);
        assertTrue(bodyStart >= 0, "path-type redirect body is missing");

        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            final char character = source.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(bodyStart + 1, index);
            }
        }
        throw new AssertionError("path-type redirect body is not balanced");
    }
}

package com.mafuyu404.taczaddon.common;

import com.mafuyu404.taczaddon.refit.RefitExternalInstallCommitSequence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class RefitExternalInstallServiceArchitectureTest {
    @Test
    void externalInstallIsAuthoritativeAndTransactional()
            throws IOException {
        String service = Files.readString(
                Path.of(
                        "src/main/java/com/mafuyu404/taczaddon/refit/RefitExternalInstallService.java"
                ),
                StandardCharsets.UTF_8
        );
        String normalized = service.replaceAll("\\s+", "");

        assertTrue(normalized.contains(
                "RefitSourceResolver.resolveExternalSources(player)"
        ));
        assertTrue(normalized.contains(
                "RefitSourceResolver.findSource("
        ));
        assertTrue(normalized.contains("request.locator()"));
        assertTrue(normalized.contains("source.isValid(player)"));
        assertTrue(normalized.contains("rollbackExtraction("));
        assertTrue(normalized.contains("player.drop(remainder,false)"));
        assertTrue(normalized.contains(
                "LiberateAttachmentService.isEnabled(player)"
        ));

        /*
         * The extraction, identity and compensation rules live in one focused
         * transaction type so they can be verified on their own.
         */
        String transaction = Files.readString(
                Path.of(
                        "src/main/java/com/mafuyu404/taczaddon/refit/RefitExternalInstallTransaction.java"
                ),
                StandardCharsets.UTF_8
        ).replaceAll("\\s+", "");

        assertTrue(
                transaction.contains(
                        "source.getStackInSlot(slot).copy()"
                ),
                "the identity snapshot must be frozen before extraction"
        );
        assertTrue(transaction.contains(
                "source.extractItem(slot,1,true)"
        ));
        assertTrue(transaction.contains(
                "source.extractItem(slot,1,false)"
        ));
        assertTrue(transaction.contains(
                "matchesExtracted(extracted,expected)"
        ));
        assertTrue(transaction.contains(
                "gun.installAttachment(extracted)"
        ));

        int restoreCheck = transaction.indexOf(
                "host.restoreOwnedSnapshot(snapshot)"
        );
        int returnExtraction = transaction.indexOf(
                "host.returnExtractionToSource(extracted)",
                restoreCheck
        );
        assertTrue(
                restoreCheck >= 0 && returnExtraction > restoreCheck,
                "the extracted item may only be returned after the gun state was verified as restored"
        );

        /*
         * A compensation that cannot be verified must not be reported as one.
         * Both halves of the return path have to feed the outcome decision.
         */
        assertTrue(
                transaction.contains(
                        "if(!host.restoreOwnedSnapshot(snapshot))"
                ),
                "an unverified gun restore must fail closed"
        );
        assertTrue(
                transaction.contains(
                        "if(!host.returnExtractionToSource(extracted))"
                ),
                "an unverified source return must fail closed"
        );
        assertTrue(
                transaction.contains(
                        "Outcome.MUTATION_FAILED_UNKNOWN"
                ),
                "unknown state must be reported instead of compensated"
        );
        assertTrue(
                transaction.contains(
                        "booleanreturnExtractionToSource(ItemStackextracted)"
                ) || transaction.contains(
                        "booleanreturnExtractionToSource("
                ),
                "the host contract reports whether the return is known"
        );

        assertTrue(
                normalized.contains(
                        "if(player.drop(remainder.copy(),false)==null)"
                ),
                "the drop fallback must be verified before reporting success"
        );
        assertTrue(
                normalized.contains(
                        "returnrollbackExtraction("
                ),
                "the service must propagate the compensation result"
        );
        assertFalse(
                normalized.contains(
                        "returnReplacedAttachment(player,replaced)"
                ),
                "the replaced attachment now travels through the result"
        );

        assertTrue(
                normalized.contains(
                        "RefitExternalInstallCommitSequence.complete("
                ),
                "the successful path must cross one explicit ownership commit sequence"
        );
        assertTrue(
                normalized.contains("newPostCommitSteps("),
                "post-commit effects must be isolated from the transaction host"
        );

        int postCommitStart = service.indexOf(
                "private static final class PostCommitSteps"
        );
        int postCommitEnd = service.indexOf(
                "private static void logOutcome",
                postCommitStart
        );
        assertTrue(postCommitStart >= 0 && postCommitEnd > postCommitStart);
        String postCommit = service.substring(
                postCommitStart,
                postCommitEnd
        );

        assertTrue(
                postCommit.contains("returnReplacedAttachment(")
                        && postCommit.contains(
                                "AttachmentPropertyManager.postChangeEvent("
                        ),
                "ownership finalization must live before property-cache post-commit effects"
        );
        assertFalse(
                postCommit.contains(
                        "RefitExternalInstallTransaction"
                ),
                "post-commit handling must not re-enter the transaction"
        );
        assertFalse(
                postCommit.contains("rollbackExtraction(")
                        || postCommit.contains(
                                "returnExtractionToSource("
                        )
                        || postCommit.contains("installAttachment("),
                "post-commit handling must not repeat extraction, install or compensation"
        );
    }

    @Test
    void ownershipFinalizationPrecedesPostCommitEffects() {
        List<String> calls = new ArrayList<>();
        RecordingSteps steps = new RecordingSteps(
                calls,
                Set.of()
        );

        boolean complete =
                RefitExternalInstallCommitSequence.complete(
                        steps,
                        failure -> {
                            throw new AssertionError(
                                    "unexpected post-commit failure",
                                    failure
                            );
                        }
                );

        assertTrue(complete);
        assertEquals(
                List.of(
                        "ownership",
                        "postChangeEvent",
                        "dropAmmo",
                        "synchronize",
                        "refresh"
                ),
                calls
        );
    }

    @Test
    void postCommitFailuresStillSynchronizeAndRefresh() {
        List<String> calls = new ArrayList<>();
        List<Throwable> failures = new ArrayList<>();
        RecordingSteps steps = new RecordingSteps(
                calls,
                Set.of(
                        "postChangeEvent",
                        "dropAmmo"
                )
        );

        boolean complete =
                RefitExternalInstallCommitSequence.complete(
                        steps,
                        failures::add
                );

        assertFalse(
                complete,
                "a failed post-commit effect must be reported"
        );
        assertEquals(
                List.of(
                        "ownership",
                        "postChangeEvent",
                        "dropAmmo",
                        "synchronize",
                        "refresh"
                ),
                calls,
                "post-commit failures must not repeat ownership or transaction work"
        );
        assertEquals(2, failures.size());
        assertEquals(
                1,
                calls.stream()
                        .filter("ownership"::equals)
                        .count(),
                "ownership finalization runs exactly once"
        );
    }

    @Test
    void ownershipFailureAbortsBeforePostCommitEffects() {
        List<String> calls = new ArrayList<>();
        RecordingSteps steps = new RecordingSteps(
                calls,
                Set.of("ownership")
        );

        assertThrows(
                IllegalStateException.class,
                () -> RefitExternalInstallCommitSequence.complete(
                        steps,
                        failure -> {
                            throw new AssertionError(
                                    "ownership failure is a pre-boundary failure",
                                    failure
                            );
                        }
                )
        );
        assertEquals(
                List.of("ownership"),
                calls,
                "no post-commit effect may run before ownership finalization succeeds"
        );
    }

    private static final class RecordingSteps
            implements RefitExternalInstallCommitSequence.Steps {
        private final List<String> calls;
        private final Set<String> failingSteps;

        private RecordingSteps(
                List<String> calls,
                Set<String> failingSteps
        ) {
            this.calls = calls;
            this.failingSteps = failingSteps;
        }

        @Override
        public void finalizeOwnership() {
            record("ownership");
        }

        @Override
        public void postChangeEvent() {
            record("postChangeEvent");
        }

        @Override
        public void dropAmmo() {
            record("dropAmmo");
        }

        @Override
        public void synchronizeAuthoritativeState() {
            record("synchronize");
        }

        @Override
        public void refreshScreen() {
            record("refresh");
        }

        private void record(String step) {
            this.calls.add(step);
            if (this.failingSteps.contains(step)) {
                throw new IllegalStateException(step);
            }
        }
    }
}

package io.github.rareities.protonbridge.util;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/** Robolectric does not implement Android directory fsync; durability has separate injected tests. */
public final class DirectoryBarrierRule implements TestRule {
    @Override public Statement apply(Statement base, Description description) {
        return new Statement() {
            @Override public void evaluate() throws Throwable {
                DurableFiles.DirectorySync previous = DurableFiles.directorySync;
                DurableFiles.directorySync = directory -> { };
                try { base.evaluate(); } finally { DurableFiles.directorySync = previous; }
            }
        };
    }
}

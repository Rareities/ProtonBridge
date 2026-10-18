package io.github.rareities.protonbridge.Services;

/** Counts-only notification deduplication; lifecycle/lock ownership remains in RcdService. */
final class RcNotificationState {
    private boolean published;
    private long running;
    private long finished;
    private long failed;

    synchronized boolean update(long running, long finished, long failed) {
        if (published && this.running == running && this.finished == finished && this.failed == failed) return false;
        this.running = running;
        this.finished = finished;
        this.failed = failed;
        published = true;
        return true;
    }

    synchronized void reset() { published = false; }
}

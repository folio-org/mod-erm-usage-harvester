package org.olf.erm.usage.harvester.worker;

import io.vertx.core.Future;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.olf.erm.usage.harvester.worker.WorkerVerticle.ParkedItem;

/**
 * A {@link WorkerController} for tests. It doesn't run anything, it only records the calls it
 * receives.
 */
final class TestWorkerController implements WorkerController {

  final List<QueueItem> queueItems = new ArrayList<>();

  boolean concurrencyWasDisabled = false;

  boolean startQueueWithWasCalled = false;

  Function<QueueItem, Future<Void>> queueCallback;

  boolean undeployWasCalled = false;

  String abortMessage = "";

  Throwable abortThrowable;

  final List<ParkCall> parkCalls = new ArrayList<>();

  @Override
  public void enqueue(List<QueueItem> items) {
    queueItems.addAll(items);
  }

  @Override
  public void disableConcurrency() {
    concurrencyWasDisabled = true;
  }

  @Override
  public void startQueueWith(Function<QueueItem, Future<Void>> callback) {
    this.startQueueWithWasCalled = true;
    this.queueCallback = callback;
  }

  @Override
  public void abort(Throwable cause) {
    this.abortThrowable = cause;
  }

  @Override
  public void abort(String message) {
    this.abortMessage = message;
  }

  @Override
  public boolean isAborted() {
    return abortThrowable != null || !abortMessage.isEmpty();
  }

  @Override
  public Future<Void> undeploy() {
    this.undeployWasCalled = true;
    return Future.succeededFuture();
  }

  @Override
  public void park(List<QueueItem> items, Instant dueAt, String reason) {
    parkCalls.add(new ParkCall(items, dueAt, reason));
  }

  @Override
  public List<ParkedItem> sealParked() {
    return List.of();
  }

  /** One recorded call of {@link #park(List, Instant, String)}. */
  record ParkCall(List<QueueItem> items, Instant dueAt, String reason) {}
}

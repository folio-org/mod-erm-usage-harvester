package org.olf.erm.usage.harvester.worker;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.olf.erm.usage.harvester.FetchItem;
import org.olf.erm.usage.harvester.retry.ParkedItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages the queue, manages the concurrency and manages deploying and undeploying "Verticles" */
interface WorkerController {

  /**
   * Adds items to the queue
   *
   * @param items the queue items
   */
  void enqueue(List<QueueItem> items);

  /** Disables concurrency */
  void disableConcurrency();

  /**
   * Starts the queue using the callback passed in
   *
   * @param callback the callback to use when a queue item is processed
   */
  void startQueueWith(Function<QueueItem, Future<Void>> callback);

  /**
   * Abort a task because of the exception thrown
   *
   * @param cause the exception thrown
   */
  void abort(Throwable cause);

  /**
   * Abort a task, the message contains the reason
   *
   * @param message The message explaining why the task was dropped
   */
  void abort(String message);

  /** Undeploy all the "Verticles" */
  void undeploy();

  /**
   * Parks items that are to be retried later, in a separate retry run.
   *
   * @param items the queue items to retry, with the retry count they get in the retry run
   * @param dueAt the earliest point in time the items may be retried
   * @param reason a short description of why the items are retried, for logs
   */
  void park(List<QueueItem> items, Instant dueAt, String reason);

  /**
   * Closes the list of parked items and returns it. Later calls to {@link #park(List, Instant,
   * String)} don't add anything, and later calls to this method return the same items.
   *
   * <p>Why sealing: a run can end while fetches are still in flight, because {@link #abort(String)}
   * completes the run immediately (e.g. after too many failed uploads). If such a fetch parks an
   * item after the parked items were handed over, the item can no longer be scheduled. Sealing
   * turns this silent loss into a logged warning; the month is fetched again by the next regular
   * run, and its failed attempts don't change.
   *
   * @return an immutable snapshot of the parked items
   */
  List<ParkedItem> sealParked();

  final class DefaultWorkerController implements WorkerController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultWorkerController.class);

    private final LoggerContext logCtx;

    private final Vertx vertx;

    private final Context context;

    private int maxConcurrency;

    private final AtomicInteger currentTasks;

    private final LinkedBlockingQueue<QueueItem> queue;

    private final Promise<Void> finished;

    private final List<ParkedItem> parked;

    private boolean sealed;

    /**
     * @param vertx the entry point to the Vert.x core
     * @param context the Vert.x context
     * @param initialConcurrency the initial concurrency
     * @param logCtx the logger context
     */
    DefaultWorkerController(
        final Vertx vertx,
        final Context context,
        final int initialConcurrency,
        final LoggerContext logCtx) {
      this.logCtx = logCtx;
      this.vertx = vertx;
      this.context = context;
      this.maxConcurrency = initialConcurrency;

      this.currentTasks = new AtomicInteger(0);
      this.queue = new LinkedBlockingQueue<>();
      this.finished = Promise.promise();
      this.parked = new ArrayList<>();
      this.sealed = false;
    }

    /** {@inheritDoc} */
    @Override
    public void enqueue(List<QueueItem> items) {
      queue.addAll(items);
    }

    /** {@inheritDoc} */
    @Override
    public void disableConcurrency() {
      this.maxConcurrency = 1;
    }

    /** {@inheritDoc} */
    @Override
    public void startQueueWith(final Function<QueueItem, Future<Void>> callback) {
      for (int i = 1; i <= maxConcurrency; i++) {
        startNextWith(callback);
      }
      setTimerAndCheckIfQueueEmptyAndUndeploy();
    }

    private void setTimerAndCheckIfQueueEmptyAndUndeploy() {
      vertx.setPeriodic(
          5000,
          id -> {
            if (queue.isEmpty() && currentTasks.get() == 0) {
              vertx.cancelTimer(id);
              undeploy();
            }
          });
    }

    private void startNextWith(final Function<QueueItem, Future<Void>> callback) {
      if (currentTasks.get() < maxConcurrency) {
        final var queueItem = queue.poll();
        if (queueItem != null) {
          currentTasks.incrementAndGet();
          callback
              .apply(queueItem)
              .onComplete(
                  ar -> {
                    currentTasks.decrementAndGet();
                    startNextWith(callback);
                  });
        }
      }
    }

    /** {@inheritDoc} */
    @Override
    public void abort(Throwable cause) {
      finished.fail(cause);
      undeploy();
    }

    /** {@inheritDoc} */
    @Override
    public void abort(String message) {
      finished.tryFail(message);
      undeploy();
    }

    /** {@inheritDoc} */
    @Override
    public void undeploy() {
      finished.tryComplete();
      queue.clear();

      if (!vertx.deploymentIDs().contains(context.deploymentID())) {
        return;
      }

      vertx
          .undeploy(context.deploymentID())
          .onSuccess(v -> LOGGER.info(logCtx.createMsg("Undeployed WorkerVerticle")))
          .onFailure(
              t ->
                  LOGGER.error(
                      logCtx.createMsg("Error during undeploying: {}", t.getMessage()), t));
    }

    // park() runs on the Vert.x context and sealParked() on the Quartz thread: synchronized makes
    // them thread-safe, and the sealed flag rejects late parks after the hand-off.

    /** {@inheritDoc} */
    @Override
    public synchronized void park(List<QueueItem> items, Instant dueAt, String reason) {
      if (sealed) {
        LOGGER.warn(
            logCtx.createMsg(
                "Parked items already handed over, dropping {} (due at {}, reason: {})",
                items,
                dueAt,
                reason));
        return;
      }
      items.forEach(qi -> parked.add(new ParkedItem(qi.item(), qi.retryCount(), dueAt, reason)));
    }

    /** {@inheritDoc} */
    @Override
    public synchronized List<ParkedItem> sealParked() {
      sealed = true;
      return List.copyOf(parked);
    }

    /**
     * @return the "finished" promise to determine whether everything is done or not. Not part of
     *     the interface, since the interface consumers don't need it.
     */
    Promise<Void> getFinished() {
      return finished;
    }

    /**
     * @return the queue this controller manages. Only used for tests! Not part of the interface,
     *     since the interface consumers don't need it.
     */
    LinkedBlockingQueue<QueueItem> getQueue() {
      return this.queue;
    }

    /**
     * @return the current maximum number of concurrent tasks allowed. Only used for tests! Not part
     *     of the interface, since the interface consumers don't need it.
     */
    int getMaxConcurrency() {
      return this.maxConcurrency;
    }
  }

  /**
   * Item to be put to the queue
   *
   * @param item the fetch item
   * @param retryCount how many retries to fetch the item
   */
  record QueueItem(FetchItem item, int retryCount) {

    /**
     * Turns a list of {@link FetchItem} items into a list of {@link QueueItem} items.
     *
     * @param itemList the list of fetch items
     * @param retryCount how many retries are allowed
     * @return the list of queue items
     */
    static List<QueueItem> createQueueItemList(
        final List<FetchItem> itemList, final int retryCount) {
      return itemList.stream().map(item -> new QueueItem(item, retryCount)).toList();
    }

    /**
     * @param item the fetch item
     * @param retryCount the retry count
     * @return the new queue item
     */
    static QueueItem of(final FetchItem item, final int retryCount) {
      return new QueueItem(item, retryCount);
    }
  }
}

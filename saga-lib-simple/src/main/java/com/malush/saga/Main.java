package com.malush.saga;

import com.codebullets.sagalib.*;
import com.codebullets.sagalib.startup.EventStreamBuilder;
import com.malush.saga.interceptor.CustomSagaInterceptor;
import com.malush.saga.storage.CustomStorage;
import com.malush.saga.workflow.buy.CompensateTicketRequest;
import com.malush.saga.workflow.buy.SellTicketRequest;
import com.malush.saga.workflow.buy.SellTicketSagaProviderFactory;
import com.malush.saga.workflow.buy.SellTicketState;
import com.malush.saga.workflow.participants.rms.reply.ChargeRetailerReply;
import com.malush.saga.workflow.participants.rms.reply.CompensateRetailerReply;
import com.malush.saga.workflow.participants.rms.reply.RMSReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Simple lightweight application showcasing the basic usage of saga-lib in the context of Microservice Architecture
 * and eventual consistency between services
 * <p>
 * To run the app either run the Main.java from your favorite IDE or:
 * 1. run the ./gradlew build
 * 2. go to build/distributions
 * 3. extract the archive, for example tar -xvf saga-lib-simple-x.x.x.tar
 * 4. go inside the bin folder to find the OS specific start script
 * 5. run the script, e.g. ./saga-lib-simple
 * <p>
 * To turn on the debug log, go to saga-lib-playground/saga-lib-simple/src/main/resources/log4j2.yml and change root log level from info to debug
 */
public class Main {
  private static Logger log = LoggerFactory.getLogger(Main.class);

  private static SellTicketSagaProviderFactory sellTicketsSagaProviderFactory = new SellTicketSagaProviderFactory();
  private static CustomStorage customStorage = new CustomStorage();
  private static CustomSagaInterceptor interceptor = new CustomSagaInterceptor();
  private static final MessageStream sellTicketsMsgStream = EventStreamBuilder.configure()
      .usingStorage(customStorage)
      .usingSagaProviderFactory(sellTicketsSagaProviderFactory)
      .callingInterceptor(interceptor)
      .build();

  /**
   * Library usage examples
   */
  public static void main(String[] args) {

    log.info("Saga-lib test app started");

    ExecutorService executor = Executors.newFixedThreadPool(4);

    try {
      log.info("------------------------------------------------------------------------");
      log.info("Best case: the request comes in and is successfully processed by RMS");
      log.info("------------------------------------------------------------------------");

      String requestId = UUID.randomUUID().toString();
      String retailerId = "retailerX";
      double amount = 10;

      executor.submit(new RestAPITask(new SellTicketRequest(requestId, retailerId, amount)));
      TimeUnit.MILLISECONDS.sleep(500); //wait for the first thread to send a request to RMS and then start another thread as a successful mock RMS reply.
      executor.submit(new RMSReplyTask(new ChargeRetailerReply(true, requestId, amount, retailerId)));

      TimeUnit.SECONDS.sleep(1); //pause for a better log reading experience
      log.info("------------------------------------------------------------------------");
      log.info("Timeout case: the request comes in but the reply from RMS does not come back within the predefined time limit");
      log.info("------------------------------------------------------------------------");

      requestId = UUID.randomUUID().toString();
      executor.submit(new RestAPITask(new SellTicketRequest(requestId, retailerId, amount)));
      TimeUnit.SECONDS.sleep(2); // Sleep for 2 seconds which will fire handleTimout event handler that will do the compensation
      // Late reply from RMS should be ignored as no saga is found
      executor.submit(new RMSReplyTask(new ChargeRetailerReply(true, requestId, amount, retailerId)));
      // Eventually we should receive RMS compensation reply which should finish the saga.
      // Until the response is received the Saga will not be finished. What happens with open Saga's will be explained in another example.
      // In the following line we are just simulating that we received a compensation response from one of the participants (RMS),
      // and in this particular case where we only have RMS then this message will end the saga.
      TimeUnit.SECONDS.sleep(1);
      executor.submit(new RMSReplyTask(new CompensateRetailerReply(true, requestId, amount, retailerId)));

      TimeUnit.SECONDS.sleep(1); //pause for a better log reading experience
      log.info("------------------------------------------------------------------------");
      log.info("RMS error case: the request comes in and is successfully received by RMS but it detects a problem and returns back an error");
      log.info("which should fire compensation");
      log.info("------------------------------------------------------------------------");

      requestId = UUID.randomUUID().toString();
      executor.submit(new RestAPITask(new SellTicketRequest(requestId, retailerId, amount)));
      TimeUnit.MILLISECONDS.sleep(500); //wait for the first thread to send a request to RMS and then start another thread as a failed mock RMS reply.
      // RMS replies that their local transaction was not successful. This will trigger compensation
      executor.submit(new RMSReplyTask(new ChargeRetailerReply(false, requestId, amount, retailerId)));
      // After compensation was triggered with the RMS reply above then Orchestrator sent out compensation requests to all participants including even RMS for simplicity reasons.
      // The compensation operations should be idempotent, and all participants should expect to receive compensations for the same requestId multiple times.
      // In the following line RMS is replying that compensation went successfully, even if compensation was not needed at this point, as they replied that their transaction failed.
      // This is just a proposal, the user of the library can choose not to send the compensation request if the orchestrator knows that there is no need to send it to a particular participant.
      TimeUnit.MILLISECONDS.sleep(500); //wait for the RMSReplyTask to finish
      executor.submit(new RMSReplyTask(new CompensateRetailerReply(true, requestId, amount, retailerId)));

      TimeUnit.SECONDS.sleep(1); //pause for a better log reading experience
      log.info("------------------------------------------------------------------------");
      log.info("Compensation case: Simulate execution of a backend process that triggers compensation for unfinished Saga's");
      log.info("------------------------------------------------------------------------");

      //For example two Sell Ticket REST requests arrive at one point

      String requestId1 = UUID.randomUUID().toString();
      String requestId2 = UUID.randomUUID().toString();

      // These request will create two Saga's, both sending the chargeRetailer command to RMS
      executor.submit(new RestAPITask(new SellTicketRequest(requestId1, "retailerA", 10)));
      executor.submit(new RestAPITask(new SellTicketRequest(requestId2, "retailerB", 10)));
      TimeUnit.MILLISECONDS.sleep(100);

      //if scheduler is executed at this point is should not compensate these two requests
      executor.submit(new ScheduledTask());

      TimeUnit.MILLISECONDS.sleep(500);
      // For the first request RMS replies that the transaction was unsuccessful.
      // The corresponding REST API thread will respond back to the client that Saga was unsuccessful and that compensation was triggered.
      executor.submit(new RMSReplyTask(new ChargeRetailerReply(false, requestId1, 10, "retailerA")));

      //At some point the timeout for requestId1 will occur and the compensation will be triggered again, and compensation command sent again to RMS.

      // For the second request let's assume that RMS sends a reply but it was not received by the Orchestrator because a message broker was down,
      // therefore the Saga timeout will occur for request 2 which will trigger compensation for this request.
      // The corresponding  REST API thread will respond back to the client that Saga was unsuccessful and that compensation was triggered.
      // But, since the message broker is down, the compensation message will not reach RMS, therefore the saga will not actually be finished.
      TimeUnit.SECONDS.sleep(2);

      // For this thread the timeout will not occur as it was already triggered

      // Now let's assume that some time has passed and the scheduler was started again.
      // Now it should find all hanging saga's from db and send one more time the compensation commands to RMS for all of them
      // This will happen again and again until the compensation reply is received from all participants and the saga is finished
      executor.submit(new ScheduledTask());

      TimeUnit.SECONDS.sleep(1);

      // Suddenly we are able to communicate with RMS again and we receive compensation replies. This should close both saga's.
      executor.submit(new RMSReplyTask(new CompensateRetailerReply(true, requestId1, 10, "retailerA")));
      executor.submit(new RMSReplyTask(new CompensateRetailerReply(true, requestId2, 10, "retailerB")));

    } catch (Exception e) {
      log.error("Error while handling messages: {}", e);
    } finally {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException e){
        executor.shutdownNow();
      }
    }
  }

  /**
   * We want to simulate a REST API usage as in the real scenario REST most probably be used as an entry point to Saga.
   * The API is used by a client application when it wants to start a saga workflow
   * Each incoming request will be processed by a different thread, and here we are defining a runnable task that will be executed when the thread is started.
   * It will add the sellTicketRequest to the message stream and wait until the saga is completed, or until compensation is triggered,
   * when it will return the response back to the client
   */
  private static class RestAPITask implements Runnable {
    private SellTicketRequest request;

    public RestAPITask(SellTicketRequest request) {
      this.request = request;
    }

    @Override
    public void run() {
      log.info("Starting restAPI thread: {}", Thread.currentThread().getName());
      log.info("Sell Ticket REST API call: requestId = {}, retailerId = {}, amount = {}", request.requestId, request.retailerId, request.amount);
      try {
        //start the saga by forwarding the request to the StartSaga event handler
        sellTicketsMsgStream.handle(request);
      } catch (Exception e) {
        log.error("Exception while trying to handle sellTicketRequest: ", e);
      }
      //Interceptor is used to detect if saga was successfully completed or if compensation was triggered in order to be able to return a response back to the client.
      //By calling the getSagaResult method here we will put the thread into a waiting state. The thread is unblocked on successful saga completion or if compensation is triggered.
      String result = interceptor.getSagaResult(request.requestId);
      log.info("Sell Ticket REST API response: requestId = {}, message = {}", request.requestId, result);
      log.info("Exiting restAPI thread: {}", Thread.currentThread().getName());
    }
  }

  /**
   * We want to simulate the response coming back from RMS (via some protocol, in our case AMQP).
   * In the real usage scenario we will be receiving a response from RMS via a reply queue.
   * When we receive a message from RMS we will add it to the message stream which will trigger the appropriate Saga event handler.
   */
  private static class RMSReplyTask implements Runnable {
    private RMSReply reply;

    private RMSReplyTask(ChargeRetailerReply reply) {
      this.reply = reply;
    }

    private RMSReplyTask(CompensateRetailerReply reply) {
      this.reply = reply;
    }

    @Override
    public void run() {
      log.info("Starting rmsReply thread: {}", Thread.currentThread().getName());
      Optional.of(reply)
          .filter(ChargeRetailerReply.class::isInstance)
          .map(ChargeRetailerReply.class::cast)
          .ifPresent(reply -> {
            try {
              sellTicketsMsgStream.handle(reply);
            } catch (Exception e) {
              log.error("Message handling error", e);
            }
          });
      Optional.of(reply)
          .filter(CompensateRetailerReply.class::isInstance)
          .map(CompensateRetailerReply.class::cast)
          .ifPresent(reply -> {
            try {
              sellTicketsMsgStream.handle(reply);
            } catch (Exception e) {
              log.error("Message handling error", e);
            }
          });

      log.info("Exiting rmsReply thread: {}", Thread.currentThread().getName());
    }
  }

  /**
   * We will have a scheduler that will endlessly try to compensate and finish all hanging Saga's by submitting the compensation message into the stream.
   * According to Saga Pattern, it is the responsibility of the Orchestrator to try this forever in a loop until successful.
   * The alternative to the scheduler would be to have an infinite loop in the SellTicketSaga that makes sure the compensation was successful,
   * but that would block the thread per request. We don't want to block the thread, even for a while since if at least one participant becomes unavailable
   * for some prolonged period of time then blocking the thread would potentially take a lot if not all of available resources.
   * Therefore a scheduled process (e.g. 5 minute interval) is a better option.
   * Scheduler should first read the unfinished Saga's from DB, then load the state from memory storage, and if it cannot find that sagaId in memory
   * it should compensate that Saga by sending the compensation command to all of the participants.
   * If it finds the saga in memory it should load the state and see from the state if compensation was triggered.
   * In our simple example this can be detected via SellTicketState.compensationTriggered flag.
   * If the scheduler loads the saga from DB and finds it in memory but it determines from its state that compensation was not triggered,
   * then it should not trigger compensation as this saga might still be in progress, not yet eligible for compensation
   * (e.g. timeout didn't occur yet, or participants didn't yet reply if their local transaction was successful or not)
   */

  private static class ScheduledTask implements Runnable {

    @Override
    public void run() {
      log.info("Starting Scheduler thread: {}", Thread.currentThread().getName());
      //load all hanging saga's from db
      List<String> unfinishedSagas = customStorage.getUnfinishedSagasFromDB();
      boolean sagaFoundInMemory = false;
      for (String requestId : unfinishedSagas) {
        //boolean sagaFoundInMemory = false;
        //try to find saga in memory
        Optional<SellTicketState> inMemoryState =
            customStorage.load("SellTicketSaga", requestId)
                .stream()
                .filter(SellTicketState.class::isInstance)
                .map(SellTicketState.class::cast)
                .findFirst();

        inMemoryState.ifPresent(state -> {
          try {
            // Only trigger compensation for those in-memory saga's that are already flagged for compensation.
            // Otherwise it might be saga that is just starting or is waiting for all participants to reply before the saga timeout
            if (state.isCompensationTriggered()) {
              sellTicketsMsgStream.handle(new CompensateTicketRequest(state.getRequestId(), state.getAmount(), state.getRetailerId()));
            }
          } catch (Exception e) {
            log.error("Message handling error", e);
          }
        });

        // If saga is not found in memory then do the compensation. This will occur for example if the Orchestrator has crashed
        // and there's nothing in memory.
        // Also, because of edge cases where we are not able to flag the transaction for compensation (a system glitch, software bug, etc)
        // it might be good to be able to clear the memory and then rely only on database storage to be able to compensate all transactions and start clean.
        if(!inMemoryState.isPresent()) {
          SellTicketState state = customStorage.getSagaStateFromDB(requestId);
          try {
            sellTicketsMsgStream.handle(new CompensateTicketRequest(state.getRequestId(), state.getAmount(), state.getRetailerId()));
          } catch (Exception e) {
            log.error("Message handling error", e);
          }
        }
      }

      log.info("Exiting Scheduler thread: {}", Thread.currentThread().getName());
    }
  }
}

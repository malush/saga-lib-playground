package com.malush.saga.interceptor;

import com.codebullets.sagalib.ExecutionContext;
import com.codebullets.sagalib.Saga;
import com.codebullets.sagalib.SagaLifetimeInterceptor;
import com.malush.saga.workflow.buy.SellTicketState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class CustomSagaInterceptor implements SagaLifetimeInterceptor {

  private final Logger log = LoggerFactory.getLogger(CustomSagaInterceptor.class);

  private ConcurrentMap<String, String> sagaResultMap = new ConcurrentHashMap<>();

  @Override
  public void onStarting(Saga<?> saga, ExecutionContext context, Object message) {}

  @Override
  public void onHandlerExecuting(Saga<?> saga, ExecutionContext context, Object message) {  }

  /**
   *  We want to intercept every handler and determine if the compensation has been triggered after execution.
   *  If it has, then we can notify the waiting thread that is waiting for this particular Saga to execute.
   *  In this case we know that the compensation has been triggered which is enough to return the response immediately
   *  to the client without waiting for compensation to be completed and for all Saga participants to reply to the
   *  compensation command.
   *
   *  For example, it might be that we have sent a command to RMS but since the message broker was down the message never
   *  got through. After the timeout event triggers the compensation we will send the compensation command to all participants
   *  (in this case only RMS) but since the message broker is down this message would also not be received and processed,
   *  which means that the Saga would not be able to finish and the calling thread (REST API) will remain waiting all this time.
   *  Instead we should just notify the thread that the compensation has been triggered and notify the client immediately that
   *  the requested transaction failed. In the background it is up to the Orchestrator to endlessly try and make all services
   *  consistent with each other by sending compensation commands until all participants reply that the compensation was successful.
   *  This is done asynchronously and we don't need to keep the client waiting, or timing out.
   *
   */
  @Override
  public void onHandlerExecuted(Saga<?> saga, ExecutionContext context, Object message) {
    Optional.of(saga.state())
        .filter(SellTicketState.class::isInstance)
        .map(SellTicketState.class::cast)
        .ifPresent(sellTicketState -> {
          if(sellTicketState.isCompensationTriggered()) {
            setSagaResult(sellTicketState.getRequestId(), "SAGA FAILED, TRANSACTION COMPENSATED");
          }
        });
  }

  @Override
  public void onFinished(Saga<?> saga, ExecutionContext context) {

    Optional.of(saga.state())
        .filter(SellTicketState.class::isInstance)
        .map(SellTicketState.class::cast)
        .ifPresent(sellTicketState -> setSagaResult(sellTicketState.getRequestId(), "SAGA FINISHED SUCCESSFULLY"));
  }

  private synchronized void setSagaResult(String requestId, String message) {
    sagaResultMap.put(requestId, message);
    notifyAll();
  }

  public synchronized String getSagaResult(String requestId) {
    while (!sagaResultMap.containsKey(requestId)) {
      try {
        wait();
      } catch (InterruptedException e) {
        log.error("Interrupted Exception");
      }
    }
    return sagaResultMap.remove(requestId);
  }
}

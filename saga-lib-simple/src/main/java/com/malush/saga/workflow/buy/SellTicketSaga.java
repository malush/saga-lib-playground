package com.malush.saga.workflow.buy;

import com.codebullets.sagalib.*;
import com.codebullets.sagalib.timeout.Timeout;
import com.malush.saga.workflow.participants.itms.ITMS;
import com.malush.saga.workflow.participants.rms.RMS;
import com.malush.saga.workflow.participants.rms.reply.ChargeRetailerReply;
import com.malush.saga.workflow.participants.rms.reply.CompensateRetailerReply;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

public class SellTicketSaga extends AbstractSaga<SellTicketState> {

  private final Logger log = LoggerFactory.getLogger(SellTicketSaga.class);

  private final RMS rms;
  private final ITMS itms;

  @Inject
  public SellTicketSaga(final RMS rms, final ITMS itms) {
    this.rms = rms;
    this.itms = itms;
  }

  @StartsSaga
  public void ticketSale(final SellTicketRequest sellTicketRequest) throws Exception {
    log.info("ticketSale request: amount = {}, retailerId = {}", sellTicketRequest.amount, sellTicketRequest.retailerId);
    rms.chargeRetailerCMD(sellTicketRequest.requestId, sellTicketRequest.amount, sellTicketRequest.retailerId);
    state().setRequestId(sellTicketRequest.requestId);
    state().setAmount(sellTicketRequest.amount);
    state().setRetailerId(sellTicketRequest.retailerId);
    state().addInstanceKey(sellTicketRequest.requestId);

    requestTimeout(1, TimeUnit.SECONDS);

    log.info("Command sent to RMS, Saga State: requestId = {}, finishedState = {}", sellTicketRequest.requestId, context().saga().isFinished());
  }

  @EventHandler
  public void retailerCharged(final ChargeRetailerReply chargeRetailerReply) {
    //check if timeout or another participant already triggered compensation
    if (state().isCompensationTriggered()) {
      log.info("Compensation already triggered for requestId: {}. The reply is ignored.", state().getRequestId());
      return;
    }
    if (chargeRetailerReply.success) {
      try {
        itms.setTicketStatus(state().getRequestId(), state().getRetailerId(), ITMS.Status.SOLD);
        setFinished();
        log.info("Reply received from RMS and transaction processed in ITMS. Saga State: requestId = {}, finishedState = {}", state().getRequestId(), context().saga().isFinished());
      } catch (Exception e) {
        log.error("Local transaction failed in ITMS", e);
      }
    } else {
      log.info("RMS local transaction failed. Triggering compensation for requestId = {}", state().getRequestId());
      compensateTicket(new CompensateTicketRequest(chargeRetailerReply));
    }
  }

  @EventHandler
  public void compensationRequested(final CompensateTicketRequest compensateTicketRequest) {
    compensateTicket(compensateTicketRequest);
  }

  @EventHandler
  public void retailerCompensated(final CompensateRetailerReply compensateRetailerReply) {
    if (compensateRetailerReply.success) {
      state().setRmsCompensated(true);
    }
    if (state().isItmsCompensated()) {
      setFinished();
      log.info("Ticket compensated in both RMS and ITMS, requestId = {}, finishedState = {}", state().getSagaId(), context().saga().isFinished());
    }
  }

  @EventHandler
  public void handleTimeout(Timeout timeout) {
    //log.info("is state equal to timeout state: {}", timeout.getSagaId().equals(state().getSagaId()));
    log.info("Request timed out for requestId: {}", state().getRequestId());
    compensateTicket(new CompensateTicketRequest(state().getRequestId(), state().getAmount(), state().getRetailerId()));
  }


  @Override
  public void createNewState() {
    setState(new SellTicketState());
  }

  @Override
  public Collection<KeyReader> keyReaders() {
    Collection<KeyReader> readers = new ArrayList<>(1);
    readers.add(KeyReaders.forMessage(
        ChargeRetailerReply.class,
        chargeRetailerReply -> chargeRetailerReply.requestId
    ));
    readers.add(KeyReaders.forMessage(
        CompensateTicketRequest.class,
        compensateTicketRequest -> compensateTicketRequest.requestId
    ));
    readers.add(KeyReaders.forMessage(
        CompensateRetailerReply.class,
        compensateRetailerReply -> compensateRetailerReply.requestId
    ));

    return readers;
  }

  private void compensateTicket(CompensateTicketRequest compensateTicketRequest) {
    log.info("Handling compensation request. requestId = {}, retailerId = {}, sagaFinished = {}", state().getRequestId(), state().getRetailerId(), context().saga().isFinished());
    state().setCompensationTriggered(true);
    //send the command to RMS to compensate a retailer
    rms.compensateRetailerCMD(compensateTicketRequest.requestId, compensateTicketRequest.amount, compensateTicketRequest.retailerId);
    //set ticket status back to ACTIVATED in ITMS
    itms.setTicketStatus(compensateTicketRequest.requestId, compensateTicketRequest.retailerId, ITMS.Status.ACTIVATED);
    state().setItmsCompensated(true);
  }
}


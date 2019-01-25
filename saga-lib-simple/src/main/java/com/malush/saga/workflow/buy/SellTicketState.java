package com.malush.saga.workflow.buy;

import com.codebullets.sagalib.AbstractSagaState;

public class SellTicketState extends AbstractSagaState<String> {
  private String requestId;
  private double amount;
  private String retailerId;
  private boolean compensationTriggered;
  private boolean rmsCompensated;
  private boolean itmsCompensated;

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public double getAmount() {
    return amount;
  }

  public void setAmount(double amount) {
    this.amount = amount;
  }

  public String getRetailerId() {
    return retailerId;
  }

  public void setRetailerId(String retailerId) {
    this.retailerId = retailerId;
  }

  public boolean isRmsCompensated() {
    return rmsCompensated;
  }

  public boolean isCompensationTriggered() {
    return compensationTriggered;
  }

  public void setCompensationTriggered(boolean compensationTriggered) {
    this.compensationTriggered = compensationTriggered;
  }

  public void setRmsCompensated(boolean rmsCompensated) {
    this.rmsCompensated = rmsCompensated;
  }

  public boolean isItmsCompensated() {
    return itmsCompensated;
  }

  public void setItmsCompensated(boolean itmsCompensated) {
    this.itmsCompensated = itmsCompensated;
  }
}

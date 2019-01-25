package com.malush.saga.workflow.participants.rms.reply;

public class RMSReply {
  public boolean success;
  public String requestId;
  public double amount;
  public String retailerId;

  protected RMSReply(boolean success, String requestId, double amount, String retailerId) {
    this.success = success;
    this.requestId = requestId;
    this.amount = amount;
    this.retailerId = retailerId;
  }
}

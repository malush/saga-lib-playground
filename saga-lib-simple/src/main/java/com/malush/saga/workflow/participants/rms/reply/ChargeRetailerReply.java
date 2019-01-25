package com.malush.saga.workflow.participants.rms.reply;

public class ChargeRetailerReply extends RMSReply{
  public ChargeRetailerReply(boolean success, String requestId, double amount, String retailerId) {
    super(success, requestId, amount, retailerId);
  }
}

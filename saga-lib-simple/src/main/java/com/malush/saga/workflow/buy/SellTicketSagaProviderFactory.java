package com.malush.saga.workflow.buy;

import com.codebullets.sagalib.Saga;
import com.codebullets.sagalib.processing.SagaProviderFactory;
import com.malush.saga.workflow.participants.itms.ITMS;
import com.malush.saga.workflow.participants.rms.RMS;

import javax.inject.Provider;

public class SellTicketSagaProviderFactory implements SagaProviderFactory {

  @Override
  public Provider<? extends Saga> createProvider(final Class sagaClass) {
    return () -> new SellTicketSaga(new RMS(), new ITMS());
  }
}

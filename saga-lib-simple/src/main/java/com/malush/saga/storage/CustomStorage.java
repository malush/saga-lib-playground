package com.malush.saga.storage;

import com.codebullets.sagalib.SagaState;
import com.codebullets.sagalib.storage.InstanceKeySearchParam;
import com.codebullets.sagalib.storage.MemoryStorage;
import com.malush.saga.workflow.buy.SellTicketState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

/**
 * Extension of Memory Storage that uses the "real" DB to insert and soft delete states for recovery purposes in case of
 * a system crash. For other operations such as update and get we should use the faster memory storage and limit the number
 * of DB calls for a single Saga. There should be only 2 calls: Insert the state, and soft delete the state. In case of a
 * crash we should:
 * 1. read all states from a DB,
 * 2. see if these exist in memory as well
 */
public class CustomStorage extends MemoryStorage {

  private final Logger log = LoggerFactory.getLogger(CustomStorage.class);

  private ConcurrentMap<String, SellTicketState> mockDBStorage = new ConcurrentHashMap<>();

  @Override
  public void save(SagaState state) {
    log.debug("Saving saga state: sagaId = {}", state.getSagaId());
    saveInDB(state);
    super.save(state);
  }

  @Override
  public SagaState load(String sagaId) {
    log.debug("Loading saga state: sagaId = {}", sagaId);
    return super.load(sagaId);
  }

  @Override
  public void delete(String sagaId) {
    log.debug("Deleting saga state: sagaId = {}", sagaId);
    removeFromDB(sagaId);
    super.delete(sagaId);
  }

  @Override
  public Collection<? extends SagaState> load(String type, Object instanceKey) {
    log.debug("Loading saga state for type = {} and instanceKey = {}", type, instanceKey);
    return super.load(type, instanceKey);
  }

  @Override
  public Stream<? extends SagaState> loadAll(Iterable<InstanceKeySearchParam> searchParams) {
    log.debug("Loading all saga states for search params: ");
    searchParams.forEach(param -> {log.debug("type = {}, instanceKey = {}", param.getSagaTypeName(), param.getInstanceKey());});
    return super.loadAll(searchParams);
  }

  private void saveInDB(SagaState state) {
    Optional.of(state)
        .filter(SellTicketState.class::isInstance)
        .map(SellTicketState.class::cast)
        .ifPresent(sellTicketState -> {
          //insert but don't update
          if(!mockDBStorage.containsKey(sellTicketState.getRequestId())) {
            mockDBStorage.putIfAbsent(sellTicketState.getRequestId(), sellTicketState);
          }
        });
  }

  private void removeFromDB(String sagaId) {
    Optional.of(load(sagaId))
        .filter(SellTicketState.class::isInstance)
        .map(SellTicketState.class::cast)
        .ifPresent(sellTicketState -> {
          mockDBStorage.remove(sellTicketState.getRequestId());
        });
  }

  public List<String> getUnfinishedSagasFromDB() {
    return new ArrayList<>(mockDBStorage.keySet());
  }

  public SellTicketState getSagaStateFromDB (String requestId) {
    return mockDBStorage.get(requestId);
  }
}

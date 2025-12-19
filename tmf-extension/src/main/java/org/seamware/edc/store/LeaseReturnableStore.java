package org.seamware.edc.store;

public interface LeaseReturnableStore<T> {

    void returnLease(T leasedEntity);

}

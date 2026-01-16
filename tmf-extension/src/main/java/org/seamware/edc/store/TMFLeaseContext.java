package org.seamware.edc.store;

import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.LeaseContext;
import org.eclipse.edc.transaction.spi.TransactionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

// TODO: reevaluate usage together with Usage as TransferProcess representation
public class TMFLeaseContext implements LeaseContext {

    private static final int LEASE_DURATION_IN_MS = 60000;

    private final Monitor monitor;
    private final TransactionContext transactionContext;

    private final ScheduledExecutorService leaseExecutor = Executors.newSingleThreadScheduledExecutor();
    private final List<String> leaseList = Collections.synchronizedList(new ArrayList<>());


    public TMFLeaseContext(Monitor monitor, TransactionContext transactionContext) {
        this.monitor = monitor;
        this.transactionContext = transactionContext;
    }

    @Override
    public void breakLease(String s) {
        transactionContext.execute(() -> {
            synchronized (leaseList) {
                if (leaseList.contains(s)) {
                    leaseList.remove(s);
                    monitor.debug(String.format("Broke lease %s", s));
                } else {
                    monitor.debug("Lease does not exist " + s);
                }
            }
        });
    }

    @Override
    public void acquireLease(String entity) {
        transactionContext.execute(() -> {
            synchronized (leaseList) {
                if (leaseList.contains(entity)) {
                    throw new IllegalStateException(String.format("%s is currently leased", entity));
                }
                leaseList.add(entity);
                monitor.debug("Acquired lease " + entity);
            }
            scheduleBreak(entity);
        });
    }

    private void scheduleBreak(String entity) {
        leaseExecutor.schedule(() -> {
            try {
                breakLease(entity);
            } catch (Exception e) {
                monitor.warning(String.format("Failed to break lease for %s. Reschedule.", entity), e);
                scheduleBreak(entity);
            }
        }, LEASE_DURATION_IN_MS, TimeUnit.MILLISECONDS);
    }
}

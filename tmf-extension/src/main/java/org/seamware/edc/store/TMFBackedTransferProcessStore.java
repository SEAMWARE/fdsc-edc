package org.seamware.edc.store;

import org.eclipse.edc.connector.controlplane.transfer.spi.store.TransferProcessStore;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcess;
import org.eclipse.edc.connector.controlplane.transfer.spi.types.TransferProcessStates;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.persistence.LeaseContext;
import org.eclipse.edc.spi.query.Criterion;
import org.eclipse.edc.spi.query.CriterionOperatorRegistry;
import org.eclipse.edc.spi.query.QuerySpec;
import org.eclipse.edc.spi.result.StoreResult;
import org.eclipse.edc.transaction.spi.TransactionContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.seamware.edc.domain.ExtendableProduct;
import org.seamware.edc.domain.ExtendableUsageVO;
import org.seamware.edc.tmf.AgreementApiClient;
import org.seamware.edc.tmf.ProductInventoryApiClient;
import org.seamware.edc.tmf.UsageApiClient;
import org.seamware.tmforum.agreement.model.AgreementItemVO;
import org.seamware.tmforum.agreement.model.AgreementVO;
import org.seamware.tmforum.agreement.model.ProductRefVO;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class TMFBackedTransferProcessStore implements TransferProcessStore, LeaseReturnableStore<TransferProcess> {

    private final Monitor monitor;
    private final TMFEdcMapper tmfEdcMapper;
    private final CriterionOperatorRegistry criterionOperatorRegistry;
    private final LeaseContext leaseContext;
    private final UsageApiClient usageApi;
    private final AgreementApiClient agreementApi;
    private final ProductInventoryApiClient productInventoryApi;


    public TMFBackedTransferProcessStore(Monitor monitor, TMFEdcMapper tmfEdcMapper, TransactionContext transactionContext, CriterionOperatorRegistry criterionOperatorRegistry, UsageApiClient usageApi, AgreementApiClient agreementApi, ProductInventoryApiClient productInventoryApi) {
        this.monitor = monitor;
        this.tmfEdcMapper = tmfEdcMapper;
        this.criterionOperatorRegistry = criterionOperatorRegistry;
        this.usageApi = usageApi;
        this.agreementApi = agreementApi;
        this.productInventoryApi = productInventoryApi;
        this.leaseContext = new TMFLeaseContext(monitor, transactionContext);
    }

    // TODO: Solve conflict between correlation and transferprocessId

    @Override
    public @Nullable TransferProcess findForCorrelationId(String s) {

        monitor.info("Find transfer by correlation id " + s);
//        TransferProcess transferProcess = usageApi.findByTransferId(s)
//                .map(tmfEdcMapper::fromUsage)
//                .orElse(null);
//        if (transferProcess != null) {
//            monitor.info("Found transfer process " + transferProcess.getId() + " - " + TransferProcessStates.from(transferProcess.getState()).name());
//        } else {
//            monitor.info("No transfer process");
//        }
//
//        return transferProcess;
        return null;
    }

    @Override
    public void delete(String s) {
        leaseContext.acquireLease(s);
        Optional<ExtendableUsageVO> optionalUsage = usageApi.findByTransferId(s);
        if (optionalUsage.isEmpty()) {
            leaseContext.breakLease(s);
            return;
        }
        usageApi.deleteUsage(optionalUsage.get().getId());
        leaseContext.breakLease(s);
    }

    @Override
    public Stream<TransferProcess> findAll(QuerySpec querySpec) {
        monitor.info("Find all processes " + querySpec.toString());

        return Stream.empty();
    }

    @Override
    public @Nullable TransferProcess findById(String s) {
        monitor.info("Find transfer process by id " + s);
//        TransferProcess transferProcess = usageApi.findByTransferId(s)
//                .map(tmfEdcMapper::fromUsage)
//                .orElse(null);
//
//        if (transferProcess != null) {
//            monitor.info("Found transfer process " + transferProcess.getId() + " - " + TransferProcessStates.from(transferProcess.getState()).name());
//        } else {
//            monitor.info("No transfer process");
//        }
//
//        return transferProcess;
        return null;
    }

    @Override
    public @NotNull List<TransferProcess> nextNotLeased(int i, Criterion... criteria) {

        Predicate<TransferProcess> filterPredicate = Arrays.stream(criteria)
                .map(criterionOperatorRegistry::<TransferProcess>toPredicate)
                .reduce(x -> true, Predicate::and);
        List<TransferProcess> transferProcesses = new ArrayList<>();
        int offset = 0;
        boolean moreTPsAvailable = true;
        int limit = 100;
//        while (moreTPsAvailable && transferProcesses.size() < i) {
//            usageApi.getUsages(offset, limit)
//                    .stream()
//                    .map(tmfEdcMapper::fromUsage)
//                    .filter(filterPredicate)
//                    .forEach(tp -> {
//                        try {
//                            leaseContext.acquireLease(tp.getId());
//                            transferProcesses.add(tp);
//                        } catch (EdcException e) {
//                            monitor.debug(String.format("%s is already leased.", tp.getId()));
//                        }
//                    });
//
//            moreTPsAvailable = transferProcesses.size() == limit;
//            offset += limit;
//        }
        return transferProcesses;

    }

    @Override
    public StoreResult<TransferProcess> findByIdAndLease(String s) {
        monitor.info("Find by transfer process by id " + s);
        TransferProcess transferProcess = findById(s);
        try {
            leaseContext.acquireLease(s);
        } catch (EdcException e) {
            return StoreResult.alreadyLeased(String.format("TransferProcess %s is already leased.", s));
        }
        return Optional.ofNullable(transferProcess)
                .map(StoreResult::success)
                .orElse(StoreResult.notFound(String.format("Transfer Process %s does not exist.", s)));
    }

    @Override
    public void save(TransferProcess transferProcess) {
        try {
            List<AgreementItemVO> agreementItemVOS = agreementApi.findByContractId(transferProcess.getContractId())
                    .map(AgreementVO::getAgreementItem)
                    .orElseThrow(() -> new IllegalArgumentException("The referenced contract needs to reference a product."));
            if (agreementItemVOS.size() != 1) {
                throw new IllegalArgumentException("We currently only support agreements referencing exactly one item.");
            }
            List<ProductRefVO> productRefVOS = agreementItemVOS.getFirst().getProduct();
            if (Optional.ofNullable(productRefVOS).map(List::size).orElse(0) != 1) {
                throw new IllegalArgumentException("The agreement item should reference exactly one product.");
            }
            String assetId = transferProcess.getAssetId();
            if (assetId == null || assetId.isEmpty()) {
                assetId = productInventoryApi.getProductById(productRefVOS.getFirst().getId())
                        .map(ExtendableProduct::getExternalId)
                        .orElseThrow(() -> new IllegalArgumentException("The ref should contain a valid product."));

            }
            ExtendableUsageVO extendableUsageVO = tmfEdcMapper.fromTransferProcess(transferProcess, assetId, productRefVOS.getFirst().getId());
            Optional<ExtendableUsageVO> optionalUsageVO = Optional.ofNullable(transferProcess.getId())
                    .flatMap(usageApi::findByTransferId);
            if (optionalUsageVO.isPresent()) {
                usageApi.updateUsage(optionalUsageVO.get().getId(), tmfEdcMapper.toUpdate(extendableUsageVO));
            } else {
                usageApi.createUsage(tmfEdcMapper.toCreate(extendableUsageVO));
            }
        } finally {
            returnLease(transferProcess);
        }
    }

    @Override
    public void returnLease(TransferProcess transferProcess) {
        leaseContext.breakLease(transferProcess.getId());
    }
}

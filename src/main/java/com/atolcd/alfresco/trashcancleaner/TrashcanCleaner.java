package com.atolcd.alfresco.trashcancleaner;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.transaction.UserTransaction;

import org.alfresco.model.ContentModel;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TrashcanCleaner {
    private static Log logger = LogFactory.getLog(TrashcanCleaner.class);

    private NodeService nodeService;
    private TransactionService transactionService;
    private int protectedDays = 1;
    private StoreRef storeRef;
    private int deleteBatchCount = 1000;

    /**
     * @param nodeService the nodeService to set
     */
    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    /**
     * @param transactionService the transactionService to set
     */
    public void setTransactionService(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * @param protectedDays The protectedDays to set.
     */
    public void setProtectedDays(int protectedDays) {
        this.protectedDays = protectedDays;
        if (logger.isDebugEnabled()) {
            if (this.protectedDays > 0) {
                logger.debug("Deleted items will be protected during "
                        + protectedDays + " days");
            } else {
                logger.debug("Trashcan cleaner has been deactivated ('protectedDays' set to an incorrect value)");
            }
        }
    }

    public void setBatchNumber(int batchNumber) {
        deleteBatchCount = batchNumber;
        if (deleteBatchCount > 0) {
            logger.info("Number of items to be deleted: " + deleteBatchCount);
        }
    }

    public void setStoreUrl(String storeUrl) {
        this.storeRef = new StoreRef(storeUrl);
    }

    private List<NodeRef> fillBatchToDelete(List<NodeRef> batch, List<ChildAssociationRef> trashChildAssocs) {
        for (int j = trashChildAssocs.size(); j > 0 && batch.size() < this.deleteBatchCount; --j) {
            ChildAssociationRef childAssoc = (ChildAssociationRef) trashChildAssocs.get(j - 1);
            NodeRef childRef = childAssoc.getChildRef();
            if (this.olderThanDaysToKeep(childRef)) {
                batch.add(childRef);
            }
        }

        return batch;
    }

    private boolean olderThanDaysToKeep(NodeRef node) {
        if (this.protectedDays <= 0) {
            return true;
        } else {
            Date archivedDate = (Date) this.nodeService.getProperty(node, ContentModel.PROP_ARCHIVED_DATE);
            return (long) this.protectedDays * 86400000L < System.currentTimeMillis() - archivedDate.getTime();
        }
    }

    public void execute() {
        logger.warn("********* execute, protectedDays: " + protectedDays);
        System.out.println("--------------- execute, protectedDays: " + this.protectedDays + ", deleteBatchCount: " + deleteBatchCount);
        if (this.protectedDays > 0) {
            NodeRef archiveRoot = nodeService.getRootNode(storeRef);
            System.out.println("--------------- archiveRoot: " + archiveRoot.toString());
            List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);
            System.out.println("--------------- childAssocs: " + childAssocs.size());
            List<NodeRef> nodes = new ArrayList<NodeRef>(deleteBatchCount);
            List<NodeRef> deletedItemsToPurge = fillBatchToDelete(nodes, childAssocs);

            UserTransaction tx = null;
            ResultSet results = null;
            try {
                tx = this.transactionService
                        .getNonPropagatingUserTransaction(false);
                tx.begin();

                int itemsToPurge = deletedItemsToPurge.size();

                if (itemsToPurge > 0) {
                    logger.warn("Trashcan Cleaner is about to purge " + itemsToPurge + " items.");
                    System.out.println("Trashcan Cleaner is about to purge " + itemsToPurge + " items.");
                /*logger.info("Items to purge:");
                for (NodeRef item : deletedItemsToPurge) {
                  String itemName = (String) this.nodeService.getProperty(item, ContentModel.PROP_NAME);
                  logger.info(" - " + itemName);
                }*/
                } else {
                    logger.warn("There's no items to purge.");
                    System.out.println("There's no items to purge.");
                }

                for (NodeRef item : deletedItemsToPurge) {
                    this.nodeService.deleteNode(item);
                }

                tx.commit();
            } catch (Throwable err) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Error while cleaning the trashcan: "
                            + err.getMessage());
                }
                try {
                    if (tx != null) {
                        tx.rollback();
                    }
                } catch (Exception tex) {
                    if (logger.isWarnEnabled()) {
                        logger.warn("Error during the rollback: "
                                + tex.getMessage());
                    }
                }
            } finally {
                if (results != null) {
                    results.close();
                }
            }
        }
    }
}

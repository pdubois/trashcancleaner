package org.alfresco.trashcan.cleaner;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.alfresco.model.ContentModel;
import org.alfresco.query.CannedQueryFactory;
import org.alfresco.query.CannedQueryResults;
import org.alfresco.query.PagingRequest;
import org.alfresco.repo.model.filefolder.GetChildrenCannedQueryFactory;
import org.alfresco.repo.node.getchildren.GetChildrenCannedQuery;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.util.Pair;
import org.alfresco.util.registry.NamedObjectRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TrashcanCleaner
{
    private static final String CANNED_QUERY_FILEFOLDER_LIST = "fileFolderGetChildrenCannedQueryFactory";
    private static Log logger = LogFactory.getLog(TrashcanCleaner.class);

    private NodeService nodeService;
    private TransactionService transactionService;
    private int protectedDays = 7;
    private StoreRef storeRef;
    private NamedObjectRegistry<CannedQueryFactory<NodeRef>> cannedQueryRegistry;
    private int pageLen = 3;

    /**
     * Set the registry of {@link CannedQueryFactory canned queries}
     */
    public void setCannedQueryRegistry(NamedObjectRegistry<CannedQueryFactory<NodeRef>> cannedQueryRegistry)
    {
        this.cannedQueryRegistry = cannedQueryRegistry;
    }

    /**
     * @param nodeService the nodeService to set
     */
    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }

    /**
     * @param transactionService the transactionService to set
     */
    public void setTransactionService(TransactionService transactionService)
    {
        this.transactionService = transactionService;
    }

    /**
     * @param protectedDays The protectedDays to set.
     */
    public void setProtectedDays(int protectedDays)
    {
        this.protectedDays = protectedDays;
        if (logger.isDebugEnabled())
        {
            if (this.protectedDays > 0)
            {
                logger.debug("Deleted items will be protected during " + protectedDays + " days");
            }
            else
            {
                logger.debug("Trashcan cleaner has been desactivated ('protectedDays' set to an incorrect value)");
            }
        }
    }

    public void setStoreUrl(String storeUrl)
    {
        this.storeRef = new StoreRef(storeUrl);
    }

    public void execute()
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Execute called!!!!");
        }
        if (this.protectedDays > 0)
        {

            final RetryingTransactionCallback<Boolean> pagingWork = new RetryingTransactionCallback<Boolean>()
                {
                    public Boolean execute() throws Exception
                    {

                        NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                        // get canned query
                        GetChildrenCannedQueryFactory getChildrenCannedQueryFactory = (GetChildrenCannedQueryFactory) cannedQueryRegistry
                                .getNamedObject(CANNED_QUERY_FILEFOLDER_LIST);

                        List<Pair<QName, Boolean>> sortProps = new ArrayList<Pair<QName, Boolean>>();
                        sortProps.add(new Pair<QName, Boolean>(ContentModel.PROP_ARCHIVED_DATE, true));

                        Set<QName> assocTypeQNames = new HashSet<QName>(1);
                        assocTypeQNames.add(ContentModel.ASSOC_CHILDREN);

                        Set<QName> childTypeQNames = new HashSet<QName>(2);
                        assocTypeQNames.add(ContentModel.TYPE_CONTENT);
                        assocTypeQNames.add(ContentModel.TYPE_FOLDER);

                        PagingRequest pagingRequest = new PagingRequest(pageLen);

                        GetChildrenCannedQuery cq = (GetChildrenCannedQuery) getChildrenCannedQueryFactory
                                .getCannedQuery(archiveRoot, "*", assocTypeQNames, childTypeQNames, null, sortProps,
                                        pagingRequest);

                        // execute canned query
                        CannedQueryResults<NodeRef> results = cq.execute();

                        if (logger.isDebugEnabled())
                        {
                            logger.debug("Number of pages available:" + results.getPageCount());
                        }

                        List<NodeRef> pageElements = results.getPage();

                        Date toDate = new Date(new Date().getTime() - (1000L * 60L * 60L * 24L * protectedDays));

                        // display the page
                        for (NodeRef nodeRef : pageElements)
                        {
                            Date archivedDate = (Date) nodeService
                                    .getProperty(nodeRef, ContentModel.PROP_ARCHIVED_DATE);

                            if (archivedDate.compareTo(toDate) >= 0)
                                return false;
                            String name = (String) nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);
                            if (logger.isDebugEnabled())
                            {
                                logger.debug("Delete NodeRef :" + nodeRef + " $ Name :" + name + " $ archivedDate :" + archivedDate);
                            }
                            nodeService.deleteNode(nodeRef);

                        }


                        return true;
                    }
                };
            Boolean doMore;
            
            int iteration = 1;
            do
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("iteration =" + iteration);
                }
                iteration++;
                doMore = transactionService.getRetryingTransactionHelper().doInTransaction(pagingWork, false, true);
            }
            while (doMore == true);

        }
    }
}

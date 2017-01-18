package org.alfresco.trashcan.cleaner;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.alfresco.model.ContentModel;
import org.alfresco.query.CannedQueryFactory;
import org.alfresco.query.CannedQueryResults;
import org.alfresco.query.PagingRequest;
import org.alfresco.repo.lock.JobLockService;
import org.alfresco.repo.lock.JobLockService.JobLockRefreshCallback;
import org.alfresco.repo.lock.LockAcquisitionException;
import org.alfresco.repo.model.filefolder.GetChildrenCannedQueryFactory;
import org.alfresco.repo.node.getchildren.GetChildrenCannedQuery;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.trashcan2.CleanerComponent;
import org.alfresco.util.Pair;
import org.alfresco.util.registry.NamedObjectRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TrashcanCleaner
{
    private static final long LOCK_TTL = 30000L; // 30 sec
    private static final QName LOCK_QNAME = QName.createQName(NamespaceService.SYSTEM_MODEL_1_0_URI,
            "org.alfresco.repo.TrashcanCleaner");
    private static final String CANNED_QUERY_FILEFOLDER_LIST = "fileFolderGetChildrenCannedQueryFactory";
    private static Log logger = LogFactory.getLog(TrashcanCleaner.class);

    private NodeService nodeService;
    private TransactionService transactionService;
    private DictionaryService dictionaryService;
    private HashSet<QName> setToProtect = new HashSet<QName>();
    private HashSet<NodeRef> nodesToSkip = new HashSet<NodeRef>();

    private int protectedDays = 7;
    private StoreRef storeRef;
    private NamedObjectRegistry<CannedQueryFactory<NodeRef>> cannedQueryRegistry;
    private int pageLen = 3;

    public enum Status
    {
        RUNNING, STOPPED
    }

    private Status status = Status.STOPPED;

    public Status getStatus()
    {
        return status;
    }

    public void setPageLen(int pageLen)
    {
        this.pageLen = pageLen;
    }

    private JobLockService jobLockService;

    
    public void setNodesToSkip(String nodesToSkip)
    {
        if (nodesToSkip == null || nodesToSkip.length() == 0 || nodesToSkip.startsWith("$"))
            return;
        this.nodesToSkip = new HashSet<NodeRef>();
        String[] values = nodesToSkip.split(",");
        for(String v : values)
        {
            this.nodesToSkip.add(new NodeRef(v));
        }
    }
    
    public void setJobLockService(JobLockService jobLockService)
    {
        this.jobLockService = jobLockService;
    }

    public void setSetToProtect(HashSet<String> setToProtectString)
    {
        if (setToProtectString == null)
            return;
        setToProtect.clear();
        // convert to QName
        for (String qStringName : setToProtectString)
        {
            setToProtect.add(QName.createQName(qStringName));
        }
    }

    public void setDictionaryService(DictionaryService dictionaryService)
    {
        this.dictionaryService = dictionaryService;
    }

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
     * Return the set of types that needs to be imune against deletion
     * 
     * @return
     */
    Set<QName> getTypesToProtect()
    {
        return setToProtect;
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

    /**
     * Return true if type is equal or a subtype of the type to protect
     * 
     * @param type
     * @return
     */
    protected boolean mustBeProtected(NodeRef nodeRef)
    {
        //check the reference first then check the type
        
        if( this.nodesToSkip.contains(nodeRef))
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Node skiped because in nodesToSkip: " + nodeRef);
            }
            return true;
        }
        
        QName type = nodeService.getType(nodeRef);
        Set<QName> typesToProtect = getTypesToProtect();

        for (QName typeToPtotect : typesToProtect)
        {
            if (typeToPtotect.equals(type) == true || dictionaryService.isSubClass(type, typeToPtotect) == true)
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("Node " + nodeRef + " skiped because is type or subtype of: " + type);
                }
                return true;
            }
        }
        return false;
    }
    int deleteRecursive(NodeRef nodeRef, int maxNumOfNodesInTransaction, MutableBoolean skip)
    {
        final NodeRef fNodeREf = nodeRef;
        final MutableBoolean fSkip = skip;
        final int fMaxNumOfNodesInTransaction = maxNumOfNodesInTransaction;
        final RetryingTransactionCallback<Integer> recursiveWork = new RetryingTransactionCallback<Integer>()
            {
                public Integer execute() throws Exception
                {
                    
                    if (!nodeService.exists(fNodeREf))
                        return 0; // finished
                    //before trying to prune, we test if the root none has aspect ASPECT_ARCHIVED
                    if(!nodeService.hasAspect(fNodeREf, ContentModel.ASPECT_ARCHIVED))
                    {
                        //issue a warning it does not look normal and skip it
                        logger.warn("Expected ASPECT_ARCHIVED on " + fNodeREf + " to be present. Node skipped!");
                        fSkip.set(true);
                        return 0; // do not delete it
                    }
                    //also testing ContentModel.PROP_ARCHIVED_DATE != null
                    if(nodeService.getProperty(fNodeREf, ContentModel.PROP_ARCHIVED_DATE)== null)
                    {
                        //issue a warning it does not look normal and skip it
                        logger.warn("Expected PROP_ARCHIVED_DATE on " + fNodeREf + " not null. Node skipped!");
                        fSkip.set(true);
                        return 0; // do not delete it
                    }
                    return this.deleteRecursiveInternal(fNodeREf, fMaxNumOfNodesInTransaction);
                }

                public int deleteRecursiveInternal(NodeRef nodeRef, int fMaxNumOfNodesInTransaction)
                {
                    if (!nodeService.exists(nodeRef))
                        return 0; // finished
                    List<ChildAssociationRef> allChildren = nodeService.getChildAssocs(nodeRef);

                    // if no child then delete the node, this is a leaf
                    if (allChildren == null || allChildren.size() == 0)
                    {
                        
                        // maybe we need to preserve it if specific type
                        // or specific nodeRef provided in configuration
                        if (!mustBeProtected(nodeRef))
                        {
                            nodeService.deleteNode(nodeRef);
                            return 1;
                        }
                        else
                        {
                            fSkip.set(true);
                            return 0; // do not delete it
                        }
                        
                        

                    }

                    // recursive an iterate and count
                    int sum = 0;
                    for (ChildAssociationRef assoc : allChildren)
                    {
                        sum += deleteRecursiveInternal(assoc.getChildRef(), fMaxNumOfNodesInTransaction);
                        if (sum >= fMaxNumOfNodesInTransaction)
                            break;
                    }
                    return sum;
                }

            };
        return transactionService.getRetryingTransactionHelper().doInTransaction(recursiveWork, false,
                true);
    }
    public void execute()
    {
        String lockToken = null;
        final AtomicBoolean keepGoing = new AtomicBoolean(true);
        try
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("In trash can execte!!");
            }

            // Lock
            lockToken = jobLockService.getLock(LOCK_QNAME, LOCK_TTL);
            // Refresh to get callbacks
            JobLockRefreshCallback callback = new JobLockRefreshCallback()
                {
                    @Override
                    public void lockReleased()
                    {
                        if (logger.isDebugEnabled())
                        {
                            logger.debug("In trash can lockReleased!!");
                        }
                        keepGoing.set(false);
                        status = Status.STOPPED;
                    }

                    @Override
                    public boolean isActive()
                    {
                        if (logger.isDebugEnabled())
                        {
                            logger.debug("In trash can isActive()!!");
                        }

                        return keepGoing.get();
                    }
                };
            jobLockService.refreshLock(lockToken, LOCK_QNAME, LOCK_TTL, callback);

            status = Status.RUNNING;
            if (logger.isDebugEnabled())
            {
                logger.debug("Before Execute internal !");
            }

            executeLocalInternal();
            if (logger.isDebugEnabled())
            {
                logger.debug("After Execute internal !");
            }
            
            //jobLockService.releaseLock(lockToken, LOCK_QNAME);
        }
        catch (LockAcquisitionException e)
        {
            
            if (logger.isDebugEnabled())
            {
                logger.debug("Lock acquisition failed!", e);
            }

        }
        finally
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Finally before released lock !!");
            }
            keepGoing.set(false); // Notify the refresh callback that we are done
            if (lockToken != null)
            {
                jobLockService.releaseLock(lockToken, LOCK_QNAME);
                status = Status.STOPPED;
                if (logger.isDebugEnabled())
                {
                    logger.debug("After released lock !!");
                }
            }
        }
    }

    private void executeLocalInternal()
    {
        if (logger.isDebugEnabled())
        {
            logger.debug("Execute called!!!!");
            logger.debug("Trashcancleaner protectedDays days=" + protectedDays);
        }
        if (this.protectedDays > 0)
        {
            final MutableInteger fToSkip = new MutableInteger(0);
            
            
            final RetryingTransactionCallback<List<NodeRef>> getPage = new RetryingTransactionCallback<List<NodeRef>>()
            {
                public List<NodeRef> execute() throws Exception
                {

                    NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                    // get canned query
                    //It could be the sort slowing down
                    //set org.alfresco.repo.node.getchildren.GetChildrenCannedQuery=debug to get query exec time
                    GetChildrenCannedQueryFactory getChildrenCannedQueryFactory = (GetChildrenCannedQueryFactory) cannedQueryRegistry
                            .getNamedObject(CANNED_QUERY_FILEFOLDER_LIST);

                    List<Pair<QName, Boolean>> sortProps = new ArrayList<Pair<QName, Boolean>>();
                    sortProps.add(new Pair<QName, Boolean>(ContentModel.PROP_ARCHIVED_DATE, true));

                    Set<QName> assocTypeQNames = new HashSet<QName>(1);
                    assocTypeQNames.add(ContentModel.ASSOC_CHILDREN);

                    Set<QName> childTypeQNames = new HashSet<QName>(2);
                    //Other custom types are purged
                    //childTypeQNames.add(ContentModel.TYPE_CONTENT);
                    //childTypeQNames.add(ContentModel.TYPE_FOLDER);

                    PagingRequest pagingRequest = new PagingRequest(fToSkip.value, pageLen);

                    GetChildrenCannedQuery cq = (GetChildrenCannedQuery) getChildrenCannedQueryFactory
                            .getCannedQuery(archiveRoot, "*", assocTypeQNames, childTypeQNames, null, sortProps,
                                    pagingRequest);
                    long queryStartExecTime = System.currentTimeMillis();
                    // execute canned query
                    CannedQueryResults<NodeRef> results = cq.execute();

                    if (logger.isDebugEnabled())
                    {
                        long queryEndExecTime = System.currentTimeMillis();
                        logger.debug("Query exec time: " + (queryEndExecTime - queryStartExecTime) + "ms");
                        long getPageCountStart = System.currentTimeMillis();
                        logger.debug("Number of pages available:" + results.getPageCount());
                        long getPageCountEnd = System.currentTimeMillis();
                        logger.debug("getPageCount() exec time:" + (getPageCountEnd - getPageCountStart) + "ms");
                    }

                    List<NodeRef> pageElements = results.getPage();

                    return pageElements;
                }
               };

            // final AtomicBoolean keepGoing = new AtomicBoolean(true);
            Boolean doMore = true;

            int iteration = 1;
            do
            {
                if (logger.isDebugEnabled())
                {
                    logger.debug("iteration =" + iteration);
                }
                iteration++;
                List<NodeRef> pageElements = transactionService.getRetryingTransactionHelper().doInTransaction(getPage, true, true);
                if( pageElements.size() == 0 )
                   break;
                Date toDate = new Date(new Date().getTime() - (1000L * 60L * 60L * 24L * protectedDays));
                // display the page
                for (NodeRef nodeRef : pageElements)
                {
                    Date archivedDate = (Date) nodeService
                            .getProperty(nodeRef, ContentModel.PROP_ARCHIVED_DATE);
                    //here we are testing the case if some node are in archive but not having ContentModel.PROP_ARCHIVED_DATE
                    //I also probably mean that the aspect ASPECT_ARCHIVED is not there neither.
                    //Therefore we display a warning when aspect ASPECT_ARCHIVED is not present or PROP_ARCHIVED_DATE
                    //is null. Given that there elements are skipped we do the tests in deleteRecursive(...)
                    if (archivedDate != null && archivedDate.compareTo(toDate) >= 0)
                    {
                        doMore = false;
                        break;
                    }
                    String name = (String) nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);
                    if (logger.isDebugEnabled())
                    {
                        logger.debug("Delete NodeRef :" + nodeRef + " $ Name :" + name + " $ archivedDate :"
                                + archivedDate);
                    }
                    // Maybe there is more than one element to deleted there
                    // It might be a big tree and therefore causing/requiring big or even too big transaction
                    MutableBoolean fSkipBool = new MutableBoolean(false);
                    while (deleteRecursive(nodeRef, 500, fSkipBool) != 0)
                    {
                    }
                    // it true we have a tree that was pruned but containing
                    // a node of type that must be preserved
                    if (fSkipBool.value == true)
                    {
                        fToSkip.increment();
                    }
                }

                //doMore = true;
                
            }
            while (doMore == true);
            

        }
    }

}

class MutableInteger
{
    int value;

    public MutableInteger(int n)
    {
        value = n;
    }

    public void increment()
    {
        ++value;
    }

}

class MutableBoolean
{
    boolean value;

    public MutableBoolean(boolean v)
    {
        value = v;
    }

    public void set(boolean v)
    {
        value = v;
    }

    public boolean get(boolean v)
    {
        return value;
    }

}

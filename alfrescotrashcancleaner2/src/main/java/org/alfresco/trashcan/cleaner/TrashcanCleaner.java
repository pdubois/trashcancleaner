package org.alfresco.trashcan.cleaner;


import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.lock.JobLockService;
import org.alfresco.repo.lock.JobLockService.JobLockRefreshCallback;
import org.alfresco.repo.lock.LockAcquisitionException;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.service.cmr.dictionary.DictionaryService;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.search.ResultSet;
import org.alfresco.service.cmr.search.SearchParameters;
import org.alfresco.service.cmr.search.SearchService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.transaction.TransactionService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class TrashcanCleaner
{

    // private static final String ARCHIVE_SEARCH_STRING = "ASPECT:\"sys:archived\" AND TYPE:\"cm:content\"";
    private static final String ARCHIVE_SEARCH_STRING = "ASPECT:\"sys:archived\" ";
    private static final long LOCK_TTL = 30000L; // 30 sec
    private static final QName LOCK_QNAME = QName.createQName(NamespaceService.SYSTEM_MODEL_1_0_URI,
            "org.alfresco.repo.TrashcanCleaner");
    private static Log logger = LogFactory.getLog(TrashcanCleaner.class);

    private NodeService nodeService;
    private TransactionService transactionService;
    private DictionaryService dictionaryService;
    private HashSet<QName> setToProtect = new HashSet<QName>();

    private HashSet<NodeRef> nodesToSkip = new HashSet<NodeRef>();


    private int protectedDays = 7;
    private int pageLen = 3;
    
    // contains when cleaner started
    private long startTime = 0L;
    // contains how long runner can work maximum.
    // default is 4 hours
    private long cleanerMaxRunningTime = 1000L * 60L * 60L * 4L;
    
    private SearchService searchService;

    public TrashcanCleaner()
    {
        setToProtect.add(QName.createQName("{http://www.alfresco.org/model/site/1.0}site"));
    }

    public void setSearchService(SearchService searchService)
    {
        this.searchService = searchService;
    }

    public void setCleanerMaxRunningTime(long cleanerMaxRunningTime)
    {
        this.cleanerMaxRunningTime = cleanerMaxRunningTime;
    }
    
    public long getCleanerMaxRunningTime(long cleanerMaxRunningTime)
    {
        return this.cleanerMaxRunningTime;
    }

    public enum Status
    {
        RUNNING, STOPPING, STOPPED, DISABLED
    }

    private Status status = Status.STOPPED;

    public Status getStatus()
    {
        return status;
    }
    
    /**
     * Set status to STOPPING if status is RUNNING
     * @return previous status
     */
    public Status stop()
    {
        
        Status previousStatus = getStatus();
        if (status == Status.RUNNING)
        {
            status = Status.STOPPING;
        }
        if (logger.isDebugEnabled())
        {
            logger.debug("stop called, previous status: " + previousStatus);
        }
        return previousStatus;
    }
    
    /**
     * It stops current execution.
     * @return
     */
    public Status Disable()
    {
        Status previousStatus = this.getStatus();
        if (logger.isDebugEnabled())
        {
            logger.debug("stop called, previous status: " + previousStatus);
        }
        status = Status.DISABLED;
        return previousStatus;
    }

    /**
     * It enables current execution.
     * @return
     */
    public Status Enable()
    {
        Status previousStatus = this.getStatus();
        if (logger.isDebugEnabled())
        {
            logger.debug("Enable called, previous status: " + previousStatus);
        }
        status = Status.STOPPED;
        return previousStatus;
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
        {
            
            setToProtect.clear();
            setToProtect.add(QName.createQName("{http://www.alfresco.org/model/site/1.0}site"));
            return;
        }


        // convert to QName
        for (String qStringName : setToProtectString)
        {
            setToProtect.add(QName.createQName(qStringName));
        }
        setToProtect.add(QName.createQName("{http://www.alfresco.org/model/site/1.0}site"));
    }

    public void setDictionaryService(DictionaryService dictionaryService)
    {
        this.dictionaryService = dictionaryService;
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
        final TrashcanCleaner fThis = this;
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
                    if (mustBeProtected(fNodeREf))
                    {
                        logger.warn("Node type protected: " + fNodeREf );
                        fSkip.set(true);
                        return 0;
                    }
                    
                    //also testing ContentModel.PROP_ARCHIVED_DATE != null
                    Date archiveDate = (Date)nodeService.getProperty(fNodeREf, ContentModel.PROP_ARCHIVED_DATE);
                    Date toDate = new Date(new Date().getTime() - (1000L * 60L * 60L * 24L * protectedDays));
                    //System.out.println("archiveDate ===" + archiveDate + " toDate ===="  + toDate);
                    
                    if( archiveDate== null || archiveDate.after(toDate))
                    {
                        //issue a warning it does not look normal and skip it
                        logger.warn("Expected PROP_ARCHIVED_DATE on " + fNodeREf + " not null. Node skipped!");
                        fSkip.set(true);
                        return 0; // do not delete it
                    }
                    
                    //compare the dates
                    //System.out.println("Before initial deleteRecursiveInternal!!!" + " archived date:" + archiveDate );
                    return this.deleteRecursiveInternal(fNodeREf, fMaxNumOfNodesInTransaction);
                }

                public int deleteRecursiveInternal(NodeRef nodeRef, int fMaxNumOfNodesInTransaction)
                {
                    if (!nodeService.exists(nodeRef))
                        return 0; // finished
                    if (fThis.getStatus() == Status.STOPPING || fThis.getStatus() == Status.DISABLED)
                        return 0;
                    List<ChildAssociationRef> allChildren = nodeService.getChildAssocs(nodeRef);

                    // if no child then delete the node, this is a leaf
                    if (allChildren == null || allChildren.size() == 0)
                    {
                        
                        // maybe we need to preserve it if specific type
                        // or specific nodeRef provided in configuration
                        if (!mustBeProtected(nodeRef))
                        {
                            //System.out.println("Node deleted:" + nodeRef + " has aspect archived: " + nodeService.hasAspect(nodeRef, ContentModel.ASPECT_ARCHIVED) +
                            //        " Name:" + nodeService.getProperty(nodeRef, ContentModel.PROP_NAME)
                            // + " date:" + (Date)nodeService.getProperty(nodeRef, ContentModel.PROP_ARCHIVED_DATE));
                            
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
                        //System.out.println("Before deleteRecursiveInternal!!!");
                        NodeRef parent = assoc.getParentRef();
                        //System.out.println("Type parent: " + nodeService.getType(parent));
                        sum += deleteRecursiveInternal(assoc.getChildRef(), fMaxNumOfNodesInTransaction);
                        //System.out.println("After deleteRecursiveInternal Sum = " + sum);
                        if (sum >= fMaxNumOfNodesInTransaction)
                        {
                            break;
                        }
                    }
                    return sum;
                }

            };
            if (logger.isDebugEnabled())
            {
                logger.debug("****** In delete recursive : " + nodeRef);
            };
        //System.out.println("****** In delete recursive : " + nodeRef);
        return transactionService.getRetryingTransactionHelper().doInTransaction(recursiveWork, false,
                true);
    }
    public void execute()
    {
        String lockToken = null;
        TimerTask stopTask = null;
        Timer timer = new Timer();
        final TrashcanCleaner fThis = this;
        final AtomicBoolean keepGoing = new AtomicBoolean(true);
        
        
        if (this.getStatus() == Status.DISABLED ||  this.getStatus() == Status.RUNNING)
        {
            if (logger.isDebugEnabled())
            {
                logger.debug("Trashcan RUNNING or DISABLED: " + this.getStatus());
            }
            return;
        }
        
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
            
            //start a timer to stop after getCleanerMaxRunningTime();
            stopTask = new TimerTask()
            { 

                public void run()
                {
                    Status previousStatus = fThis.stop(); 
                    if (logger.isDebugEnabled())
                    {
                        logger.debug("Stopping Timer task called, previous status" + previousStatus);
                    }
                }
            };
            
          //sched(TimerTask task, long time, long period)
          // scheduleAtFixedRate(TimerTask task, long delay, long period)
          timer.scheduleAtFixedRate(stopTask,this.cleanerMaxRunningTime, this.cleanerMaxRunningTime); 
            
            this.startTime = System.currentTimeMillis();
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
            timer.cancel();
            keepGoing.set(false); // Notify the refresh callback that we are done
            if (lockToken != null)
            {
                jobLockService.releaseLock(lockToken, LOCK_QNAME);
                if (status != status.DISABLED)
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
            final TrashcanCleaner fTrashcanCleaner = this;
            
            final RetryingTransactionCallback<List<NodeRef>> getPage = new RetryingTransactionCallback<List<NodeRef>>()
            {
                public List<NodeRef> execute() throws Exception
                {
                    List<NodeRef> pageElements = fTrashcanCleaner.executeQuery(new StoreRef("archive","SpacesStore"), searchService, ARCHIVE_SEARCH_STRING, fToSkip.value, pageLen);
                    return pageElements;
                }
               };

            // final AtomicBoolean keepGoing = new AtomicBoolean(true);
            Boolean doMore = true;

            int iteration = 1;
            do
            {
                if( this.getStatus() == Status.STOPPING || this.getStatus() == Status.DISABLED)
                    break;
                if (logger.isDebugEnabled())
                {
                    logger.debug("iteration =" + iteration);
                }
                iteration++;
                List<NodeRef> pageElements = transactionService.getRetryingTransactionHelper().doInTransaction(getPage, false, true);
                
                if (logger.isDebugEnabled())
                {
                    logger.debug("*********** WILL CHECK : " + pageElements.size() + " ROOT NODES IN THE BIN!!!");
                };
                
                //System.out.println("Page element size = " + pageElements.size());
                if( pageElements.size() == 0 )
                   break;
                Date toDate = new Date(new Date().getTime() - (1000L * 60L * 60L * 24L * protectedDays));
                // display the page
                for (NodeRef nodeRef : pageElements)
                {
                    if (logger.isDebugEnabled())
                    {
                        logger.debug("*********** Node : " + nodeRef);
                    };
                    //System.out.println(" *********** Node : " + nodeRef);
                    //fToSkip.increment();
                    if( this.getStatus() == Status.STOPPING || this.getStatus() == Status.DISABLED )
                        break;
                    if (!nodeService.exists(nodeRef))
                    {
                        //System.out.println(" ***** Node not exist: " + nodeRef);
                        continue;
                    }
                    Date archivedDate = (Date) nodeService
                            .getProperty(nodeRef, ContentModel.PROP_ARCHIVED_DATE);
                    //here we are testing the case if some node are in archive but not having ContentModel.PROP_ARCHIVED_DATE
                    //I also probably mean that the aspect ASPECT_ARCHIVED is not there neither.
                    //Therefore we display a warning when aspect ASPECT_ARCHIVED is not present or PROP_ARCHIVED_DATE
                    //is null. Given that there elements are skipped we do the tests in deleteRecursive(...)
                    //* @return  the value <code>0</code> if the argument Date is equal to
                    //        *          this Date; a value less than <code>0</code> if this Date
                    //        *          is before the Date argument; and a value greater than
                    //        *      <code>0</code> if this Date is after the Date argument.
                    if (archivedDate == null || archivedDate.after(toDate))
                    {
                        fToSkip.increment();
                        if (logger.isDebugEnabled())
                        {
                            logger.debug("*********** Skip : " + nodeRef);
                        };
                        //System.out.println(" *********** Skip : " + nodeRef);
                        //doMore = false;
                        //break;
                        //System.out.println("Do nothing! " + archivedDate);
                        continue;
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
                    while (this.getStatus() != Status.STOPPING && this.getStatus() != Status.DISABLED && deleteRecursive(nodeRef, 500, fSkipBool) != 0)
                    {
                        //fToSkip.increment();
                        //System.out.println("Skip: " + fToSkip.value);
                    }
                    // it true we have a tree that was pruned but containing
                    // a node of type that must be preserved
                    if (fSkipBool.value == true)
                    {
                        fToSkip.increment();
                    }
                    if (logger.isDebugEnabled())
                    {
                        logger.debug("****************After Skip: " + fToSkip.value);
                    }
                    //System.out.println("****************After Skip: " + fToSkip.value);
                }
                if (logger.isDebugEnabled())
                {
                    logger.debug("**************** Page finished!!! DOING ONLY ONE BIG PAGE FOR THE getChildren() CHANGE ");
                }
                //System.out.println("Page finished!!!");
                doMore = false; //STOP HERE do only 1 page THIS IS FOR THE getChildren() CHANGE.
                
            }
            while (doMore == true);
            

        }
    }
    
    private List<NodeRef> executeQuery(
            StoreRef storeRef,
            SearchService searchService,
            String query,
            int startingElement,
            int pageLen)
    {

          NodeRef rootNode = nodeService.getRootNode(storeRef);
          List<ChildAssociationRef> children = nodeService.getChildAssocs(rootNode);
          
          List<NodeRef> nodeToClean = new ArrayList(children.size());
          
          for(ChildAssociationRef assoc: children)
          {
              nodeToClean.add(assoc.getChildRef());
          }
          return nodeToClean;
//        SearchParameters sp = new SearchParameters();
//        sp.addStore(storeRef);
//        sp.setLanguage(SearchService.LANGUAGE_FTS_ALFRESCO);
//        sp.setSkipCount(startingElement);
//        // -1 unlimited result size
//        sp.setMaxItems(-1);
//        sp.setQuery(query);
//        ResultSet results = searchService.query(sp);
//        List<NodeRef> nodeToClean = new ArrayList<NodeRef>(pageLen);
//        int i;
//        for (i = startingElement; i < startingElement + pageLen; i++)
//        {
//            if (i - startingElement >= results.length())
//                break;
//            NodeRef nodeRef = results.getNodeRef(i - startingElement);
//            nodeToClean.add(nodeRef);
//        }
//        results.close();
//        return nodeToClean;
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

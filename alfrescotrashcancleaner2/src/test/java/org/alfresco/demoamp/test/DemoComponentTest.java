package org.alfresco.demoamp.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.trashcan.cleaner.TrashcanCleaner;
import org.alfresco.util.CronTriggerBean;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.quartz.impl.StdSchedulerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.quartz.JobDetailBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import com.tradeshift.test.remote.Remote;
import com.tradeshift.test.remote.RemoteTestRunner;

/**
 * A simple class demonstrating how to run out-of-container tests loading Alfresco application context. This class uses
 * the RemoteTestRunner to try and connect to localhost:4578 and send the test name and method to be executed on a
 * running Alfresco. One or more hostnames can be configured in the @Remote annotation. If there is no available remote
 * server to run the test, it falls back on local running of JUnits. For proper functioning the test class file must
 * match exactly the one deployed in the webapp (either via JRebel or static deployment) otherwise
 * "incompatible magic value XXXXX" class error loading issues will arise.
 */

@RunWith(RemoteTestRunner.class)
@Remote(runnerClass = SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:alfresco/application-context.xml")
public class DemoComponentTest
{

    private static final int NODE_CREATION_BATCH_SIZE = 200;
    private static final int NUM_OF_DELETED = 10;
    private static final int NUM_REMAININGS = NODE_CREATION_BATCH_SIZE - NUM_OF_DELETED + 1;

    static Logger log = Logger.getLogger(DemoComponentTest.class);

    @Autowired
    @Qualifier("NodeService")
    protected NodeService nodeService;

    @Autowired
    @Qualifier("policyBehaviourFilter")
    protected BehaviourFilter policyBehaviourFilter;

    @Autowired
    @Qualifier("ServiceRegistry")
    ServiceRegistry serviceRegistry;

    @Autowired
    @Qualifier("trashcanCleaner")
    TrashcanCleaner trashcanCleaner;

    @Autowired
    @Qualifier("FileFolderService")
    FileFolderService fileFolderService;

    @Autowired
    @Qualifier("SiteService")
    SiteService siteService;

    @Autowired
    @Qualifier("trashcanCleanerTrigger")
    CronTriggerBean trashcanCleanerTrigger;

    @Autowired
    @Qualifier("trashcanCleanerJobDetail")
    JobDetailBean trashcanCleanerJobDetail;
    


    @Test
    public void testPurgeBinWithSites()
    {
        assertNotNull(serviceRegistry);
        HashSet<String> typeToProtect = new HashSet<String>();
        typeToProtect.add("{http://www.alfresco.org/model/site/1.0}site");
        trashcanCleaner.setSetToProtect(typeToProtect);
        // empty the bin
        InsureBinEmpty();
        populateBinWithSites();
        // PopulateBin(null);
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    // try to count number of elements in the bin
                    StoreRef storeRef = new StoreRef("archive://SpacesStore");
                    NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                    List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);
                    // test if purge deleted all the elements from the bin
                    trashcanCleaner.execute();
                    List<ChildAssociationRef> childAssocAfter = nodeService.getChildAssocs(archiveRoot);
                    // none should be delete because they are all sites
                    assertEquals(childAssocs.size(), childAssocAfter.size());
                    return null;
                }
            }, AuthenticationUtil.getSystemUserName());

    }

    
    @Test
    public void testPurgeBinWithNoArchivedAspectProps()
    {
        assertNotNull(serviceRegistry);
        HashSet<String> typeToProtect = new HashSet<String>();
        trashcanCleaner.setSetToProtect(typeToProtect);
        // empty the bin
        InsureBinEmpty();
        PopulateBin(null);
        final TransactionService fTransactionService = serviceRegistry.getTransactionService();
//        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
//                {
//                    public Object doWork() throws Exception
//                    {
//                        offsetNodesInBin(fTransactionService);
//                        return null;
//                    }
//                }, AuthenticationUtil.getSystemUserName());

        RemoveAspectArchivedInBin(fTransactionService, 10); //Those 10 should be preserved from deletion
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    // try to count number of elements in the bin
                    StoreRef storeRef = new StoreRef("archive://SpacesStore");
                    NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                    List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);
                    // test if purge deleted all the elements from the bin
                    trashcanCleaner.execute();
                    List<ChildAssociationRef> childAssocAfter = nodeService.getChildAssocs(archiveRoot);
                    System.out.println("-----------------BEFORE:" + childAssocs.size());
                    System.out.println("-----------------AFTER:" + childAssocs.size());
                    // none should be delete because they are all sites
                    assertEquals(childAssocs.size(), childAssocAfter.size());
                    return null;
                }
            }, AuthenticationUtil.getSystemUserName());

    }

    
    
    
    @Test
    public void testPurgeBinBigTreeDirect()
    {
        testPurgeBinBigTree(true);
    }

    /**
     * Do not call trigger directly but with a trigger
     */
    @Test
    public void testPurgeBinBigTreeTrigger()
    {
        testPurgeBinBigTree(false);
    }

    /**
     * Test that it can cope with file folder structure. It chunks the final delete from bin in smaller pieces
     */
    public void testPurgeBinBigTree(boolean directExecute)
    {
        assertNotNull(serviceRegistry);
        HashSet<String> typeToProtect = new HashSet<String>();
        trashcanCleaner.setSetToProtect(typeToProtect);
        // empty the bin
        InsureBinEmpty();
        NodeRef secParent = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<NodeRef>()
            {
                public NodeRef doWork() throws Exception
                {
                    StoreRef storeRef = new StoreRef("workspace://SpacesStore");
                    NodeRef workspaceRoot = nodeService.getRootNode(storeRef);
                    List<ChildAssociationRef> list = nodeService.getChildAssocs(workspaceRoot,
                            RegexQNamePattern.MATCH_ALL,
                            QName.createQName(NamespaceService.APP_MODEL_1_0_URI, "company_home"));

                    NodeRef companyHome = list.get(0).getChildRef();
                    NodeRef secParent = nodeService.getChildByName(companyHome, ContentModel.ASSOC_CONTAINS,
                            "SecondaryParent");
                    if (secParent == null)
                    {
                        secParent = fileFolderService.create(companyHome, "SecondaryParent", ContentModel.TYPE_FOLDER)
                                .getNodeRef();
                    }
                    return secParent;
                }
            }, AuthenticationUtil.getSystemUserName());
        PopulateBinWithBigTree(secParent);
        System.out.println("After PopulateBinWithBigTree!");
        if (directExecute)
        {
            AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
                {
                    public Object doWork() throws Exception
                    {
                        // try to count number of elements in the bin
                        StoreRef storeRef = new StoreRef("archive://SpacesStore");
                        NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                        List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);

                        // test if purge deleted all the elements from the
                        // bin
                        trashcanCleaner.execute();
                        childAssocs = nodeService.getChildAssocs(archiveRoot);

                        assertEquals(childAssocs.size(), NUM_REMAININGS);
                        return null;
                    }
                }, AuthenticationUtil.getSystemUserName());
        }
        else
        {
            try
            {
                // configure the scheduler time
                SimpleTrigger trigger = new SimpleTrigger();
                trigger.setName("TESTTRIGGER");
                trigger.setStartTime(new Date(System.currentTimeMillis() + 1000L));
                trigger.setRepeatInterval(1);
                // only execute once
                trigger.setRepeatCount(1);

                // schedule it
                Scheduler scheduler = new StdSchedulerFactory().getScheduler();
                scheduler.start();
                scheduler.scheduleJob(trashcanCleanerJobDetail, trigger);
                System.out.println("Before starting !!!");
                // wait
                try
                {
                    Thread.sleep(4000L);
                }
                catch (InterruptedException e1)
                {
                }

                // Should be running here
                // trashcanCleaner.getStatus();

                do
                {
                    System.out
                            .println("*********************************************************************Before SLEEP");
                    try
                    {
                        Thread.sleep(4000L);
                    }
                    catch (InterruptedException e1)
                    {
                    }
                }
                while (trashcanCleaner.getStatus() == TrashcanCleaner.Status.RUNNING);
                System.out.println("*********************************************************************Finished");
                AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
                    {
                        public Object doWork() throws Exception
                        {
                            // try to count number of elements in the bin
                            StoreRef storeRef = new StoreRef("archive://SpacesStore");
                            NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                            List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);

                            childAssocs = nodeService.getChildAssocs(archiveRoot);

                            assertEquals(childAssocs.size(), NUM_REMAININGS);
                            return null;
                        }
                    }, AuthenticationUtil.getSystemUserName());

            }
            catch (Exception e)
            {
                // TODO Auto-generated catch block
                // fail(e.getMessage());
                e.printStackTrace();
            }
        }
        System.out.println("*********************************************************************VERIFIED");

    }

    /**
     * Test that it can cope with file folder structure. It chunks the final delete from bin in smaller pieces
     */
    @Test
    public void testPurgeBinBigConcurTree()
    {
        assertNotNull(serviceRegistry);
        HashSet<String> typeToProtect = new HashSet<String>();
        trashcanCleaner.setSetToProtect(typeToProtect);
        // empty the bin
        InsureBinEmpty();
        NodeRef secParent = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<NodeRef>()
            {
                public NodeRef doWork() throws Exception
                {
                    StoreRef storeRef = new StoreRef("workspace://SpacesStore");
                    NodeRef workspaceRoot = nodeService.getRootNode(storeRef);
                    List<ChildAssociationRef> list = nodeService.getChildAssocs(workspaceRoot,
                            RegexQNamePattern.MATCH_ALL,
                            QName.createQName(NamespaceService.APP_MODEL_1_0_URI, "company_home"));

                    NodeRef companyHome = list.get(0).getChildRef();
                    NodeRef secParent = nodeService.getChildByName(companyHome, ContentModel.ASSOC_CONTAINS,
                            "SecondaryParent");
                    if (secParent == null)
                    {
                        secParent = fileFolderService.create(companyHome, "SecondaryParent", ContentModel.TYPE_FOLDER)
                                .getNodeRef();
                    }
                    return secParent;
                }
            }, AuthenticationUtil.getSystemUserName());
        PopulateBinWithBigTree(secParent);
        System.out.println("After PopulateBinWithBigTree!");

        try
        {
            // configure the scheduler time
            SimpleTrigger trigger = new SimpleTrigger();
            trigger.setName("TESTTRIGGER2");
            trigger.setStartTime(new Date(System.currentTimeMillis() + 1000L));
            trigger.setRepeatInterval(1);
            // only execute once
            trigger.setRepeatCount(1);

            // schedule it
            Scheduler scheduler = new StdSchedulerFactory().getScheduler();
            scheduler.start();
            scheduler.scheduleJob(trashcanCleanerJobDetail, trigger);
            
            try
            {
                Thread.sleep(2000L);
            }
            catch (InterruptedException e1)
            {
            }
            
            System.out.println("Before starting !!!");
            
            AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
                    {
                        public Object doWork() throws Exception
                        {
                            // try to count number of elements in the bin
                            StoreRef storeRef = new StoreRef("archive://SpacesStore");
                            NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                            List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);

                            // test if purge deleted all the elements from the
                            // bin
                            //Returns immediately
                            long before = System.currentTimeMillis();
                            trashcanCleaner.execute();
                            long after = System.currentTimeMillis();                            
                            assert(after - before < 500);
                            return null;
                        }
                    }, AuthenticationUtil.getSystemUserName());
            
            // Should be running here
            // trashcanCleaner.getStatus();

            do
            {
                System.out.println("*********************************************************************Before SLEEP");
                try
                {
                    Thread.sleep(4000L);
                }
                catch (InterruptedException e1)
                {
                }
            }
            while (trashcanCleaner.getStatus() == TrashcanCleaner.Status.RUNNING);
            System.out.println("*********************************************************************Finished");
            AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
                {
                    public Object doWork() throws Exception
                    {
                        // try to count number of elements in the bin
                        StoreRef storeRef = new StoreRef("archive://SpacesStore");
                        NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                        List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);

                        childAssocs = nodeService.getChildAssocs(archiveRoot);

                        assertEquals(childAssocs.size(), NUM_REMAININGS);
                        return null;
                    }
                }, AuthenticationUtil.getSystemUserName());

        }
        catch (Exception e)
        {
            // TODO Auto-generated catch block
            // fail(e.getMessage());
            e.printStackTrace();
        }
        System.out.println("*********************************************************************VERIFIED");
    }

    @Test
    public void testPurgeBinWithSecondaryParents()
    {
        assertNotNull(serviceRegistry);
        HashSet<String> typeToProtect = new HashSet<String>();
        trashcanCleaner.setSetToProtect(typeToProtect);
        // empty the bin
        InsureBinEmpty();
        NodeRef secParent = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<NodeRef>()
            {
                public NodeRef doWork() throws Exception
                {
                    StoreRef storeRef = new StoreRef("workspace://SpacesStore");
                    NodeRef workspaceRoot = nodeService.getRootNode(storeRef);
                    List<ChildAssociationRef> list = nodeService.getChildAssocs(workspaceRoot,
                            RegexQNamePattern.MATCH_ALL,
                            QName.createQName(NamespaceService.APP_MODEL_1_0_URI, "company_home"));

                    NodeRef companyHome = list.get(0).getChildRef();
                    NodeRef secParent = nodeService.getChildByName(companyHome, ContentModel.ASSOC_CONTAINS,
                            "SecondaryParent");
                    if (secParent == null)
                    {
                        secParent = fileFolderService.create(companyHome, "SecondaryParent", ContentModel.TYPE_FOLDER)
                                .getNodeRef();
                    }
                    return secParent;
                }
            }, AuthenticationUtil.getSystemUserName());
        PopulateBin(secParent);
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    // try to count number of elements in the bin
                    StoreRef storeRef = new StoreRef("archive://SpacesStore");
                    NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                    List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);

                    // test if purge deleted all the elements from the bing
                    trashcanCleaner.execute();
                    childAssocs = nodeService.getChildAssocs(archiveRoot);

                    assertEquals(childAssocs.size(), NUM_REMAININGS);
                    return null;
                }
            }, AuthenticationUtil.getSystemUserName());

        final NodeRef fSecParent = secParent;
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    // insure secParent is empty
                    int count = nodeService.countChildAssocs(fSecParent, false);
                    assertTrue(count == 0);
                    return null;
                }
            }, AuthenticationUtil.getSystemUserName());

    }

    @Test
    public void testPurgeBigPageSize()
    {
        assertNotNull(serviceRegistry);
        HashSet<String> typeToProtect = new HashSet<String>();
        trashcanCleaner.setSetToProtect(typeToProtect);
        List<Integer> pageLens = new ArrayList<Integer>(5);
        pageLens.add(1);
        pageLens.add(5);
        pageLens.add(7);
        pageLens.add(10);
        pageLens.add(13);
        for (int pl : pageLens)
        {
            // empty the bin
            InsureBinEmpty();
            assertTrue(true);
            trashcanCleaner.setPageLen(pl);
            PopulateBin(null);
            AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
                {
                    public Object doWork() throws Exception
                    {
                        // try to count number of elements in the bin
                        StoreRef storeRef = new StoreRef("archive://SpacesStore");
                        NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                        List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);

                        // test if purge deleted all the elements from the
                        // bin
                        trashcanCleaner.execute();
                        childAssocs = nodeService.getChildAssocs(archiveRoot);

                        assertEquals(childAssocs.size(), NUM_REMAININGS);
                        return null;
                    }
                }, AuthenticationUtil.getSystemUserName());
        }

    }

    protected void PopulateBinWithBigTree(NodeRef secondParent)
    {
        final NodeRef fSecondParent = secondParent;
        // use TransactionWork to wrap service calls in a user transaction
        TransactionService transactionService = serviceRegistry.getTransactionService();
        final FileFolderService fileFolderService = serviceRegistry.getFileFolderService();
        final RetryingTransactionCallback<List<NodeRef>> populateBinWork = new RetryingTransactionCallback<List<NodeRef>>()
            {
                public List<NodeRef> execute() throws Exception
                {
                    StoreRef storeRef = new StoreRef("workspace://SpacesStore");
                    NodeRef workspaceRoot = nodeService.getRootNode(storeRef);
                    List<ChildAssociationRef> list = nodeService.getChildAssocs(workspaceRoot,
                            RegexQNamePattern.MATCH_ALL,
                            QName.createQName(NamespaceService.APP_MODEL_1_0_URI, "company_home"));

                    NodeRef companyHome = list.get(0).getChildRef();

                    List<NodeRef> batchOfNodes = new ArrayList<NodeRef>(NODE_CREATION_BATCH_SIZE);
                    for (int i = 0; i < NODE_CREATION_BATCH_SIZE; i++)
                    {
                        if (i % 10 != 0)
                        {
                            // assign name
                            String name = "Foundation API sample (" + System.currentTimeMillis() + ")";
                            Map<QName, Serializable> contentProps = new HashMap<QName, Serializable>();
                            contentProps.put(ContentModel.PROP_NAME, name);

                            // create content node
                            NodeService nodeService = serviceRegistry.getNodeService();
                            ChildAssociationRef association = nodeService.createNode(companyHome,
                                    ContentModel.ASSOC_CONTAINS,
                                    QName.createQName(NamespaceService.CONTENT_MODEL_PREFIX, name),
                                    ContentModel.TYPE_CONTENT, contentProps);
                            NodeRef content = association.getChildRef();

                            // add titled aspect (for Web Client display)
                            Map<QName, Serializable> titledProps = new HashMap<QName, Serializable>();
                            titledProps.put(ContentModel.PROP_TITLE, name);
                            titledProps.put(ContentModel.PROP_DESCRIPTION, name);
                            nodeService.addAspect(content, ContentModel.ASPECT_TITLED, titledProps);

                            //
                            // write some content to new node
                            //
                            ContentService contentService = serviceRegistry.getContentService();
                            serviceRegistry.getContentService();
                            ContentWriter writer = contentService.getWriter(content, ContentModel.PROP_CONTENT, true);
                            writer.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);
                            writer.setEncoding("UTF-8");
                            String text = "The quick brown fox jumps over the lazy dog";
                            writer.putContent(text);
                            if (fSecondParent != null)
                            {
                                nodeService.addChild(fSecondParent, content, ContentModel.ASSOC_CONTAINS,
                                        QName.createQName(NamespaceService.CONTENT_MODEL_PREFIX, name));
                            }
                            batchOfNodes.add(content);
                        }
                        else
                        {
                            // create a tree of more than 500 file folders
                            String name = "Foundation API sample (" + System.currentTimeMillis() + ")";
                            FileInfo theRoot = fileFolderService.create(companyHome, name, ContentModel.TYPE_FOLDER);

                            batchOfNodes.add(theRoot.getNodeRef());
                            for (int j = 1; j < 700; j++)
                            {
                                name = "Foundation API sample- (" + j + ")";
                                if (j % 10 == 0)
                                {
                                    name = "Folder- (" + j + ")";
                                    FileInfo theNewRoot = fileFolderService.create(theRoot.getNodeRef(), name,
                                            ContentModel.TYPE_FOLDER);
                                    theRoot = theNewRoot;
                                }
                                else
                                {
                                    // create some content
                                    fileFolderService.create(theRoot.getNodeRef(), name, ContentModel.TYPE_CONTENT);
                                }
                            }
                        }
                    }
                    return batchOfNodes;
                }
            };
        final TransactionService fTransactionService = transactionService;
        final List<NodeRef> batchOfnodes = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<List<NodeRef>>()
            {
                public List<NodeRef> doWork() throws Exception
                {
                    return (List<NodeRef>) fTransactionService.getRetryingTransactionHelper().doInTransaction(
                            populateBinWork);

                }
            }, AuthenticationUtil.getSystemUserName());

        // delete the batch
        final RetryingTransactionCallback<Object> deleteWork = new RetryingTransactionCallback<Object>()
            {
                public List<NodeRef> execute() throws Exception
                {
                    for (NodeRef node : batchOfnodes)
                    {
                        nodeService.deleteNode(node);
                    }

                    return null;
                }
            };
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    return (Object) fTransactionService.getRetryingTransactionHelper().doInTransaction(deleteWork);

                }
            }, AuthenticationUtil.getSystemUserName());

        // modify the {http://www.alfresco.org/model/system/1.0}archivedDate or
        // sys:archivedDate

        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    offsetNodesInBin(fTransactionService);
                    return null;
                }
            }, AuthenticationUtil.getSystemUserName());

    }

    protected void offsetNodesInBin(TransactionService transactionService)
    {
        final TransactionService fTransactionService = transactionService;
        final RetryingTransactionCallback<List<NodeRef>> getArchivedNodeDateOffseted = new RetryingTransactionCallback<List<NodeRef>>()
            {
                public List<NodeRef> execute() throws Exception
                {
                    StoreRef storeRef = new StoreRef("archive://SpacesStore");
                    NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                    List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);
                    int i = 0;
                    for (ChildAssociationRef childAssoc : childAssocs)
                    {
                        if (i > NUM_OF_DELETED)
                            break;
                        i++;
                        Calendar cal = Calendar.getInstance();
                        cal.set(Calendar.YEAR, 1974);
                        cal.set(Calendar.MONTH, 4);
                        cal.set(Calendar.DAY_OF_MONTH, 28);
                        cal.set(Calendar.HOUR_OF_DAY, 17);
                        cal.set(Calendar.MINUTE, 30);
                        cal.set(Calendar.SECOND, 0);
                        cal.set(Calendar.MILLISECOND, 0);

                        Date d = cal.getTime();
                        policyBehaviourFilter.disableBehaviour(ContentModel.ASPECT_ARCHIVED);
                        try
                        {
                            nodeService.setProperty(childAssoc.getChildRef(), ContentModel.PROP_ARCHIVED_DATE, d);
                        }
                        finally
                        {
                            policyBehaviourFilter.enableBehaviour(ContentModel.ASPECT_ARCHIVED);
                        }

                    }

                    return null;
                }
            };
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    return (Object) fTransactionService.getRetryingTransactionHelper().doInTransaction(
                            getArchivedNodeDateOffseted);
                }
            }, AuthenticationUtil.getSystemUserName());
    }

    /**
     * Remove the aspect ASPECT_ARCHIVED
     * @param transactionService
     * @param numOfNodesToRemoveAspect
     */
    protected void RemoveAspectArchivedInBin(TransactionService transactionService, int numOfNodesToRemoveAspect)
    {
        final TransactionService fTransactionService = transactionService;
        final int fNNumOfNodesToRemoveAspect = numOfNodesToRemoveAspect;
        final RetryingTransactionCallback<List<NodeRef>> getArchivedNodeDateOffseted = new RetryingTransactionCallback<List<NodeRef>>()
            {
                public List<NodeRef> execute() throws Exception
                {
                    StoreRef storeRef = new StoreRef("archive://SpacesStore");
                    NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                    List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);
                    int i = 0;
                    for (ChildAssociationRef childAssoc : childAssocs)
                    {
                        if (i > fNNumOfNodesToRemoveAspect)
                            break;
                        i++;
                        
                        nodeService.removeAspect(childAssoc.getChildRef(), ContentModel.ASPECT_ARCHIVED);

                    }

                    return null;
                }
            };
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    return (Object) fTransactionService.getRetryingTransactionHelper().doInTransaction(
                            getArchivedNodeDateOffseted);
                }
            }, AuthenticationUtil.getSystemUserName());
    }

    
    
    protected void InsureBinEmpty()
    {
        // use TransactionWork to wrap service calls in a user transaction
        TransactionService transactionService = serviceRegistry.getTransactionService();
        final RetryingTransactionCallback<Object> emptyBinWork = new RetryingTransactionCallback<Object>()
            {
                public Object execute() throws Exception
                {
                    StoreRef storeRef = new StoreRef("archive://SpacesStore");
                    NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                    List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);
                    // delete nodes
                    for (ChildAssociationRef childAssoc : childAssocs)
                    {
                        nodeService.deleteNode(childAssoc.getChildRef());
                    }
                    return null;
                }
            };
        final TransactionService fTransactionService = transactionService;
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    fTransactionService.getRetryingTransactionHelper().doInTransaction(emptyBinWork);
                    return null;
                }
            }, AuthenticationUtil.getSystemUserName());
    }

    protected void populateBinWithSites()
    {
        // use TransactionWork to wrap service calls in a user transaction
        TransactionService transactionService = serviceRegistry.getTransactionService();
        final RetryingTransactionCallback<List<NodeRef>> populateBinWork = new RetryingTransactionCallback<List<NodeRef>>()
            {
                public List<NodeRef> execute() throws Exception
                {
                    StoreRef storeRef = new StoreRef("workspace://SpacesStore");
                    NodeRef workspaceRoot = nodeService.getRootNode(storeRef);
                    List<ChildAssociationRef> list = nodeService.getChildAssocs(workspaceRoot,
                            RegexQNamePattern.MATCH_ALL,
                            QName.createQName(NamespaceService.APP_MODEL_1_0_URI, "company_home"));

                    NodeRef companyHome = list.get(0).getChildRef();

                    List<NodeRef> batchOfNodes = new ArrayList<NodeRef>(NODE_CREATION_BATCH_SIZE);
                    for (int i = 0; i < 2; i++)
                    {

                        // assign name
                        String name = "Site-" + System.currentTimeMillis();

                        SiteInfo siteInfo = siteService
                                .createSite("site-dashboard", name, "Titre" + System.currentTimeMillis(),
                                        "Description-" + System.currentTimeMillis(), true);
                        batchOfNodes.add(siteInfo.getNodeRef());

                    }
                    return batchOfNodes;
                }
            };
        final TransactionService fTransactionService = transactionService;
        final List<NodeRef> batchOfnodes = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<List<NodeRef>>()
            {
                public List<NodeRef> doWork() throws Exception
                {
                    return (List<NodeRef>) fTransactionService.getRetryingTransactionHelper().doInTransaction(
                            populateBinWork);

                }
            }, "admin");

        // delete the batch
        final RetryingTransactionCallback<Object> deleteWork = new RetryingTransactionCallback<Object>()
            {
                public List<NodeRef> execute() throws Exception
                {
                    for (NodeRef node : batchOfnodes)
                    {
                        if (nodeService.hasAspect(node, ContentModel.ASPECT_UNDELETABLE))
                            nodeService.removeAspect(node, ContentModel.ASPECT_UNDELETABLE);
                        nodeService.deleteNode(node);
                    }

                    return null;
                }
            };
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    return (Object) fTransactionService.getRetryingTransactionHelper().doInTransaction(deleteWork);

                }
            }, AuthenticationUtil.getSystemUserName());

        // modify the {http://www.alfresco.org/model/system/1.0}archivedDate or
        // sys:archivedDate

        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    // return (Object)
                    // fTransactionService.getRetryingTransactionHelper().doInTransaction(
                    // getArchivedNodeDateOffseted);
                    offsetNodesInBin(fTransactionService);
                    return null;
                }
            }, AuthenticationUtil.getSystemUserName());

    }

    protected void PopulateBin(NodeRef secondParent)
    {
        final NodeRef fSecondParent = secondParent;
        // use TransactionWork to wrap service calls in a user transaction
        TransactionService transactionService = serviceRegistry.getTransactionService();
        final RetryingTransactionCallback<List<NodeRef>> populateBinWork = new RetryingTransactionCallback<List<NodeRef>>()
            {
                public List<NodeRef> execute() throws Exception
                {
                    StoreRef storeRef = new StoreRef("workspace://SpacesStore");
                    NodeRef workspaceRoot = nodeService.getRootNode(storeRef);
                    List<ChildAssociationRef> list = nodeService.getChildAssocs(workspaceRoot,
                            RegexQNamePattern.MATCH_ALL,
                            QName.createQName(NamespaceService.APP_MODEL_1_0_URI, "company_home"));

                    NodeRef companyHome = list.get(0).getChildRef();

                    List<NodeRef> batchOfNodes = new ArrayList<NodeRef>(NODE_CREATION_BATCH_SIZE);
                    for (int i = 0; i < NODE_CREATION_BATCH_SIZE; i++)
                    {
                        // assign name
                        String name = "Foundation API sample (" + System.currentTimeMillis() + ")";
                        Map<QName, Serializable> contentProps = new HashMap<QName, Serializable>();
                        contentProps.put(ContentModel.PROP_NAME, name);

                        // create content node
                        NodeService nodeService = serviceRegistry.getNodeService();
                        ChildAssociationRef association = nodeService.createNode(companyHome,
                                ContentModel.ASSOC_CONTAINS,
                                QName.createQName(NamespaceService.CONTENT_MODEL_PREFIX, name),
                                ContentModel.TYPE_CONTENT, contentProps);
                        NodeRef content = association.getChildRef();

                        // add titled aspect (for Web Client display)
                        Map<QName, Serializable> titledProps = new HashMap<QName, Serializable>();
                        titledProps.put(ContentModel.PROP_TITLE, name);
                        titledProps.put(ContentModel.PROP_DESCRIPTION, name);
                        nodeService.addAspect(content, ContentModel.ASPECT_TITLED, titledProps);

                        //
                        // write some content to new node
                        //
                        ContentService contentService = serviceRegistry.getContentService();
                        serviceRegistry.getContentService();
                        ContentWriter writer = contentService.getWriter(content, ContentModel.PROP_CONTENT, true);
                        writer.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);
                        writer.setEncoding("UTF-8");
                        String text = "The quick brown fox jumps over the lazy dog";
                        writer.putContent(text);
                        if (fSecondParent != null)
                        {
                            nodeService.addChild(fSecondParent, content, ContentModel.ASSOC_CONTAINS,
                                    QName.createQName(NamespaceService.CONTENT_MODEL_PREFIX, name));
                        }
                        batchOfNodes.add(content);
                    }
                    return batchOfNodes;
                }
            };
        final TransactionService fTransactionService = transactionService;
        final List<NodeRef> batchOfnodes = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<List<NodeRef>>()
            {
                public List<NodeRef> doWork() throws Exception
                {
                    return (List<NodeRef>) fTransactionService.getRetryingTransactionHelper().doInTransaction(
                            populateBinWork);

                }
            }, AuthenticationUtil.getSystemUserName());

        // delete the batch
        final RetryingTransactionCallback<Object> deleteWork = new RetryingTransactionCallback<Object>()
            {
                public List<NodeRef> execute() throws Exception
                {
                    for (NodeRef node : batchOfnodes)
                    {
                        nodeService.deleteNode(node);
                    }

                    return null;
                }
            };
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    return (Object) fTransactionService.getRetryingTransactionHelper().doInTransaction(deleteWork);

                }
            }, AuthenticationUtil.getSystemUserName());

        // modify the {http://www.alfresco.org/model/system/1.0}archivedDate or
        // sys:archivedDate

        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    // return (Object)
                    // fTransactionService.getRetryingTransactionHelper().doInTransaction(
                    // getArchivedNodeDateOffseted);
                    offsetNodesInBin(fTransactionService);
                    return null;
                }
            }, AuthenticationUtil.getSystemUserName());

    }
}

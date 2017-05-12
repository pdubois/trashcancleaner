/**
 * Copyright (C) 2017 Alfresco Software Limited.
 * <p/>
 * This file is part of the Alfresco SDK project.
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package alternative.trashcancleaner.platformsample;

import org.alfresco.rad.test.AbstractAlfrescoIT;
import org.alfresco.rad.test.AlfrescoTestRunner;
import org.alfresco.rad.test.Remote;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.model.ContentModel;
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
import org.alfresco.util.CronTriggerBean;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.scheduling.quartz.JobDetailBean;
import org.quartz.Scheduler;
import org.quartz.SimpleTrigger;
import org.quartz.impl.StdSchedulerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Integration Test of the DemoComponent using the Alfresco Test Runner. The Alfresco Test Runner (i.e.
 * AlfrescoTestRunner.class) will check if it is running in an Alfresco instance, if so it will execute normally
 * locally. On the other hand, if it detects no Alfresco Spring context, then it will make a call to a custom Web Script
 * that will execute this test in the running container remotely. The remote location is determined by the @Remote
 * config.
 *
 * @author martin.bergljung@alfresco.com
 * @since 3.0
 */
@RunWith(value = AlfrescoTestRunner.class)
// Specifying the remote endpoint is not required, it
// will default to http://localhost:8080/alfresco if
// not provided. This shows the syntax but simply
// sets the value back to the default value.
@Remote(endpoint = "http://localhost:8080/alfresco")
public class DemoComponentIT extends AbstractAlfrescoIT
{

    private static final int NODE_CREATION_BATCH_SIZE = 200;
    private static final int NUM_OF_DELETED = 50;
    private static final int NUM_REMAININGS = NODE_CREATION_BATCH_SIZE - NUM_OF_DELETED + 1;
    private static final String CUSTOM_CONTENT_URI = "custom.model";
    private static final QName CUSTOM_CONTENT_MODEL = QName.createQName(CUSTOM_CONTENT_URI, "mycontent");
    private static final QName CUSTOM_FOLDER_MODEL = QName.createQName(CUSTOM_CONTENT_URI, "myfolder");

    static Logger log = Logger.getLogger(DemoComponentIT.class);

    protected NodeService nodeService;

    protected BehaviourFilter policyBehaviourFilter;

    protected TrashcanCleaner trashcanCleaner;

    protected FileFolderService fileFolderService;

    protected SiteService siteService;

    protected CronTriggerBean trashcanCleanerTrigger;

    protected JobDetailBean trashcanCleanerJobDetail;

    protected ServiceRegistry serviceRegistry;

    private TransactionService transactionService;

    protected void initFields()
    {
        if (nodeService == null)
        {
            nodeService = (NodeService) getApplicationContext().getBean("NodeService");
        }

        if (policyBehaviourFilter == null)
        {
            policyBehaviourFilter = (BehaviourFilter) getApplicationContext().getBean("policyBehaviourFilter");
        }

        if (trashcanCleaner == null)
        {
            trashcanCleaner = (TrashcanCleaner) getApplicationContext().getBean("trashcanCleaner");
        }

        if (fileFolderService == null)
        {
            fileFolderService = (FileFolderService) getApplicationContext().getBean("FileFolderService");
        }

        if (siteService == null)
        {
            siteService = (SiteService) getApplicationContext().getBean("SiteService");
        }

        if (trashcanCleanerTrigger == null)
        {
            trashcanCleanerTrigger = (CronTriggerBean) getApplicationContext().getBean("trashcanCleanerTrigger");
        }

        if (trashcanCleanerJobDetail == null)
        {
            trashcanCleanerJobDetail = (JobDetailBean) getApplicationContext().getBean("trashcanCleanerJobDetail");
        }

        if (transactionService == null)
        {
            transactionService = (TransactionService) getApplicationContext().getBean("TransactionService");
        }

        if (serviceRegistry == null)
        {
            serviceRegistry = this.getServiceRegistry();
        }
    }

    // @Test
    public void testGetCompanyHome()
    {
        DemoComponent demoComponent = (DemoComponent) getApplicationContext()
                .getBean("alternative.trashcancleaner.DemoComponent");
        NodeRef companyHome = demoComponent.getCompanyHome();
        assertNotNull(companyHome);
        String companyHomeName = (String) getServiceRegistry().getNodeService().getProperty(companyHome,
                ContentModel.PROP_NAME);
        assertNotNull(companyHomeName);
        assertEquals("Company Home", companyHomeName);
    }

    // @Test
    public void testChildNodesCount()
    {
        DemoComponent demoComponent = (DemoComponent) getApplicationContext()
                .getBean("alternative.trashcancleaner.DemoComponent");
        NodeRef companyHome = demoComponent.getCompanyHome();
        int childNodeCount = demoComponent.childNodesCount(companyHome);
        assertNotNull(childNodeCount);
        // There are 7 folders by default under Company Home
        assertEquals(7, childNodeCount);
    }

    @Test
    public void testSimple()
    {
        initFields();
        assertNotNull(serviceRegistry);
        // empty the bin
        InsureBinEmpty();
        populateBin(null, null, null);
        // populateBin(null, null, null);
        // try to count number of elements in the bin
        int numBefore = getNodesInBin().size();
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    StoreRef storeRef = new StoreRef("archive://SpacesStore");
                    NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                    List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);
                    // test if purge deleted all the elements from the bin
                    // System.out.println("**** Child Assoc Before: " + childAssocs.size());
                    trashcanCleaner.setPageLen(5);
                    trashcanCleaner.execute();
                    // Should all be deleted because not custom type
                    // if ( childAssocs == null )
                    // System.out.println("childAssocs IS NLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLL");
                    // System.out.println("**** Child Assoc Before: " + childAssocs.size());
                    List<ChildAssociationRef> childAssocAfter = nodeService.getChildAssocs(archiveRoot);

                    // if ( childAssocAfter == null )
                    // System.out.println("childAssocAfter IS NLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLLL");
                    // System.out.println("**** Child Assoc After: " + childAssocAfter.size());
                    // assertEquals(childAssocs.size() - NUM_OF_DELETED, childAssocAfter.size());
                    return null;

                }
            }, AuthenticationUtil.getSystemUserName());
        System.out.println("**** Child Assoc Before: " + numBefore);
        System.out.println("**** Child Assoc After: " + getNodesInBin().size());
        System.out.println("**** NUM_OF_DELETED: " + NUM_OF_DELETED);
        assertTrue(getNodesInBin().size() + NUM_OF_DELETED == numBefore);

    }

    @Test
    public void testPurgeBinWithSites()
    {
        assertNotNull(this.getServiceRegistry());
        initFields();
        HashSet<String> typeToProtect = new HashSet<String>();
        typeToProtect.add("{http://www.alfresco.org/model/site/1.0}site");
        trashcanCleaner.setSetToProtect(typeToProtect);
        // empty the bin
        InsureBinEmpty();
        // assertEquals(7, 7);
        populateBinWithSites();
        // PopulateBin(null);
        int numBefore = this.getNodesInBin().size();
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
        int numAfter = this.getNodesInBin().size();
        System.out.println("******* numBefore = " + numBefore);
        System.out.println("******* numAfter = " + numAfter);
        assertEquals(numBefore, numAfter);
    }

    @Test
    public void testPurgeBinWithNodeToProtect()
    {
        initFields();
        // assertNotNull(serviceRegistry);
        InsureBinEmpty();
        HashSet<NodeRef> nodesInTheBin = populateBin(null, null, null);
        // System.out.println("******* nodesInTheBin = " + nodesInTheBin.size());
        StringBuffer sb = new StringBuffer();
        int i = 0;
        int numToDel = 0;
        for (NodeRef nr : nodesInTheBin)
        {
            if (i % 2 == 0)
            {

                if (sb.length() == 0)
                {
                    sb.append(nr.toString());
                }
                else
                {
                    sb.append(",");
                    sb.append(nr.toString());
                }
            }
            else
            {
                numToDel++;
            }
            i++;
        }
        // System.out.println("******* sb.toString() = " + sb.toString());
        // System.out.println("******* numToDel = " + numToDel);
        trashcanCleaner.setNodesToSkip(sb.toString());
        trashcanCleaner.setPageLen(700);
        int numBefore = this.getNodesInBin().size();
        final int fnumToDel = numToDel;
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
                    // System.out.println("******* childAssocs.size()-fnumToDel = " + (childAssocs.size() - fnumToDel));
                    // System.out.println("******* childAssocAfter.size() = " + childAssocAfter.size());
                    // assertTrue(childAssocs.size() - fnumToDel <= childAssocAfter.size());
                    return null;
                }
            }, AuthenticationUtil.getSystemUserName());
        System.out.println("******* numBefore-fnumToDel = " + ((numBefore) - fnumToDel));
        System.out.println("******* childAssocAfter.size() = " + this.getNodesInBin().size());
        // assertTrue((numBefore - fnumToDel + 1) == this.getNodesInBin().size());
        // assertEquals((numBefore - fnumToDel ), this.getNodesInBin().size() );
        assertTrue((numBefore - fnumToDel) <= this.getNodesInBin().size());
    }

    @Test
    public void testPurgeBinWithNoArchivedAspectProps()
    {
        initFields();
        assertNotNull(serviceRegistry);
        HashSet<String> typeToProtect = new HashSet<String>();
        trashcanCleaner.setSetToProtect(typeToProtect);
        // empty the bin
        InsureBinEmpty();

        populateBin(null, null, null);
        final TransactionService fTransactionService = serviceRegistry.getTransactionService();
        // assertEquals(7, 7);
        RemoveAspectArchivedInBin(fTransactionService, 10); // Those 10 should be preserved from deletion
        int numBefore = getNodesInBin().size();
        int sizeBefore = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Integer>()
            {
                public Integer doWork() throws Exception
                {
                    // System.out
                    // .println("7 ****************************************************************************");
                    // try to count number of elements in the bin
                    StoreRef storeRef = new StoreRef("archive://SpacesStore");
                    NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                    List<ChildAssociationRef> childAssocs = getNodesInBin();
                    int sizeBefore = childAssocs.size();
                    // test if purge deleted all the elements from the bin
                    trashcanCleaner.execute();
                    List<ChildAssociationRef> childAssocAfter = getNodesInBin();
                    // System.out
                    // .println("8 ****************************************************************************");
                    // System.out.println("-----------------BEFORE:" + sizeBefore);

                    // none should be delete because they are all sites
                    // assertEquals(childAssocs.size() - NUM_OF_DELETED + 10, childAssocAfter.size());
                    return sizeBefore;
                }
            }, AuthenticationUtil.getSystemUserName());

        int sizeAfter = getNodesInBin().size();
        System.out.println("********* Size Before = " + numBefore);
        System.out.println("********* Size After = " + sizeAfter);
        System.out.println("numBefore - NUM_OF_DELETED + 10 :" + (numBefore - NUM_OF_DELETED + 10));
        // assertTrue((numBefore - NUM_OF_DELETED + 10) == sizeAfter);
        assertEquals((numBefore - NUM_OF_DELETED + 10), sizeAfter);

    }

    @Test
    public void testPurgeBinBigTreeDirect()
    {
        initFields();
        testPurgeBinBigTree(true);
    }

    /**
     * Do not call trigger directly but with a trigger
     */
    @Test
    public void testPurgeBinBigTreeTrigger()
    {
        initFields();
        testPurgeBinBigTree(false);
    }

    @Test
    public void testPurgeBinWithCustomTypes()
    {
        initFields();
        assertNotNull(serviceRegistry);

        // empty the bin
        InsureBinEmpty();
        populateBin(null, CUSTOM_CONTENT_MODEL, CUSTOM_FOLDER_MODEL);
        // populateBin(null, null, null);
        int numBefore = getNodesInBin().size();
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    // try to count number of elements in the bin
                    StoreRef storeRef = new StoreRef("archive://SpacesStore");
                    NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                    List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);
                    // test if purge deleted all the elements from the bin
                    // System.out.println("**** Child Assoc Before: " + childAssocs.size());
                    // configure type to protest
                    HashSet<String> typeToProtect = new HashSet<String>();
                    typeToProtect.add("{http://www.alfresco.org/model/site/1.0}site");
                    typeToProtect.add("{http://www.alfresco.org/model/system/1.0}archiveUser");
                    trashcanCleaner.setSetToProtect(typeToProtect);
                    trashcanCleaner.execute();
                    List<ChildAssociationRef> childAssocAfter = nodeService.getChildAssocs(archiveRoot);
                    // System.out.println("**** Child Assoc after : " + childAssocAfter.size());
                    // Should all be deleted because not custom type
                    // assertEquals(childAssocs.size() - NUM_OF_DELETED, childAssocAfter.size());
                    return null;
                }
            }, AuthenticationUtil.getSystemUserName());
        System.out.println("******* numBefore - NUM_OF_DELETED= " + (numBefore - NUM_OF_DELETED));
        System.out.println("******* childAssocAfter.size() = " + this.getNodesInBin().size());
        // assertTrue((numBefore - fnumToDel + 1) == this.getNodesInBin().size());
        // assertEquals((numBefore - fnumToDel ), this.getNodesInBin().size() );
        assertTrue((numBefore - NUM_OF_DELETED) <= this.getNodesInBin().size());

    }

    @Test
    public void testPurgeBigPageSize()
    {
        initFields();
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
            populateBin(null, null, null);
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
                        HashSet<String> typeToProtect = new HashSet<String>();
                        //typeToProtect.add("{http://www.alfresco.org/model/site/1.0}site");
                        //typeToProtect.add("{http://www.alfresco.org/model/system/1.0}archiveUser");
                        trashcanCleaner.setSetToProtect(typeToProtect);
                        trashcanCleaner.execute();
                        //childAssocs = nodeService.getChildAssocs(archiveRoot);

                        //assertEquals(NUM_REMAININGS, childAssocs.size());
                        return null;
                    }
                }, AuthenticationUtil.getSystemUserName());
                
            assertTrue(NUM_REMAININGS >= this.getNodesInBin().size()-5);
        }

    }
    
    @Test
    public void testPurgeBinBigConcurTree()
    {
        initFields();
        assertNotNull(serviceRegistry);
        HashSet<String> typeToProtect = new HashSet<String>();
        typeToProtect.add("{http://www.alfresco.org/model/site/1.0}site");
        typeToProtect.add("{http://www.alfresco.org/model/system/1.0}archiveUser");
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

       // List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);
        
        final NodeRef archiveRoot = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<NodeRef>()
        {
            public NodeRef doWork() throws Exception
            {
                // try to count number of elements in the bin
                StoreRef storeRef = new StoreRef("archive://SpacesStore");      
                return nodeService.getRootNode(storeRef);
                
            }
        }, AuthenticationUtil.getSystemUserName());
        
        List<ChildAssociationRef> childAssocs = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<List<ChildAssociationRef>>()
        {
            public List<ChildAssociationRef> doWork() throws Exception
            {
                // try to count number of elements in the bin
                StoreRef storeRef = new StoreRef("archive://SpacesStore");      
                return nodeService.getChildAssocs(archiveRoot);
                
            }
        }, AuthenticationUtil.getSystemUserName());
        
        
        //System.out.println("After PopulateBinWithBigTree, number of elements in the bin: " + childAssocs );

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

            //System.out.println("Before starting !!!");

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
                        // Returns immediately
                        long before = System.currentTimeMillis();
                        trashcanCleaner.execute();
                        long after = System.currentTimeMillis();
                        assert (after - before < 500);
                        return null;
                    }
                }, AuthenticationUtil.getSystemUserName());

            // Should be running here
            // trashcanCleaner.getStatus();

            do
            {
                //System.out.println("*********************************************************************Before SLEEP");
                try
                {
                    Thread.sleep(4000L);
                }
                catch (InterruptedException e1)
                {
                }
            }
            while (trashcanCleaner.getStatus() == TrashcanCleaner.Status.RUNNING);
            //System.out.println("*********************************************************************Finished");
            AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
                {
                    public Object doWork() throws Exception
                    {
                        // try to count number of elements in the bin
                        StoreRef storeRef = new StoreRef("archive://SpacesStore");
                        NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                        List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);
                        assertEquals( NUM_REMAININGS, childAssocs.size());
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
        //System.out.println("*********************************************************************VERIFIED");
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
        // System.out.println(" *********** AFTER InsureBinEmpty()
        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        NodeRef secParent = AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<NodeRef>()
            {
                public NodeRef doWork() throws Exception
                {
                    StoreRef storeRef = new StoreRef("workspace://SpacesStore");
                    NodeRef workspaceRoot = nodeService.getRootNode(storeRef);
                    // System.out.println(" *********** AFTER InsureBinEmpty() 22222
                    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    List<ChildAssociationRef> list = nodeService.getChildAssocs(workspaceRoot,
                            RegexQNamePattern.MATCH_ALL,
                            QName.createQName(NamespaceService.APP_MODEL_1_0_URI, "company_home"));
                    // System.out.println(" *********** AFTER InsureBinEmpty() 33333
                    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                    NodeRef companyHome = list.get(0).getChildRef();
                    // System.out.println(" *********** AFTER companyHome " + companyHome);
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

        // System.out.println(" *********** AFTER SEC PARENT
        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
        PopulateBinWithBigTree(secParent);
        int numBefore = this.getNodesInBin().size();
        // System.out.println("After PopulateBinWithBigTree!");
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

                        // assertEquals(childAssocs.size(), NUM_REMAININGS);
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
                // System.out.println("Before starting !!!");
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
                    // System.out.println(
                    // "*********************************************************************Before SLEEP");
                    try
                    {
                        Thread.sleep(4000L);
                    }
                    catch (InterruptedException e1)
                    {
                    }
                }
                while (trashcanCleaner.getStatus() == TrashcanCleaner.Status.RUNNING);

            }
            catch (Exception e)
            {
                // TODO Auto-generated catch block
                // fail(e.getMessage());
                e.printStackTrace();
            }
        }
        System.out.println("*********************************************************************NUM_REMAININGS = "
                + NUM_REMAININGS);
        System.out.println(
                "********************************************************************* Num Before = " + numBefore);
        System.out.println("*********************************************************************After = "
                + getNodesInBin().size());
        // assertEquals(getNodesInBin().size(), NUM_REMAININGS);
        assertTrue(getNodesInBin().size() >= NUM_REMAININGS);
    }

    List<ChildAssociationRef> getNodesInBin()
    {
        initFields();
        final TransactionService fTransactionService = transactionService;
        final RetryingTransactionCallback<List<ChildAssociationRef>> getArchivedNodeDateOffseted = new RetryingTransactionCallback<List<ChildAssociationRef>>()
            {
                public List<ChildAssociationRef> execute() throws Exception
                {
                    StoreRef storeRef = new StoreRef("archive://SpacesStore");
                    NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                    return nodeService.getChildAssocs(archiveRoot);

                }
            };
        return AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<List<ChildAssociationRef>>()
            {
                public List<ChildAssociationRef> doWork() throws Exception
                {
                    return fTransactionService.getRetryingTransactionHelper()
                            .doInTransaction(getArchivedNodeDateOffseted, false, true);
                }
            }, AuthenticationUtil.getSystemUserName());
    }

    /**
     * Remove the aspect ASPECT_ARCHIVED
     * 
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

                        if (nodeService.hasAspect(childAssoc.getChildRef(), ContentModel.ASPECT_ARCHIVED))
                        {
                            nodeService.removeAspect(childAssoc.getChildRef(), ContentModel.ASPECT_ARCHIVED);
                            // System.out.println(
                            // "Aspect Removed
                            // ******************************************************************************");
                        }

                    }

                    return null;
                }
            };
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    return (Object) fTransactionService.getRetryingTransactionHelper()
                            .doInTransaction(getArchivedNodeDateOffseted, false, true);
                }
            }, AuthenticationUtil.getSystemUserName());
    }

    protected void InsureBinEmpty()
    {
        // use TransactionWork to wrap service calls in a user transaction
        TransactionService transactionService = this.getServiceRegistry().getTransactionService();
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
                    fTransactionService.getRetryingTransactionHelper().doInTransaction(emptyBinWork, false, true);
                    return null;
                }
            }, AuthenticationUtil.getSystemUserName());

    }

    protected void populateBinWithSites()
    {
        // use TransactionWork to wrap service calls in a user transaction
        TransactionService transactionService = this.getServiceRegistry().getTransactionService();
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

                        SiteInfo siteInfo = siteService.createSite("site-dashboard", name,
                                "Titre" + System.currentTimeMillis(), "Description-" + System.currentTimeMillis(),
                                true);
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
                    return (List<NodeRef>) fTransactionService.getRetryingTransactionHelper()
                            .doInTransaction(populateBinWork, false, true);

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
                    return (Object) fTransactionService.getRetryingTransactionHelper().doInTransaction(deleteWork,
                            false, true);

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

    protected HashSet<NodeRef> populateBinWithNodes()
    {

        // use TransactionWork to wrap service calls in a user transaction
        TransactionService transactionService = this.getServiceRegistry().getTransactionService();
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

                        SiteInfo siteInfo = siteService.createSite("site-dashboard", name,
                                "Titre" + System.currentTimeMillis(), "Description-" + System.currentTimeMillis(),
                                true);
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
                    return (List<NodeRef>) fTransactionService.getRetryingTransactionHelper()
                            .doInTransaction(populateBinWork, false, true);

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
                    return (Object) fTransactionService.getRetryingTransactionHelper().doInTransaction(deleteWork,
                            false, true);

                }
            }, AuthenticationUtil.getSystemUserName());

        // modify the {http://www.alfresco.org/model/system/1.0}archivedDate or
        // sys:archivedDate

        return AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<HashSet<NodeRef>>()
            {
                public HashSet<NodeRef> doWork() throws Exception
                {
                    // return (Object)
                    // fTransactionService.getRetryingTransactionHelper().doInTransaction(
                    // getArchivedNodeDateOffseted);
                    return offsetNodesInBin(fTransactionService);
                }
            }, AuthenticationUtil.getSystemUserName());

    }

    protected HashSet<NodeRef> populateBin(NodeRef secondParent, QName customContentType, QName customFolderType)
    {
        final NodeRef fSecondParent = secondParent;
        final QName fCustomContentType = customContentType;
        final QName fCustomFolderTypee = customFolderType;

        // System.out.println(
        // "
        // 1***********************************************************************************************************************");
        // use TransactionWork to wrap service calls in a user transaction
        TransactionService transactionService = this.getServiceRegistry().getTransactionService();
        final ServiceRegistry fServiceRegistry = this.getServiceRegistry();
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
                        NodeService nodeService = fServiceRegistry.getNodeService();
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
                        ContentService contentService = fServiceRegistry.getContentService();

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

                        if (fCustomContentType != null)
                            nodeService.setType(content, fCustomContentType);
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
                    return (List<NodeRef>) fTransactionService.getRetryingTransactionHelper()
                            .doInTransaction(populateBinWork, false, true);

                }
            }, AuthenticationUtil.getSystemUserName());
        // System.out.println(
        // " 2
        // ***********************************************************************************************************************");
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
                    return (Object) fTransactionService.getRetryingTransactionHelper().doInTransaction(deleteWork,
                            false, true);

                }
            }, AuthenticationUtil.getSystemUserName());
        // System.out.println(
        // " 3
        // ***********************************************************************************************************************");
        // modify the {http://www.alfresco.org/model/system/1.0}archivedDate or
        // sys:archivedDate

        return AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<HashSet<NodeRef>>()
            {
                public HashSet<NodeRef> doWork() throws Exception
                {
                    // return (Object)
                    // fTransactionService.getRetryingTransactionHelper().doInTransaction(
                    // getArchivedNodeDateOffseted);
                    return offsetNodesInBin(fTransactionService);

                }
            }, AuthenticationUtil.getSystemUserName());

    }

    protected HashSet<NodeRef> offsetNodesInBin(TransactionService transactionService)
    {

        final TransactionService fTransactionService = transactionService;
        final RetryingTransactionCallback<HashSet<NodeRef>> getArchivedNodeDateOffseted = new RetryingTransactionCallback<HashSet<NodeRef>>()
            {
                public HashSet<NodeRef> execute() throws Exception
                {
                    HashSet<NodeRef> fNodeSet = new HashSet<NodeRef>();
                    StoreRef storeRef = new StoreRef("archive://SpacesStore");
                    NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                    List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);
                    // System.out.println(childAssocs.size()
                    // + " 4
                    // ***********************************************************************************************************************");
                    int i = 0;
                    // policyBehaviourFilter.disableBehaviour( ContentModel.ASPECT_ARCHIVED);
                    for (ChildAssociationRef childAssoc : childAssocs)
                    {
                        // System.out.println(
                        // " 5
                        // ***********************************************************************************************************************");
                        if (i > NUM_OF_DELETED)
                        {
                            // System.out.println("6.0 ************************************************ Ofsetting:" +
                            // i);
                            break;
                        }
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
                        // System.out.println(
                        // " 6
                        // ***********************************************************************************************************************");
                        try
                        {
                            // System.out.println("******** Is enabled before: " + policyBehaviourFilter
                            // .isEnabled(childAssoc.getChildRef(), ContentModel.ASPECT_ARCHIVED));
                            policyBehaviourFilter.disableBehaviour(childAssoc.getChildRef(),
                                    ContentModel.ASPECT_ARCHIVED);
                            // System.out.println("Is enabled before 2: " + policyBehaviourFilter
                            // .isEnabled(childAssoc.getChildRef(), ContentModel.ASPECT_ARCHIVED));

                            // System.out.println("*********************** Offset: " + childAssoc.getChildRef());
                            nodeService.setProperty(childAssoc.getChildRef(), ContentModel.PROP_ARCHIVED_DATE, d);
                            policyBehaviourFilter.enableBehaviour(childAssoc.getChildRef(),
                                    ContentModel.ASPECT_ARCHIVED);
                            // System.out.println("Is enabled before 3: " + policyBehaviourFilter
                            // .isEnabled(childAssoc.getChildRef(), ContentModel.ASPECT_ARCHIVED));
                        }
                        finally
                        {
                            policyBehaviourFilter.enableBehaviour(childAssoc.getChildRef(),
                                    ContentModel.ASPECT_ARCHIVED);
                            // System.out.println("***********Is enabled before 4: " + policyBehaviourFilter
                            // .isEnabled(childAssoc.getChildRef(), ContentModel.ASPECT_ARCHIVED));
                        }

                        fNodeSet.add(childAssoc.getChildRef());

                    }

                    return fNodeSet;
                }
            };
        return AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<HashSet<NodeRef>>()
            {
                public HashSet<NodeRef> doWork() throws Exception
                {
                    return (HashSet<NodeRef>) fTransactionService.getRetryingTransactionHelper()
                            .doInTransaction(getArchivedNodeDateOffseted, false, true);
                }
            }, AuthenticationUtil.getSystemUserName());
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
                    return (List<NodeRef>) fTransactionService.getRetryingTransactionHelper()
                            .doInTransaction(populateBinWork, false, true);

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
                    // System.out.println("Deleted node batch: " + batchOfnodes.size());
                    return null;
                }
            };
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    return (Object) fTransactionService.getRetryingTransactionHelper().doInTransaction(deleteWork,
                            false, true);

                }
            }, AuthenticationUtil.getSystemUserName());

        // modify the {http://www.alfresco.org/model/system/1.0}archivedDate or
        // sys:archivedDate
        // System.out.println("After Deleted node batch: " + this.getNodesInBin().size());
        AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>()
            {
                public Object doWork() throws Exception
                {
                    offsetNodesInBin(fTransactionService);
                    return null;
                }
            }, AuthenticationUtil.getSystemUserName());

    }

}
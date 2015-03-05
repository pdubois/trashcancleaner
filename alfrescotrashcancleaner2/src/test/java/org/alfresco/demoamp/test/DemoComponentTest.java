package org.alfresco.demoamp.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alfresco.demoamp.DemoComponent;
import org.alfresco.model.ContentModel;
import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.policy.BehaviourFilter;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.repo.transaction.RetryingTransactionHelper.RetryingTransactionCallback;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.cmr.repository.StoreRef;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;
import org.alfresco.service.namespace.RegexQNamePattern;
import org.alfresco.service.transaction.TransactionService;
import org.alfresco.trashcan.cleaner.TrashcanCleaner;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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
 * 
 * @author Gabriele Columbro
 * @author Maurizio Pillitu
 */

@RunWith(RemoteTestRunner.class)
@Remote(runnerClass = SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:alfresco/application-context.xml")
public class DemoComponentTest
{

    private static final String ADMIN_USER_NAME = "admin";
    private static final int NODE_CREATION_BATCH_SIZE = 99;

    static Logger log = Logger.getLogger(DemoComponentTest.class);

    @Autowired
    protected DemoComponent demoComponent;

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

    @Test
    public void testWiring()
    {
        assertNotNull(demoComponent);
    }

    @Test
    public void testGetCompanyHome()
    {
        AuthenticationUtil.setFullyAuthenticatedUser(ADMIN_USER_NAME);
        NodeRef companyHome = demoComponent.getCompanyHome();
        assertNotNull(companyHome);
        String companyHomeName = (String) nodeService.getProperty(companyHome, ContentModel.PROP_NAME);
        assertNotNull(companyHomeName);
        assertEquals("Company Home", companyHomeName);
    }

    @Test
    public void testChildNodesCount()
    {
        AuthenticationUtil.setFullyAuthenticatedUser(ADMIN_USER_NAME);
        NodeRef companyHome = demoComponent.getCompanyHome();
        int childNodeCount = demoComponent.childNodesCount(companyHome);
        assertNotNull(childNodeCount);
        // There are 7 folders by default under Company Home
        assertTrue(true);
    }

    @Test
    public void testPurgeBin()
    {
        assertNotNull(serviceRegistry);
        // empty the bin
        InsureBinEmpty();
        assertTrue(true);
        PopulateBin();
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

                    assertEquals(childAssocs.size(), 90);
                    return null;
                }
            }, AuthenticationUtil.getSystemUserName());

    }

    @Test
    public void testPurgeBinPageSize()
    {
        assertNotNull(serviceRegistry);
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
            PopulateBin();
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

                        assertEquals(childAssocs.size(), 90);
                        return null;
                    }
                }, AuthenticationUtil.getSystemUserName());
        }

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
                    int i = 1;
                    for (ChildAssociationRef childAssoc : childAssocs)
                    {
                        nodeService.deleteNode(childAssoc.getChildRef());
                        i++;
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

    protected void PopulateBin()
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

        // modify the {http://www.alfresco.org/model/system/1.0}archivedDate or sys:archivedDate

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
                        if (i > 10)
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

}

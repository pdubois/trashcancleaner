package alternative.trashcancleaner.platformsample;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import alternative.trashcancleaner.platformsample.TrashcanCleaner;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/**
 * Webscript populating the trashcan for testing purpose
 * @author Philippe
 *
 */
public class PopulateBin extends AbstractWebScript
{
    private static final Log logger = LogFactory.getLog(PopulateBin.class);
    
    // Defining number of elements created in a call 
    private static final int NODE_CREATION_BATCH_SIZE = 1000;

    private TrashcanCleaner trashcanCleaner;
    
    private ServiceRegistry serviceRegistry;
    
    private NodeService nodeService;
    
    protected BehaviourFilter policyBehaviourFilter;
    
    public void setPolicyBehaviourFilter(BehaviourFilter policyBehaviourFilter)
    {
        this.policyBehaviourFilter = policyBehaviourFilter;
    }

    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }

    public void setServiceRegistry(ServiceRegistry serviceRegistry)
    {
        this.serviceRegistry = serviceRegistry;
    }

    public void setTrashcanCleaner(TrashcanCleaner trashcanCleaner)
    {
        this.trashcanCleaner = trashcanCleaner;
    }

    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException
    {
            boolean offset = true;
            JSONObject jResult = new JSONObject();
            String sOffset = req.getParameter("offset");
            if( sOffset != null && sOffset.equals("false"))
            {
                offset = false;
            }
            try
            {
                Set<NodeRef> nodes = populateBin(null, null, null, offset);
                jResult.put("GEN", nodes.size());
            }
            catch (JSONException e1)
            {
                e1.printStackTrace();
            }
            res.getWriter().write(jResult.toString());        
    }
    
    /**
     * 
     * @param secondParent
     * @param customContentType
     * @param customFolderType
     * @param offset if true archive date will be changed to 28-04-1974
     * @return
     */
    protected HashSet<NodeRef> populateBin(NodeRef secondParent, QName customContentType, QName customFolderType, boolean offset)
    {
        final boolean fOffset = offset;
        final NodeRef fSecondParent = secondParent;
        final QName fCustomContentType = customContentType;


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
                        //ContentService contentService = serviceRegistry.getContentService();
                        //serviceRegistry.getContentService();
                        //ContentWriter writer = contentService.getWriter(content, ContentModel.PROP_CONTENT, true);
                        //writer.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);
                        //writer.setEncoding("UTF-8");
                        //String text = "The quick brown fox jumps over the lazy dog";
                        //writer.putContent(text);
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
                            .doInTransaction(populateBinWork);

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
        
            return AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<HashSet<NodeRef>>()
            {
                public HashSet<NodeRef> doWork() throws Exception
                {
                    return offsetNodesInBin(fTransactionService, batchOfnodes, fOffset);

                }
            }, AuthenticationUtil.getSystemUserName());
       

    }
    
    protected HashSet<NodeRef> offsetNodesInBin(TransactionService transactionService, List<NodeRef> batchOfnodes, boolean offset )
    {

        final boolean fOffset = offset;
        final TransactionService fTransactionService = transactionService;
        final List<NodeRef> fBatchOfnodes = batchOfnodes;
        final RetryingTransactionCallback<HashSet<NodeRef>> getArchivedNodeDateOffseted = new RetryingTransactionCallback<HashSet<NodeRef>>()
            {
                public HashSet<NodeRef> execute() throws Exception
                {
                    HashSet<NodeRef> fNodeSet = new HashSet<NodeRef>();
                    StoreRef storeRef = new StoreRef("archive://SpacesStore");
                    NodeRef archiveRoot = nodeService.getRootNode(storeRef);
                    //List<ChildAssociationRef> childAssocs = nodeService.getChildAssocs(archiveRoot);
                    int i = 0;
                    for (NodeRef nodeRef : fBatchOfnodes)
                    {

                        i++;
                        //The node in the bin has same uuid
                        NodeRef archivedNodeRef = new NodeRef("archive", "SpacesStore", nodeRef.getId());
                        if (fOffset)
                        {
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
                                nodeService.setProperty(archivedNodeRef, ContentModel.PROP_ARCHIVED_DATE, d);
                            }
                            finally
                            {
                                policyBehaviourFilter.enableBehaviour(ContentModel.ASPECT_ARCHIVED);
                            }
                        }
                        fNodeSet.add(archivedNodeRef);
                    }
                    return fNodeSet;
                }
            };
        return AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<HashSet<NodeRef>>()
            {
                public HashSet<NodeRef> doWork() throws Exception
                {
                    return (HashSet<NodeRef>) fTransactionService.getRetryingTransactionHelper()
                            .doInTransaction(getArchivedNodeDateOffseted);
                }
            }, AuthenticationUtil.getSystemUserName());
    }
}


    


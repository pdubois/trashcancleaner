package org.alfresco.trashcan.webscripts;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/**
 * Webscript returning information on trashcan cleaner
 * @author Philippe
 *
 */
public class GetStatus extends AbstractWebScript
{
    private static final Log logger = LogFactory.getLog(GetStatus.class);


    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException
    {
            JSONObject jResult = new JSONObject();
            try
            {
                jResult.put("STATUS", "OK");
            }
            catch (JSONException e1)
            {
                e1.printStackTrace();
            }
            res.getWriter().write(jResult.toString());        
    }

   
}


package alternative.trashcancleaner.platformsample;

import java.io.IOException;

import alternative.trashcancleaner.platformsample.TrashcanCleaner;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.extensions.webscripts.AbstractWebScript;
import org.springframework.extensions.webscripts.WebScriptRequest;
import org.springframework.extensions.webscripts.WebScriptResponse;

/**
 * Webscript disabling trashcan
 * @author Philippe
 *
 */
public class Enable extends AbstractWebScript
{
    private static final Log logger = LogFactory.getLog(Enable.class);

    private TrashcanCleaner trashcanCleaner;
    
    public void setTrashcanCleaner(TrashcanCleaner trashcanCleaner)
    {
        this.trashcanCleaner = trashcanCleaner;
    }

    @Override
    public void execute(WebScriptRequest req, WebScriptResponse res) throws IOException
    {
            JSONObject jResult = new JSONObject();
            try
            {
                jResult.put("OLDSTATUS", trashcanCleaner.Enable());
            }
            catch (JSONException e1)
            {
                e1.printStackTrace();
            }
            res.getWriter().write(jResult.toString());        
    }

   
}


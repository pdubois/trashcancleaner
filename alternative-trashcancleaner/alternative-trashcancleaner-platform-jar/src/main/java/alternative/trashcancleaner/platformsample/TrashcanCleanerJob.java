package alternative.trashcancleaner.platformsample;


import alternative.trashcancleaner.platformsample.TrashcanCleaner;
import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.scheduling.quartz.QuartzJobBean;


public class TrashcanCleanerJob extends QuartzJobBean {

  @Override
  public void executeInternal(JobExecutionContext context) throws JobExecutionException {
    JobDataMap jobData = context.getJobDetail().getJobDataMap();
    // extract the content cleaner to use
    Object trashcanCleanerObj = jobData.get("trashcanCleaner");
    if (trashcanCleanerObj == null || !(trashcanCleanerObj instanceof TrashcanCleaner)) {
      throw new AlfrescoRuntimeException(
          "TrashcanCleanerJob data must contain valid 'trashcanCleaner' reference");
    }
    final TrashcanCleaner trashcanCleaner = (TrashcanCleaner) trashcanCleanerObj;

    AuthenticationUtil.runAs(new AuthenticationUtil.RunAsWork<Object>() {
      public Object doWork() throws Exception {
        trashcanCleaner.execute();
        return null;
      }
    }, AuthenticationUtil.getSystemUserName());
  }
}

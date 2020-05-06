import jenkins.model.Jenkins
import com.cloudbees.jenkins.plugins.managed_master_hibernation.HibernationConfiguration;

import java.util.logging.Logger

//adds a folder to the bluesteel folder with a filter on specific job templates
Logger logger = Logger.getLogger("init.init_07_configure_hibernation.groovy")
logger.info("BEGIN configure_hibernation")
File disableScript = new File(Jenkins.getInstance().getRootDir(), ".disable-configure_hibernation")
if (disableScript.exists()) {
    logger.info("DISABLE configure_hibernation script")
    return
}

logger.info("enabling hibernation")
HibernationConfiguration.get().setEnabled(true);
logger.info("setting hibernation grace period to 8 hours - 28800 seconds")
HibernationConfiguration.get().setGracePeriod(28800);

logger.info("COMPLETED init_07_configure_hibernation")

disableScript.createNewFile()
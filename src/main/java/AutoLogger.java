import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AutoLogger {

    Runnable autoLogger;
    Logger logger = LoggerFactory.getLogger(AutoLogger.class);
    ScheduledExecutorService executorService;

    public AutoLogger() {
        autoLogger = () -> logger.debug("THE QUICK BROWN FOX JUMPS OVER THE LAZY DOG");
        executorService = Executors.newScheduledThreadPool(1);
    }

    public void startExecutor() {
        if(executorService == null) {
            logger.error("Class not yet instantiated.");
            return;
        }
        executorService.scheduleAtFixedRate(autoLogger, 0, 1, TimeUnit.SECONDS);
    }

}


import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import ch.qos.logback.core.rolling.RolloverFailure;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.util.StringUtils;

/**
 * Extension of FixedWindowRollingPolicy.
 *
 * On each rolling event (which is defined by <triggeringPolicy>), this policy does:
 * 1. Regular log file rolling as FixedWindowsRollingPolicy does
 * 2. Upload the rolled log file to S3 bucket
 *
 * Also, this policy uploads the active log file on JVM exit. If rollingOnExit is true,
 * another log rolling happens and a rolled log is uploaded. If rollingOnExit is false,
 * the active file is directly uploaded.
 *
 * If rollingOnExit is false and if no rolling happened before JVM exits, this rolling
 * policy uploads the active log file as it is.
 */
public class S3TimeBasedRollingPolicy<E> extends TimeBasedRollingPolicy<E> {

    ExecutorService executor = Executors.newFixedThreadPool(1);

    String awsAccessKey;
    String awsSecretKey;
    String s3BucketName;
    String s3FolderName;

    boolean rollingOnExit = true;

    private int periodMinutes;

    AmazonS3 s3Client;

    protected AmazonS3 getS3Client() {
        if (s3Client == null) {
            AWSCredentials cred = new BasicAWSCredentials(getAwsAccessKey(), getAwsSecretKey());
            s3Client = AmazonS3ClientBuilder.standard()
                    .withCredentials(new AWSStaticCredentialsProvider(cred))
                    .withRegion(Regions.AP_SOUTHEAST_1).build();

        }
        return s3Client;
    }

    @Override
    public void start() {
        MinuteTriggeringPolicy<E> triggeringPolicy = new MinuteTriggeringPolicy<E>();
        triggeringPolicy.setPeriodMinutes(periodMinutes);
        setTimeBasedFileNamingAndTriggeringPolicy(triggeringPolicy);

        super.start();
        // add a hook on JVM shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(new ShutdownHookRunnable()));
    }

    @Override
    public void rollover() throws RolloverFailure {
        super.rollover();

        // upload the current log file into S3
        String[] archiveDirectoryParts = getFileNamePattern().split("/");
        String archiveDirectory = StringUtils.join("/", Arrays.copyOf(archiveDirectoryParts, archiveDirectoryParts.length-1));
        File lastArchive = getLastModified(archiveDirectory);

        if(lastArchive != null) {
            uploadFileToS3Async(lastArchive.getPath());
        }
    }

    public static File getLastModified(String directoryFilePath) {
        File directory = new File(directoryFilePath);
        File[] files = directory.listFiles(File::isFile);
        long lastModifiedTime = Long.MIN_VALUE;
        File chosenFile = null;

        if (files != null) {
            for (File file : files) {
                if (file.lastModified() > lastModifiedTime) {
                    chosenFile = file;
                    lastModifiedTime = file.lastModified();
                }
            }
        }

        return chosenFile;
    }

    protected void uploadFileToS3Async(String filename) {
        Path source = Paths.get(filename);
        Path target = Paths.get(filename + ".gz");

        compressToGz(source, target);

        final File compressedFile = target.toFile();

        // if file does not exist or empty, do nothing
        if (!compressedFile.exists() || compressedFile.length() == 0) {
            return;
        }

        // add the S3 folder name in front if specified
        final StringBuffer s3ObjectName = new StringBuffer();
        if (getS3FolderName() != null) {
            s3ObjectName.append(getS3FolderName()).append("/");
        }
        s3ObjectName.append(compressedFile.getName());

        addInfo("Uploading " + filename);
        Runnable uploader = new Runnable() {
            @Override
            public void run() {
                try {
                    getS3Client().putObject(getS3BucketName(), s3ObjectName.toString(), compressedFile);
                    System.out.println(s3ObjectName.toString() + " uploaded to S3");
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        executor.execute(uploader);
    }

    private void compressToGz(Path source, Path target) {
        try (GZIPOutputStream gos = new GZIPOutputStream(
                new FileOutputStream(target.toFile()));
             FileInputStream fis = new FileInputStream(source.toFile())) {

            // copy file
            byte[] buffer = new byte[1024];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                gos.write(buffer, 0, len);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // On JVM exit, upload the current log
    class ShutdownHookRunnable implements Runnable {

        @Override
        public void run() {
            try {
                if (isRollingOnExit())
                    // do rolling and upload the rolled file on exit
                    rollover();
                else
                    // upload the active log file without rolling
                    uploadFileToS3Async(getActiveFileName());

                // wait until finishing the upload
                executor.shutdown();
                executor.awaitTermination(10, TimeUnit.MINUTES);
            } catch (Exception ex) {
                addError("Failed to upload a log in S3", ex);
                executor.shutdownNow();
            }
        }

    }


    public String getAwsAccessKey() {
        return awsAccessKey;
    }

    public void setAwsAccessKey(String awsAccessKey) {
        this.awsAccessKey = awsAccessKey;
    }

    public String getAwsSecretKey() {
        return awsSecretKey;
    }

    public void setAwsSecretKey(String awsSecretKey) {
        this.awsSecretKey = awsSecretKey;
    }

    public String getS3BucketName() {
        return s3BucketName;
    }

    public void setS3BucketName(String s3BucketName) {
        this.s3BucketName = s3BucketName;
    }

    public String getS3FolderName() {
        return s3FolderName;
    }

    public void setS3FolderName(String s3FolderName) {
        this.s3FolderName = s3FolderName;
    }

    public boolean isRollingOnExit() {
        return rollingOnExit;
    }

    public void setRollingOnExit(boolean rollingOnExit) {
        this.rollingOnExit = rollingOnExit;
    }

    public int getPeriodMinutes() {
        return this.periodMinutes;
    }

    public void setPeriodMinutes(int minutes) {
        this.periodMinutes = minutes;
    }


}
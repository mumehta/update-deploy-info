package com.isentia.plugins.updatedeployinfo;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.isentia.googlesheet.ReleaseSpreadsheet;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.apache.commons.io.FilenameUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;

public class UpdateDeployInfo extends Builder implements SimpleBuildStep {

    private String artifact;

    // Fields in config.jelly must match the parameter names in the "DataBoundConstructor"
    @DataBoundConstructor
    public UpdateDeployInfo(String artifact) {
        this.artifact = artifact;
    }

    public String getArtifact() { return artifact; }

    public void setArtifact(String artifact) { this.artifact = artifact; }

    private String getBranchFromJobName(String job) {
        String env = getEnvironmentFromJobName(job);
        if ("dev".equals(env))
            return "develop";
        else if ("uat".equals(env) || "prod".equals(env))
            return "master";
        else
            return "";
    }

    private String getEnvironmentFromJobName(String job) {
        String[] deployEnvSearchInPath = new String[]{"env-dev", "env-uat", "env-prod"};
        String searchEnv = "";
        for (String search : deployEnvSearchInPath) {
            if (job.contains(search)) {
                searchEnv = search;
            }
        }
        if (searchEnv.contains("dev"))
            return "dev";
        else if (searchEnv.contains("uat"))
            return "uat";
        else if (searchEnv.contains("prod"))
            return "prod";
        System.out.println("Environment not detected or is invalid.");
        return "";
    }

    private String getSemanticVersion(String accesskey, String secretkey,
                                      String bucketName, String bucketPrefix, String delimiter){
        AWSCredentials credentials = new BasicAWSCredentials(accesskey, secretkey);
        final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials)).withRegion(Regions.AP_SOUTHEAST_2).build();

        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucketName)
                .withDelimiter(delimiter)
                .withPrefix(bucketPrefix);
        ObjectListing ol = s3.listObjects(listObjectsRequest);
        List<S3ObjectSummary> objects = ol.getObjectSummaries();
        if(objects.size()>0){
            for (Iterator<S3ObjectSummary> objectSummaryIterator = objects.iterator(); objectSummaryIterator.hasNext(); ) {
                S3ObjectSummary next =  objectSummaryIterator.next();
                System.out.println(next.getKey());
            }
        } else {
            System.out.println("No objects in the bucket");
        }

        String fileName = objects.get(objects.size()-1).getKey().split(delimiter)[objects.get(objects.size()-1).getKey().split(delimiter).length - 1];
        String fileWithoutExtn = FilenameUtils.removeExtension(fileName);
        return fileWithoutExtn.split("-")[fileWithoutExtn.split("-").length-1];
    }

    public String getVersion(final String envVersion){
        String[] lstString = envVersion.split("/");
        int version=0;
        for (int i = lstString.length; i > 0; ) {
            String testStr = lstString[--i];
            try {
                version = Integer.parseInt(testStr);
                break;
            } catch (NumberFormatException e){
                continue;
            }
        }
        return Integer.toString(version);
    }

    @Override
    public void perform(Run<?,?> build, FilePath workspace, Launcher launcher, TaskListener listener) {
        String version = "";
        String accesskey = "";
        String secretkey = "";
        String bucketPrefix = "";
        String bucketName = "isentia-build-artifacts";
        String delimiter = "/";
        String project = "";
        String deployEnv = "";
        String clientJsonPath = "";
        try {
            EnvVars envVars = build.getEnvironment(listener);

            version = envVars.get("version")!=null?getVersion(envVars.get("version")):"";
            clientJsonPath = envVars.get("client_json_path")!=null?envVars.get("client_json_path"):"";
            project = envVars.get("JOB_NAME").split(delimiter)[0].toLowerCase();
            deployEnv = getEnvironmentFromJobName(envVars.get("JOB_NAME"));
            bucketPrefix = project
                            + delimiter
                            + this.artifact
                            + delimiter
                            + getBranchFromJobName(envVars.get("JOB_NAME"))
                            + delimiter
                            + version
                            + delimiter;
            accesskey = envVars.get("AWS_ACCESS_KEY_ID")!=null?envVars.get("AWS_ACCESS_KEY_ID"):null;
            secretkey = envVars.get("AWS_SECRET_ACCESS_KEY")!=null?envVars.get("AWS_SECRET_ACCESS_KEY"):null;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        String semvar = getSemanticVersion(accesskey, secretkey, bucketName, bucketPrefix, delimiter);

        ReleaseSpreadsheet releaseSheet = new ReleaseSpreadsheet();
        releaseSheet.updateReleaseSheet(clientJsonPath, project, this.artifact, deployEnv, semvar);
        System.out.println("Setting semantic version: "+semvar+" for: "+this.artifact+ " of project: " + project + " at: "+deployEnv);
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl)super.getDescriptor();
    }

    @Extension // This indicates to Jenkins that this is an implementation of an extension point.
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        private boolean useFrench;

        public DescriptorImpl() {
            load();
        }

        public FormValidation doCheckArtifact(@QueryParameter String artifact)
                throws IOException, ServletException {
            if (artifact.isEmpty() || artifact.trim().length() == 0)
                return FormValidation.error("Please provide artifact");
            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            // Indicates that this builder can be used with all kinds of project types
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Update Release Information to Google Sheet";
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            String useFrench = formData.getString("useFrench");
            save();
            return super.configure(req, formData);
        }

        public boolean getUseFrench() {
            return useFrench;
        }
    }
}


import jenkins.model.*;
import org.jenkinsci.plugins.workflow.libs.*;
import jenkins.scm.api.SCMSource;
import jenkins.plugins.git.*;
import hudson.util.Secret;
import com.cloudbees.plugins.credentials.impl.*;
import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.domains.*; 
import com.cloudbees.pipeline.governance.templates.*;
import com.cloudbees.pipeline.governance.templates.catalog.*;
import org.jenkinsci.plugins.github.GitHubPlugin;
import org.jenkinsci.plugins.github.config.GitHubServerConfig;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import java.util.logging.Logger;

Logger logger = Logger.getLogger("rolloutWorkshopSetup.groovy");

def jenkins = Jenkins.instance
def masterFolder = jenkins.getItem(System.properties.'MASTER_NAME')

//cli groovy = &lt; rollout_workshop_setup.groovy $gitHubPat $githubUsername $githubOrg
String gitHubPat = "REPLACE_GITHUB_PAT"
String githubUsername = "REPLACE_GITHUB_USERNAME"
String githubOrg = "REPLACE_GITHUB_ORG"

//GitHub Credentials
def credentialId = "cbdays-github-username-pat"
Credentials gitHubUsernamePATCred = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, credentialId, "GitHub PAT - username/password", githubUsername, gitHubPat)
SystemCredentialsProvider.getInstance().getStore().addCredentials(Domain.global(), gitHubUsernamePATCred)

def secretCredentialId = "cbdays-github-token-secret"
Credentials gitHubPATCred = new StringCredentialsImpl(CredentialsScope.GLOBAL, secretCredentialId, "GitHub PAT - secret text", Secret.fromString(gitHubPat));
SystemCredentialsProvider.getInstance().getStore().addCredentials(Domain.global(), gitHubPATCred)

//Add GitHub Server config for webhooks
GitHubPlugin.configuration().getConfigs().add(new GitHubServerConfig(secretCredentialId));

//Pipeline Shared Library
GlobalLibraries globalLibs = GlobalConfiguration.all().get(GlobalLibraries.class)
SCMSource libScm = new org.jenkinsci.plugins.github_branch_source.GitHubSCMSource(null, null, "SAME", credentialId, githubOrg, "pipeline-library")
LibraryRetriever libRetriever = new SCMSourceRetriever(libScm)
LibraryConfiguration libConfig = new LibraryConfiguration("cb-days", libRetriever)
libConfig.setDefaultVersion("master")
libConfig.setImplicit(true)
List<LibraryConfiguration> libraries= new ArrayList<LibraryConfiguration>()
libraries.add(libConfig)
globalLibs.setLibraries(libraries)

//Pipeline Template Catalog
SCMSource scm = new GitSCMSource("https://github.com/${githubOrg}/pipeline-template-catalog.git");
scm.setCredentialsId("${credentialId}");
TemplateCatalog catalog = new TemplateCatalog(scm, "master");
catalog.setUpdateInterval("1h");
GlobalTemplateCatalogManagement.get().addCatalog(catalog);
GlobalTemplateCatalogManagement.get().save();
logger.info("Creating new Pipeline Template Catalog");
catalog.updateFromSCM(); 


//microblog-fronted job from Pipeline Template
def name = "microblog-frontend"
def frontendJobXml = """
<org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject plugin="workflow-multibranch@2.21">
  <properties>
    <com.cloudbees.pipeline.governance.templates.classic.multibranch.GovernanceMultibranchPipelinePropertyImpl plugin="cloudbees-workflow-template@3.5">
      <instance>
        <model>workshopCatalog/vuejs-app</model>
        <values class="tree-map">
          <entry>
            <string>deploymentDomain</string>
            <string>v1.k8s.tel</string>
          </entry>
          <entry>
            <string>gcpProject</string>
            <string>cb-days-workshop</string>
          </entry>
          <entry>
            <string>githubCredentialId</string>
            <string>cbdays-github-username-pat</string>
          </entry>
          <entry>
            <string>name</string>
            <string>${name}</string>
          </entry>
          <entry>
            <string>repoOwner</string>
            <string>${githubOrg}</string>
          </entry>
          <entry>
            <string>repository</string>
            <string>microblog-frontend</string>
          </entry>
        </values>
      </instance>
    </com.cloudbees.pipeline.governance.templates.classic.multibranch.GovernanceMultibranchPipelinePropertyImpl>
  </properties>
  <folderViews class="jenkins.branch.MultiBranchProjectViewHolder" plugin="branch-api@2.5.5">
    <owner class="org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" reference="../.."/>
  </folderViews>
  <healthMetrics>
    <com.cloudbees.hudson.plugins.folder.health.WorstChildHealthMetric plugin="cloudbees-folder@6.9">
      <nonRecursive>false</nonRecursive>
    </com.cloudbees.hudson.plugins.folder.health.WorstChildHealthMetric>
    <com.cloudbees.hudson.plugins.folder.health.AverageChildHealthMetric plugin="cloudbees-folders-plus@3.9"/>
    <com.cloudbees.hudson.plugins.folder.health.JobStatusHealthMetric plugin="cloudbees-folders-plus@3.9">
      <success>true</success>
      <failure>true</failure>
      <unstable>true</unstable>
      <unbuilt>true</unbuilt>
      <countVirginJobs>false</countVirginJobs>
    </com.cloudbees.hudson.plugins.folder.health.JobStatusHealthMetric>
    <com.cloudbees.hudson.plugins.folder.health.ProjectEnabledHealthMetric plugin="cloudbees-folders-plus@3.9"/>
  </healthMetrics>
  <icon class="jenkins.branch.MetadataActionFolderIcon" plugin="branch-api@2.5.5">
    <owner class="org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" reference="../.."/>
  </icon>
  <orphanedItemStrategy class="com.cloudbees.hudson.plugins.folder.computed.DefaultOrphanedItemStrategy" plugin="cloudbees-folder@6.9">
    <pruneDeadBranches>true</pruneDeadBranches>
    <daysToKeep>-1</daysToKeep>
    <numToKeep>-1</numToKeep>
  </orphanedItemStrategy>
  <triggers>
    <com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger plugin="cloudbees-folder@6.9">
      <spec>H H/4 * * *</spec>
      <interval>86400000</interval>
    </com.cloudbees.hudson.plugins.folder.computed.PeriodicFolderTrigger>
  </triggers>
  <disabled>false</disabled>
  <sources>
    <jenkins.branch.BranchSource plugin="branch-api@2.5.5">
      <source class="org.jenkinsci.plugins.github_branch_source.GitHubSCMSource" plugin="github-branch-source@2.6.0">
        <id>VueJS</id>
        <apiUri>https://api.github.com</apiUri>
        <credentialsId>cbdays-github-username-pat</credentialsId>
        <repoOwner>bee-cd</repoOwner>
        <repository>microblog-frontend</repository>
        <traits>
          <org.jenkinsci.plugins.github__branch__source.BranchDiscoveryTrait>
            <strategyId>1</strategyId>
          </org.jenkinsci.plugins.github__branch__source.BranchDiscoveryTrait>
          <org.jenkinsci.plugins.github__branch__source.OriginPullRequestDiscoveryTrait>
            <strategyId>1</strategyId>
          </org.jenkinsci.plugins.github__branch__source.OriginPullRequestDiscoveryTrait>
          <org.jenkinsci.plugins.github__branch__source.ForkPullRequestDiscoveryTrait>
            <strategyId>1</strategyId>
            <trust class="org.jenkinsci.plugins.github_branch_source.ForkPullRequestDiscoveryTrait\$TrustPermission"/>
          </org.jenkinsci.plugins.github__branch__source.ForkPullRequestDiscoveryTrait>
        </traits>
      </source>
      <strategy class="jenkins.branch.DefaultBranchPropertyStrategy">
        <properties class="java.util.Arrays\$ArrayList">
          <a class="jenkins.branch.BranchProperty-array"/>
        </properties>
      </strategy>
    </jenkins.branch.BranchSource>
  </sources>
  <factory class="com.cloudbees.pipeline.governance.templates.classic.multibranch.FromTemplateBranchProjectFactory" plugin="cloudbees-workflow-template@3.5">
    <owner class="org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject" reference="../.."/>
    <catalogName>workshopCatalog</catalogName>
    <templateDirectory>vuejs-app</templateDirectory>
    <markerFile>.vuejs</markerFile>
  </factory>
</org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject>
"""

def p = masterFolder.createProjectFromXML(name, new ByteArrayInputStream(frontendJobXml.getBytes("UTF-8")));

logger.info("created $name job")


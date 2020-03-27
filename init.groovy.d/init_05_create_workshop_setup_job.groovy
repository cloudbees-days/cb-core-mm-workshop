import com.cloudbees.hudson.plugins.folder.*
import jenkins.model.Jenkins

import java.util.logging.Logger

//adds a folder to the bluesteel folder with a filter on specific job templates
Logger logger = Logger.getLogger("init.init_05_create-workshop_setup_job.groovy")
println "init_05_create-workshop_setup_job.groovy"
logger.info("BEGIN docker label for create-workshop_setup_job")
File disableScript = new File(Jenkins.getInstance().getRootDir(), ".disable-create_template_folder-script")
if (disableScript.exists()) {
    logger.info("DISABLE create_template_folder script")
    return
}

def j = Jenkins.instance
def masterFolder = j.getItem(System.properties.'MASTER_NAME')

def name = 'workshop-setup'
logger.info("creating $name job")
def job = masterFolder.getItem(name)
if (job != null) {
  logger.info("job $name already existed so deleting")
  job.delete()
}
println "--> creating $name"

def configXml = """
<flow-definition plugin="workflow-job@2.36">
  <actions>
    <org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobAction plugin="pipeline-model-definition@1.5.0"/>
    <org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction plugin="pipeline-model-definition@1.5.0">
      <jobProperties/>
      <triggers/>
      <parameters/>
      <options/>
    </org.jenkinsci.plugins.pipeline.modeldefinition.actions.DeclarativeJobPropertyTrackerAction>
  </actions>
  <description></description>
  <keepDependencies>false</keepDependencies>
  <properties>
    <hudson.plugins.jira.JiraProjectProperty plugin="jira@3.0.11"/>
    <jenkins.model.BuildDiscarderProperty>
      <strategy class="hudson.tasks.LogRotator">
        <daysToKeep>-1</daysToKeep>
        <numToKeep>3</numToKeep>
        <artifactDaysToKeep>-1</artifactDaysToKeep>
        <artifactNumToKeep>-1</artifactNumToKeep>
      </strategy>
    </jenkins.model.BuildDiscarderProperty>
    <hudson.model.ParametersDefinitionProperty>
      <parameterDefinitions>
        <hudson.model.PasswordParameterDefinition>
          <name>githubPat</name>
          <description>The GitHub.com Personal Access Token you created for this workshop. If you haven&apos;t created one yet, use this &lt;a href=&quot;https://github.com/settings/tokens/new?scopes=repo,read:user,user:email,admin:repo_hook,admin:org_hook&quot;&gt;link&lt;/a&gt; to create one.</description>
          <defaultValue></defaultValue>
        </hudson.model.PasswordParameterDefinition>
        <hudson.model.StringParameterDefinition>
          <name>githubUsername</name>
          <description>The GitHub.com username you are using for this workshop.</description>
          <defaultValue></defaultValue>
          <trim>true</trim>
        </hudson.model.StringParameterDefinition>
        <hudson.model.StringParameterDefinition>
          <name>githubOrg</name>
          <description>The GitHub.com Organization that you created for this workshop.</description>
          <defaultValue></defaultValue>
          <trim>true</trim>
        </hudson.model.StringParameterDefinition>
        <hudson.model.StringParameterDefinition>
          <name>k8sNamespace</name>
          <description>Do not change the default value unless asked to do so by your instructor.</description>
          <defaultValue>cloudbees-core</defaultValue>
          <trim>false</trim>
        </hudson.model.StringParameterDefinition>
      </parameterDefinitions>
    </hudson.model.ParametersDefinitionProperty>
  </properties>
  <definition class="org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition" plugin="workflow-cps@2.79">
    <script>def masterName = System.properties.&apos;MASTER_NAME&apos;
def encryptedPAT = new hudson.util.Secret(githubPAT).getEncryptedValue()
def entrySecret = UUID.randomUUID().toString()

pipeline {
  agent {
    kubernetes {
      label &apos;kubectl&apos;
      yaml &quot;&quot;&quot;
kind: Pod
metadata:
  name: kubectl
spec:
  serviceAccountName: jenkins
  containers:
  - name: kubectl
    image: gcr.io/cloud-builders/kubectl
    resources:
      requests:
        memory: &quot;500Mi&quot;
    command:
    - cat
    tty: true
  securityContext:
    runAsUser: 1000  
      &quot;&quot;&quot;
    }
  }
  stages {
    stage(&apos;Fork Repos&apos;) {
      steps {
        echo &quot;GitHub Username:  \${githubUsername}&quot;
        echo &quot;GitHub Organization: \${githubOrg}&quot;
        container(&apos;utils&apos;) {
          sh(script: &quot;&quot;&quot;
            curl --silent -H &quot;Authorization: token \$githubPAT&quot; --data &apos;{&quot;organization&quot;:&quot;\${githubOrg}&quot;}&apos; https://api.github.com/repos/cloudbees-days/pipeline-library/forks
            curl --silent -H &quot;Authorization: token \$githubPAT&quot; --data &apos;{&quot;organization&quot;:&quot;\${githubOrg}&quot;}&apos; https://api.github.com/repos/cloudbees-days/pipeline-template-catalog/forks
            curl --silent -H &quot;Authorization: token \$githubPAT&quot; --data &apos;{&quot;organization&quot;:&quot;\${githubOrg}&quot;}&apos; https://api.github.com/repos/cloudbees-days/core-config-bundle/forks
            curl --silent -H &quot;Authorization: token \$githubPAT&quot; --data &apos;{&quot;organization&quot;:&quot;\${githubOrg}&quot;}&apos; https://api.github.com/repos/cloudbees-days/microblog-frontend/forks
            curl --silent -H &quot;Authorization: token \$githubPAT&quot; --data &apos;{&quot;organization&quot;:&quot;\${githubOrg}&quot;}&apos; https://api.github.com/repos/cloudbees-days/microblog-backend/forks
          &quot;&quot;&quot;)
         }
      }
    }
    stage(&apos;Create Config Bundle&apos;) {
      steps {
        echo &quot;master name:  \${masterName}&quot;
        echo &quot;encrypted token: \${encryptedPAT}&quot;
        container(&apos;utils&apos;) {
          sh(script: &quot;&quot;&quot;
              mkdir core-config-bundle
              cd core-config-bundle
              git init
              git config user.email &quot;deployBot@cb-sa.io&quot;
              git config user.name &quot;\${githubUsername}&quot;
              git remote add origin https://\${githubUsername}:\${githubPAT}@github.com/\${githubOrg}/core-config-bundle.git
              git pull origin master
              sed -i.bak &apos;s#REPLACE_GITHUB_ORG#\${githubOrg}#&apos; jenkins.yaml
              sed -i.bak &apos;s#REPLACE_WITH_JENKINS_ENCODED_PAT#\${encryptedPAT}#&apos; jenkins.yaml
              sed -i.bak &apos;s#REPLACE_WITH_YOUR_GITHUB_USERNAME#\${githubUsername}#&apos; jenkins.yaml
              git add *
              git commit -a -m &apos;updating \${githubOrg}/core-config bundle with encrypted GitHub PAT and GitHub Username&apos;
              git push -u origin master
          &quot;&quot;&quot;)
        }
        container(&apos;kubectl&apos;) {
          sh &quot;mkdir \${masterName}&quot;
          sh &quot;cp core-config-bundle/*.yaml \${masterName}&quot;
          sh &quot;kubectl cp --namespace \${k8sNamespace} \${masterName} cjoc-0:/var/jenkins_home/jcasc-bundles-store/&quot;
          sh &quot;kubectl exec --namespace \${k8sNamespace} cjoc-0 -- sed -i \\&quot;/&lt;\\\\/access&gt;/i\\\\&lt;entry&gt;&lt;string&gt;\${masterName}&lt;/string&gt;&lt;hudson.util.Secret&gt;\${entrySecret}&lt;/hudson.util.Secret&gt;&lt;/entry&gt;\\&quot;  /var/jenkins_home/jcasc-bundles-store/security.xml&quot;
        }
        container(&apos;utils&apos;) {
          //download CLI client from current master
          sh &quot;curl -O http://teams-\${masterName}/teams-\${masterName}/jnlpJars/jenkins-cli.jar&quot;
          sh &quot;curl -O https://raw.githubusercontent.com/cloudbees-days/cb-core-oc-workshop/master/stopAndStartMaster.groovy&quot;
          sh &quot;sed -i &apos;s#REPLACE_MASTER_NAME#\${masterName}#&apos; stopAndStartMaster.groovy&quot;
          withCredentials([usernamePassword(credentialsId: &apos;cli-username-token&apos;, usernameVariable: &apos;USERNAME&apos;, passwordVariable: &apos;PASSWORD&apos;)]) {
            sh &quot;&quot;&quot;
              alias cli=&apos;java -jar jenkins-cli.jar -s \\&apos;http://cjoc/cjoc/\\&apos; -auth \$USERNAME:\$PASSWORD&apos;
              echo &quot;Restart Master \${masterName}&quot;
              cli groovy = &lt; stopAndStartMaster.groovy
            &quot;&quot;&quot;
          }
        }
      }
    }
  }
}</script>
    <sandbox>true</sandbox>
  </definition>
  <triggers/>
  <disabled>false</disabled>
</flow-definition>
"""

def p = masterFolder.createProjectFromXML(name, new ByteArrayInputStream(configXml.getBytes("UTF-8")));

logger.info("created $name job")

disableScript.createNewFile()
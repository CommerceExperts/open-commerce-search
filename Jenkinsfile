pipeline {
  agent {
    label 'maven'
  }
  environment {
    MAVEN_OPTS = '-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=WARN -Dorg.slf4j.simpleLogger.showDateTime=true -Djava.awt.headless=true'
    MAVEN_CLI_OPTS = '--batch-mode --errors --fail-at-end --show-version -U -DdeployAtEnd=false'
    GIT_COMMIT = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
    PROJECT_VERSION = readMavenPom().getVersion()
    JAVA_HOME = '/usr/lib/jvm/adoptium-17-jdk-hotspot/'
  }

  stages {
    stage('test') {
      steps {
        withMaven(mavenSettingsConfig: '67c40a88-505a-4f78-94a3-d879cc1a29f6') {
          sh 'mvn $MAVEN_CLI_OPTS clean test -P !sync-openapi-spec -pl !integration-tests'
        }
      }
    } // end test

    stage('build packages') {
      steps {
        withMaven(mavenSettingsConfig: '67c40a88-505a-4f78-94a3-d879cc1a29f6') {
          sh "mvn $MAVEN_CLI_OPTS install -DskipTests=true -P !sync-openapi-spec,dockerize -pl !integration-tests"
        }
      }
    } // end build packages

    stage('integration tests') {
      steps {
        withMaven(mavenSettingsConfig: '67c40a88-505a-4f78-94a3-d879cc1a29f6') {
          sh "mvn $MAVEN_CLI_OPTS integration-test -pl integration-tests"
        }
      }
    } // end integration tests

    stage('deploy') {
      when {
        allOf {
          branch 'master'
          not { expression { PROJECT_VERSION ==~ /.*-SNAPSHOT/ } }
        }
      }
      steps {
        withMaven(mavenSettingsConfig: '67c40a88-505a-4f78-94a3-d879cc1a29f6') {
          sh "mvn $MAVEN_CLI_OPTS deploy -DskipTests=true -P dockerize,!sync-openapi-spec"
          sh "mvn $MAVEN_CLI_OPTS deploy -DskipTests=true -pl indexer-service,search-service -P searchhub,dockerize"
          dir('suggest-service-parent/') {
            sh "mvn $MAVEN_CLI_OPTS deploy -DskipTests=true -pl suggest-service -P searchhub,dockerize"
          }
        }
      }
    } // end deploy

    stage('docs') {
      when {
        branch 'master'
      }
      steps {
        withMaven(mavenSettingsConfig: '67c40a88-505a-4f78-94a3-d879cc1a29f6') {
          // regenerate java docs
          sh "mvn $MAVEN_CLI_OPTS javadoc:aggregate"
          sh "rm -rf docs/apidocs && mv -v target/site/apidocs docs/"

          // stopped 'regenerate openapi docs' because the output are partially broken markdown files
          // instead the same steps can be done to do a manual update picking the relevant changes only 
          //sh "mvn $MAVEN_CLI_OPTS process-sources -pl open-commerce-search-api -P sync-openapi-spec"
          //sh 'docker run --rm -v "${PWD}:/local" openapitools/openapi-generator-cli:v7.9.0 generate -i /local/open-commerce-search-api/src/main/resources/openapi.yaml -g markdown -o /local/docs/openapi/'
          //sh 'sed -i "1,2d" docs/openapi/README.md' // remove first two lines with unnecessary header
          //sh 'mv docs/openapi/README.md docs/openapi/index.md'
          //sh "grep -RFl 'README.md' docs/openapi/* | xargs -L1 sed -i 's/README.md/index.md/g'"

          // commit + push changes
          withCredentials([usernamePassword(credentialsId: 'github-cxp-bot-credentials', passwordVariable: 'PASSWORD', usernameVariable: 'USERNAME')]) {
            sh 'git config credential.helper store'
            sh 'echo "https://$USERNAME:$PASSWORD@github.com" > ~/.git-credentials'
            sh 'git add open-commerce-search-api/src/main/resources/openapi.yaml' 
            sh 'git add docs/*'
            sh 'git commit -m "Update docs" && git push --force origin HEAD:docs || echo "docs unchanged"'
            sh 'rm ~/.git-credentials'
          }
        }
        
      }
    }
  }
  post {
    always {
      cleanWs()
    }
  }
}

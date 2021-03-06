pipeline {
  agent {
    label 'maven'
  }
  environment {
    MAVEN_OPTS = '-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=WARN -Dorg.slf4j.simpleLogger.showDateTime=true -Djava.awt.headless=true'
    MAVEN_CLI_OPTS = '--batch-mode --errors --fail-at-end --show-version -U -DdeployAtEnd=false'
    GIT_COMMIT = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
    PROJECT_VERSION = readMavenPom().getVersion()
    JAVA_HOME = '/usr/lib/jvm/adoptopenjdk-8-hotspot-amd64/'
  }

  stages {
    stage('test') {
      steps {
        withMaven(mavenSettingsConfig: '67c40a88-505a-4f78-94a3-d879cc1a29f6') {
          sh 'mvn $MAVEN_CLI_OPTS clean test -P !sync-openapi-spec'
        }
      }
    } // end test

    stage('build packages') {
      steps {
        withMaven(mavenSettingsConfig: '67c40a88-505a-4f78-94a3-d879cc1a29f6') {
          sh "mvn $MAVEN_CLI_OPTS install -DskipTests=true -P !sync-openapi-spec"
        }
      }
    } // end build packages

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
        }
      }
    } // end deploy
  }
  post {
    always {
      cleanWs()
    }
  }
}

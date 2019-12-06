pipeline {
	agent {
		label 'maven'
	}
	environment {
		MAVEN_OPTS = '-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=WARN -Dorg.slf4j.simpleLogger.showDateTime=true -Djava.awt.headless=true'
		MAVEN_CLI_OPTS = '--batch-mode --errors --fail-at-end --show-version -DinstallAtEnd=true -DdeployAtEnd=true -U'
		GIT_COMMIT = sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
	}

	stages {
		stage('test') {
			steps {
				withMaven(mavenSettingsConfig: '67c40a88-505a-4f78-94a3-d879cc1a29f6') {
					sh 'mvn $MAVEN_CLI_OPTS clean test'
				}
			}
		} // end test

		stage('build packages') {
			steps {
				withMaven(mavenSettingsConfig: '67c40a88-505a-4f78-94a3-d879cc1a29f6') {
					sh "mvn $MAVEN_CLI_OPTS install -DskipTests=true"
				}
			}
		} // end build packages

		stage('deploy') {
			steps {
				withMaven(mavenSettingsConfig: '67c40a88-505a-4f78-94a3-d879cc1a29f6') {
					sh "mvn $MAVEN_CLI_OPTS deploy -DskipTests=true"
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

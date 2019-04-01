@Library('migrationTest')
import be.belfius.Maven
import be.belfius.UCD
import be.belfius.Sonar
import be.belfius.PodTemplates


import groovy.json.JsonOutput

def version = 'NONE'
def realVersion = 'NONE'
def component=null
def label = "mavenpod-${UUID.randomUUID().toString()}"

podTemplate(label: label, yaml: """
apiVersion: v1
kind: Pod
metadata:
  labels:
    some-label: some-label-value
spec:
  containers:
  - name: busybox
    image: busybox
    command:
    - cat
    tty: true
  - name: maven
    image: maven    
    command:
    - cat
    tty: true
    volumeMounts:
      - name: secret-volume
        mountPath: /root/.ssh/
      - name: configmap-volume
        mountPath: /root/.m2/settings.xml
        subPath: settings.xml
      - name: mvn-data
        mountPath: /root/.m2/repository/
  volumes:
  - name: secret-volume
    secret:
      secretName: ssh-key-secret
      defaultMode: 256
  - name: configmap-volume
    configMap:
      name: mvn-settings
  - name: mvn-data
    persistentVolumeClaim:
      claimName: maven-slave-pvc
"""
) {

  node(label) {

      withFolderProperties{
        component=env.UCD_component
      }

      stage('Checkout') {
        checkout scm
      }

      stage('Build & Push to Nexus') {
        version = Maven.deploySnapshot(this)
        echo version        
      } 	    

      stage('SonarQube analysis') {
        Sonar.analyse(this)	
      }

      stage('Import version in UCD'){
        container("busybox"){
        sh "hostname"
        }
      }

  }
      stage("Verify Sonar Quality Gate"){
        Sonar.verifyQualityGate(this, component, version)					
      }            
} 

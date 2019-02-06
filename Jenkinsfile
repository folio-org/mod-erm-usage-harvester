buildMvn {
  publishModDescriptor = 'yes'
  runLintRamlCop = 'yes'
  publishAPI = 'yes'
  mvnDeploy = 'yes'

  doDocker = {
    buildJavaDocker {
      publishMaster = 'yes'
      healthChk = 'no'
      healthChkCmd = 'curl -sS --fail -o /dev/null  http://localhost:8081/apidocs/ || exit 1'
    }
  }
}

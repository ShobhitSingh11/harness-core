package io.harness.serializer.spring;

import io.harness.cdng.artifact.bean.ArtifactSpecWrapper;
import io.harness.cdng.artifact.bean.DockerArtifactOutcome;
import io.harness.cdng.artifact.bean.yaml.ArtifactListConfig;
import io.harness.cdng.artifact.bean.yaml.ArtifactOverrideSets;
import io.harness.cdng.artifact.bean.yaml.DockerHubArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.GcrArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.SidecarArtifact;
import io.harness.cdng.artifact.steps.ArtifactStepParameters;
import io.harness.cdng.environment.steps.EnvironmentStepParameters;
import io.harness.cdng.environment.yaml.EnvironmentYaml;
import io.harness.cdng.infra.InfrastructureDef;
import io.harness.cdng.infra.beans.InfraUseFromStage;
import io.harness.cdng.infra.beans.K8sDirectInfraMapping;
import io.harness.cdng.infra.steps.InfraStepParameters;
import io.harness.cdng.infra.yaml.K8SDirectInfrastructure;
import io.harness.cdng.k8s.K8sRollingOutcome;
import io.harness.cdng.k8s.K8sRollingRollbackStepInfo;
import io.harness.cdng.k8s.K8sRollingRollbackStepParameters;
import io.harness.cdng.k8s.K8sRollingStepInfo;
import io.harness.cdng.k8s.K8sRollingStepParameters;
import io.harness.cdng.k8s.K8sRollingStepPassThroughData;
import io.harness.cdng.manifest.state.ManifestStepParameters;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.ManifestConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOverrideSets;
import io.harness.cdng.manifest.yaml.StoreConfigWrapper;
import io.harness.cdng.manifest.yaml.kinds.K8sManifest;
import io.harness.cdng.manifest.yaml.kinds.ValuesManifest;
import io.harness.cdng.pipeline.DeploymentStage;
import io.harness.cdng.pipeline.NgPipeline;
import io.harness.cdng.pipeline.PipelineInfrastructure;
import io.harness.cdng.pipeline.beans.CDPipelineSetupParameters;
import io.harness.cdng.pipeline.beans.DeploymentStageStepParameters;
import io.harness.cdng.pipeline.stepinfo.HttpStepInfo;
import io.harness.cdng.pipeline.stepinfo.ShellScriptStepInfo;
import io.harness.cdng.service.beans.KubernetesServiceSpec;
import io.harness.cdng.service.beans.ServiceDefinition;
import io.harness.cdng.service.beans.ServiceOutcome;
import io.harness.cdng.service.beans.ServiceUseFromStage;
import io.harness.cdng.service.beans.StageOverridesConfig;
import io.harness.cdng.service.steps.ServiceStepParameters;
import io.harness.cdng.tasks.manifestFetch.step.ManifestFetchOutcome;
import io.harness.cdng.tasks.manifestFetch.step.ManifestFetchParameters;
import io.harness.cdng.variables.StageVariables;
import io.harness.spring.AliasRegistrar;

import java.util.Map;

public class NgAliasRegistrar implements AliasRegistrar {
  @Override
  public void register(Map<String, Class<?>> orchestrationElements) {
    orchestrationElements.put("artifactListConfig", ArtifactListConfig.class);
    orchestrationElements.put("artifactStepParameters", ArtifactStepParameters.class);
    orchestrationElements.put("cdPipelineSetupParameters", CDPipelineSetupParameters.class);
    orchestrationElements.put("deploymentStageStepParameters", DeploymentStageStepParameters.class);
    orchestrationElements.put("dockerArtifactOutcome", DockerArtifactOutcome.class);
    orchestrationElements.put("environmentYaml", EnvironmentYaml.class);
    orchestrationElements.put("httpStepInfo", HttpStepInfo.class);
    orchestrationElements.put("k8sDirectInfraMapping", K8sDirectInfraMapping.class);
    orchestrationElements.put("k8sDirectInfrastructure", K8SDirectInfrastructure.class);
    orchestrationElements.put("k8sRollingOutcome", K8sRollingOutcome.class);
    orchestrationElements.put("k8sRollingStepPassThroughData", K8sRollingStepPassThroughData.class);
    orchestrationElements.put("k8sRollingRollback", K8sRollingRollbackStepInfo.class);
    orchestrationElements.put("k8sRollingRollbackStepParameters", K8sRollingRollbackStepParameters.class);
    orchestrationElements.put("k8sRollingStepInfo", K8sRollingStepInfo.class);
    orchestrationElements.put("k8sRollingStepParameters", K8sRollingStepParameters.class);
    orchestrationElements.put("manifestFetchOutcome", ManifestFetchOutcome.class);
    orchestrationElements.put("manifestFetchParameters", ManifestFetchParameters.class);
    orchestrationElements.put("manifestOutcome", ManifestOutcome.class);
    orchestrationElements.put("manifestStepParameters", ManifestStepParameters.class);
    orchestrationElements.put("pipelineInfrastructure", PipelineInfrastructure.class);
    orchestrationElements.put("serviceOutcome", ServiceOutcome.class);
    orchestrationElements.put("serviceStepParameters", ServiceStepParameters.class);
    orchestrationElements.put("shellScriptStepInfo", ShellScriptStepInfo.class);
    orchestrationElements.put("stageOverridesConfig", StageOverridesConfig.class);
    orchestrationElements.put("kubernetesServiceSpec", KubernetesServiceSpec.class);
    orchestrationElements.put("io.harness.cdng.pipeline.beans.entities.pipelinesNG", NgPipeline.class);
    orchestrationElements.put("stageVariables", StageVariables.class);
    orchestrationElements.put("k8sManifest", K8sManifest.class);
    orchestrationElements.put("infrastructureDef", InfrastructureDef.class);
    orchestrationElements.put("serviceOutcome_artifactsOutcome", ServiceOutcome.ArtifactsOutcome.class);
    orchestrationElements.put("deploymentStage", DeploymentStage.class);
    orchestrationElements.put("dockerHubArtifactConfig", DockerHubArtifactConfig.class);
    orchestrationElements.put("gcrArtifactConfig", GcrArtifactConfig.class);
    orchestrationElements.put("gitStore", GitStore.class);
    orchestrationElements.put("manifestConfig", ManifestConfig.class);
    orchestrationElements.put("valuesManifest", ValuesManifest.class);
    orchestrationElements.put("serviceUseFromStage", ServiceUseFromStage.class);
    orchestrationElements.put("serviceUseFromStage_overrides", ServiceUseFromStage.Overrides.class);
    orchestrationElements.put("infraUseFromStage_overrides", InfraUseFromStage.Overrides.class);
    orchestrationElements.put("infraUseFromStage", InfraUseFromStage.class);
    orchestrationElements.put("environmentStepParameters", EnvironmentStepParameters.class);
    orchestrationElements.put("infraStepParameters", InfraStepParameters.class);
    orchestrationElements.put("manifestOverrideSets", ManifestOverrideSets.class);
    orchestrationElements.put("artifactOverrideSets", ArtifactOverrideSets.class);
    orchestrationElements.put("sidecarArtifact", SidecarArtifact.class);
    orchestrationElements.put("artifactSpecWrapper", ArtifactSpecWrapper.class);
    orchestrationElements.put("storeConfigWrapper", StoreConfigWrapper.class);
    orchestrationElements.put("serviceDefinition", ServiceDefinition.class);
  }
}

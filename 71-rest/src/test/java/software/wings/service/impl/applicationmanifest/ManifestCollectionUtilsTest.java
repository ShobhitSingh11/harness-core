package software.wings.service.impl.applicationmanifest;

import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.rule.OwnerRule.PRABU;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static software.wings.beans.SettingAttribute.Builder.aSettingAttribute;
import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;
import static software.wings.utils.WingsTestConstants.APP_ID;
import static software.wings.utils.WingsTestConstants.MANIFEST_ID;
import static software.wings.utils.WingsTestConstants.PERPETUAL_TASK_ID;
import static software.wings.utils.WingsTestConstants.SERVICE_ID;
import static software.wings.utils.WingsTestConstants.SETTING_ID;
import static software.wings.utils.WingsTestConstants.SETTING_NAME;
import static software.wings.utils.WingsTestConstants.UUID;

import com.google.inject.Inject;

import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.TaskData;
import io.harness.delegate.task.manifests.request.ManifestCollectionParams;
import io.harness.exception.InvalidRequestException;
import io.harness.k8s.model.HelmVersion;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import software.wings.WingsBaseTest;
import software.wings.annotation.EncryptableSetting;
import software.wings.beans.HelmChartConfig;
import software.wings.beans.Service;
import software.wings.beans.TaskType;
import software.wings.beans.appmanifest.ApplicationManifest;
import software.wings.beans.appmanifest.HelmChart;
import software.wings.beans.appmanifest.StoreType;
import software.wings.beans.settings.helm.HelmRepoConfigValidationTaskParams;
import software.wings.beans.settings.helm.HttpHelmRepoConfig;
import software.wings.helpers.ext.helm.request.HelmChartCollectionParams;
import software.wings.helpers.ext.helm.request.HelmChartConfigParams;
import software.wings.service.intfc.ApplicationManifestService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.SettingsService;
import software.wings.service.intfc.applicationmanifest.HelmChartService;
import software.wings.service.intfc.security.SecretManager;

import java.util.Arrays;
import java.util.List;

public class ManifestCollectionUtilsTest extends WingsBaseTest {
  private static final String CHART_NAME = "CHART_NAME";
  private static final String ENCRYPT = "ENCRYPT";

  @Inject @InjectMocks private ManifestCollectionUtils manifestCollectionUtils;
  @Mock private ApplicationManifestService applicationManifestService;
  @Mock private HelmChartService helmChartService;
  @Mock private SecretManager secretManager;
  @Mock private ServiceResourceService serviceResourceService;
  @Mock private SettingsService settingsService;

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetTaskParams() {
    ApplicationManifest applicationManifest = generateApplicationManifest();
    Service service = Service.builder().uuid(SERVICE_ID).helmVersion(HelmVersion.V3).build();
    List<HelmChart> publishedCharts =
        Arrays.asList(generateHelmChartWithVersion("1"), generateHelmChartWithVersion("2"));

    doReturn(applicationManifest).when(applicationManifestService).getById(APP_ID, MANIFEST_ID);
    doReturn(service).when(serviceResourceService).get(APP_ID, SERVICE_ID);
    doReturn(publishedCharts).when(helmChartService).listHelmChartsForAppManifest(ACCOUNT_ID, MANIFEST_ID);
    doReturn(aSettingAttribute().withUuid(SETTING_ID).withValue(HttpHelmRepoConfig.builder().build()).build())
        .when(settingsService)
        .get(any());
    doReturn(Arrays.asList(EncryptedDataDetail.builder().fieldName(ENCRYPT).build()))
        .when(secretManager)
        .getEncryptionDetails(any(EncryptableSetting.class), anyString(), anyString());

    ManifestCollectionParams collectionParams = manifestCollectionUtils.prepareCollectTaskParams(MANIFEST_ID, APP_ID);
    assertThat(collectionParams.getAppManifestId()).isEqualTo(MANIFEST_ID);
    assertThat(collectionParams.getAccountId()).isEqualTo(ACCOUNT_ID);
    assertThat(collectionParams.getPublishedVersions()).containsExactlyInAnyOrder("1", "2");

    assertThat(collectionParams).isInstanceOf(HelmChartCollectionParams.class);
    HelmChartCollectionParams helmChartCollectionParams = (HelmChartCollectionParams) collectionParams;
    assertThat(helmChartCollectionParams.getHelmChartConfigParams()).isNotNull();

    HelmChartConfigParams helmChartConfigParams = helmChartCollectionParams.getHelmChartConfigParams();
    assertThat(helmChartConfigParams.getChartName()).isEqualTo(CHART_NAME);
    assertThat(helmChartConfigParams.getEncryptedDataDetails().get(0).getFieldName()).isEqualTo(ENCRYPT);
    assertThat(helmChartConfigParams.getHelmVersion()).isEqualTo(HelmVersion.V3);
    assertThat(helmChartConfigParams.getRepoName()).isEqualTo(convertBase64UuidToCanonicalForm(SETTING_ID));
    assertThat(helmChartConfigParams.getHelmRepoConfig()).isInstanceOf(HttpHelmRepoConfig.class);
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldThrowExceptionForInvalidAppManifest() {
    ApplicationManifest applicationManifest = generateApplicationManifest();
    applicationManifest.setPollForChanges(null);

    assertThatThrownBy(() -> manifestCollectionUtils.prepareCollectTaskParams(MANIFEST_ID, APP_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Collection not configured for app manifest");

    doReturn(applicationManifest).when(applicationManifestService).getById(APP_ID, MANIFEST_ID);

    assertThatThrownBy(() -> manifestCollectionUtils.prepareCollectTaskParams(MANIFEST_ID, APP_ID))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Collection not configured for app manifest");
  }

  @Test
  @Owner(developers = PRABU)
  @Category(UnitTests.class)
  public void shouldGetValidationTask() {
    ApplicationManifest applicationManifest = generateApplicationManifest();
    Service service = Service.builder().uuid(SERVICE_ID).helmVersion(HelmVersion.V3).build();
    List<HelmChart> publishedCharts =
        Arrays.asList(generateHelmChartWithVersion("1"), generateHelmChartWithVersion("2"));

    doReturn(applicationManifest).when(applicationManifestService).getById(APP_ID, MANIFEST_ID);
    doReturn(service).when(serviceResourceService).get(APP_ID, SERVICE_ID);
    doReturn(publishedCharts).when(helmChartService).listHelmChartsForAppManifest(ACCOUNT_ID, MANIFEST_ID);
    doReturn(aSettingAttribute()
                 .withUuid(SETTING_ID)
                 .withName(SETTING_NAME)
                 .withValue(HttpHelmRepoConfig.builder().build())
                 .build())
        .when(settingsService)
        .get(any());
    doReturn(Arrays.asList(EncryptedDataDetail.builder().fieldName(ENCRYPT).build()))
        .when(secretManager)
        .getEncryptionDetails(any(EncryptableSetting.class), anyString(), anyString());

    DelegateTask delegateTask = manifestCollectionUtils.buildValidationTask(MANIFEST_ID, APP_ID);
    assertThat(delegateTask.getAccountId()).isEqualTo(ACCOUNT_ID);
    TaskData taskData = delegateTask.getData();
    assertThat(taskData.getTaskType()).isEqualTo(TaskType.HELM_REPO_CONFIG_VALIDATION.name());
    assertThat(taskData.getTimeout()).isEqualTo(120000);

    HelmRepoConfigValidationTaskParams taskParams = (HelmRepoConfigValidationTaskParams) taskData.getParameters()[0];
    assertThat(taskParams.getAppId()).isEqualTo(APP_ID);
    assertThat(taskParams.getEncryptedDataDetails().get(0).getFieldName()).isEqualTo(ENCRYPT);
    assertThat(taskParams.getRepoDisplayName()).isEqualTo(SETTING_NAME);
    assertThat(taskParams.getHelmRepoConfig()).isInstanceOf(HttpHelmRepoConfig.class);
  }

  private ApplicationManifest generateApplicationManifest() {
    ApplicationManifest appManifest =
        ApplicationManifest.builder()
            .accountId(ACCOUNT_ID)
            .storeType(StoreType.Remote)
            .serviceId(SERVICE_ID)
            .helmChartConfig(HelmChartConfig.builder().chartName(CHART_NAME).connectorId(SETTING_ID).build())
            .perpetualTaskId(PERPETUAL_TASK_ID)
            .failedAttempts(5)
            .pollForChanges(true)
            .build();
    appManifest.setUuid(MANIFEST_ID);
    return appManifest;
  }

  private HelmChart generateHelmChartWithVersion(String version) {
    return HelmChart.builder()
        .accountId(ACCOUNT_ID)
        .appId(APP_ID)
        .uuid(UUID + version)
        .applicationManifestId(MANIFEST_ID)
        .serviceId(SERVICE_ID)
        .version(version)
        .build();
  }
}
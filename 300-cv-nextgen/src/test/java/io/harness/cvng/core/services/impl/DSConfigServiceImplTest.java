package io.harness.cvng.core.services.impl;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.DEEPAK;
import static io.harness.rule.OwnerRule.KAMAL;
import static io.harness.rule.OwnerRule.RAGHU;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.harness.CvNextGenTest;
import io.harness.category.element.UnitTests;
import io.harness.cvng.beans.DataSourceType;
import io.harness.cvng.client.NextGenService;
import io.harness.cvng.core.beans.AppDynamicsDSConfig;
import io.harness.cvng.core.beans.AppDynamicsDSConfig.AppdynamicsAppConfig;
import io.harness.cvng.core.beans.AppDynamicsDSConfig.ServiceMapping;
import io.harness.cvng.core.beans.DSConfig;
import io.harness.cvng.core.beans.MonitoringSourceDTO;
import io.harness.cvng.core.entities.MetricPack;
import io.harness.cvng.core.services.api.AppDynamicsService;
import io.harness.cvng.core.services.api.CVConfigTransformer;
import io.harness.cvng.core.services.api.DSConfigService;
import io.harness.cvng.core.services.api.MonitoringSourceImportStatusCreator;
import io.harness.rule.Owner;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class DSConfigServiceImplTest extends CvNextGenTest {
  @Inject DSConfigService dsConfigService;
  @Mock NextGenService nextGenService;
  @Mock AppDynamicsService appDynamicsService;
  @Mock private Injector injector;
  @Inject AppDynamicsCVConfigTransformer appDynamicsCVConfigTransformer;
  private String accountId;
  private String connectorIdentifier;
  private String productName;
  private String projectIdentifier;
  private String orgIdentifier;

  @Before
  public void setup() throws IllegalAccessException {
    this.accountId = generateUuid();
    this.connectorIdentifier = generateUuid();
    this.productName = "Application monitoring";
    this.projectIdentifier = "projectIdentifier";
    this.orgIdentifier = "orgIdentifier";
    FieldUtils.writeField(dsConfigService, "nextGenService", nextGenService, true);
    FieldUtils.writeField(dsConfigService, "injector", injector, true);
    when(injector.getInstance(
             Key.get(MonitoringSourceImportStatusCreator.class, Names.named(DataSourceType.APP_DYNAMICS.name()))))
        .thenReturn(appDynamicsService);
    when(injector.getInstance(Key.get(CVConfigTransformer.class, Names.named(DataSourceType.APP_DYNAMICS.name()))))
        .thenReturn(appDynamicsCVConfigTransformer);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testUpsert_withSingleConfig() {
    DSConfig dsConfig = createAppDynamicsDataSourceCVConfig("appd application name", "monitoringSourceIdentifier");
    dsConfigService.upsert(dsConfig);
    List<? extends DSConfig> dataSourceCVConfigs =
        dsConfigService.list(accountId, connectorIdentifier, dsConfig.getProductName());
    assertThat(dataSourceCVConfigs).hasSize(1);
    AppDynamicsDSConfig appDynamicsDataSourceCVConfig = (AppDynamicsDSConfig) dataSourceCVConfigs.get(0);
    assertThat(appDynamicsDataSourceCVConfig).isEqualTo(dsConfig);
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testList_multiple() {
    AppDynamicsDSConfig dataSourceCVConfig = createAppDynamicsDataSourceCVConfig("app", "monitoringSourceIdentifier1");
    dsConfigService.upsert(dataSourceCVConfig);
    dataSourceCVConfig = createAppDynamicsDataSourceCVConfig("app", "monitoringSourceIdentifier2");
    dsConfigService.upsert(dataSourceCVConfig);
    List<? extends DSConfig> dataSourceCVConfigs =
        dsConfigService.list(accountId, connectorIdentifier, dataSourceCVConfig.getProductName());
    assertThat(dataSourceCVConfigs).hasSize(2);
    Set<String> identifiers = new HashSet<>();
    dataSourceCVConfigs.forEach(dsConfig -> identifiers.add(dsConfig.getIdentifier()));
    assertThat(identifiers).isEqualTo(Sets.newHashSet("monitoringSourceIdentifier1", "monitoringSourceIdentifier2"));
  }

  @Test
  @Owner(developers = KAMAL)
  @Category(UnitTests.class)
  public void testDelete() {
    AppDynamicsDSConfig dataSourceCVConfig =
        createAppDynamicsDataSourceCVConfig("appd application name", "monitoringSourceIdentifier");
    dataSourceCVConfig.setIdentifier("app1");
    dsConfigService.upsert(dataSourceCVConfig);
    assertThat(dsConfigService.list(accountId, connectorIdentifier, productName)).isNotEmpty();
    dsConfigService.delete(accountId, orgIdentifier, projectIdentifier, dataSourceCVConfig.getIdentifier());
    assertThat(dsConfigService.list(accountId, orgIdentifier, projectIdentifier)).isEmpty();
  }

  private AppDynamicsDSConfig createAppDynamicsDataSourceCVConfig(String appName, String monitoringSourceIdentifier) {
    AppDynamicsDSConfig appDynamicsDSConfig = new AppDynamicsDSConfig();
    appDynamicsDSConfig.setIdentifier(monitoringSourceIdentifier);
    appDynamicsDSConfig.setMonitoringSourceName(generateUuid());
    appDynamicsDSConfig.setConnectorIdentifier(connectorIdentifier);
    appDynamicsDSConfig.setProductName(productName);
    appDynamicsDSConfig.setAccountId(accountId);
    appDynamicsDSConfig.setOrgIdentifier(orgIdentifier);
    appDynamicsDSConfig.setProjectIdentifier(projectIdentifier);
    appDynamicsDSConfig.setAppConfigs(Lists.newArrayList(
        AppdynamicsAppConfig.builder()
            .applicationName(appName)
            .envIdentifier("harnessProd")
            .metricPacks(Sets.newHashSet(
                MetricPack.builder().accountId(accountId).identifier("appd performance metric pack").build()))
            .serviceMappings(Sets.newHashSet(
                ServiceMapping.builder().serviceIdentifier("harness-manager").tierName("manager").build(),
                ServiceMapping.builder().serviceIdentifier("harness-qa").tierName("manager-qa").build()))
            .build()));

    return appDynamicsDSConfig;
  }

  @Test
  @Owner(developers = RAGHU)
  @Category(UnitTests.class)
  public void testGetMonitoringSources() {
    AppDynamicsDSConfig dataSourceCVConfig1 =
        createAppDynamicsDataSourceCVConfig("appd application name 1", "monitoringSourceIdentifier 1");
    dsConfigService.upsert(dataSourceCVConfig1);
    AppDynamicsDSConfig dataSourceCVConfig2 =
        createAppDynamicsDataSourceCVConfig("appd application name 2", "monitoringSourceIdentifier 2");
    dsConfigService.upsert(dataSourceCVConfig2);
    DSConfig dsConfig = dsConfigService.getMonitoringSource(
        accountId, orgIdentifier, projectIdentifier, "monitoringSourceIdentifier 1");
    assertThat(dsConfig).isNotNull();
    assertThat(dsConfig.getIdentifier()).isEqualTo(dataSourceCVConfig1.getIdentifier());
    assertThat(dsConfig.getMonitoringSourceName()).isEqualTo(dataSourceCVConfig1.getMonitoringSourceName());
    assertThat(dsConfig.getAccountId()).isEqualTo(accountId);
    assertThat(dsConfig.getOrgIdentifier()).isEqualTo(orgIdentifier);
    assertThat(dsConfig.getProjectIdentifier()).isEqualTo(projectIdentifier);
    assertThat(dsConfig.getProductName()).isEqualTo(productName);
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testListMonitoringSources_checkThatNumberOfElementsAreCorrects() {
    AppDynamicsDSConfig dataSourceCVConfig1 =
        createAppDynamicsDataSourceCVConfig("appd application name 1", "monitoringSourceIdentifier 1");
    dsConfigService.upsert(dataSourceCVConfig1);
    AppDynamicsDSConfig dataSourceCVConfig2 =
        createAppDynamicsDataSourceCVConfig("appd application name 2", "monitoringSourceIdentifier 2");
    dsConfigService.upsert(dataSourceCVConfig2);
    AppDynamicsDSConfig dataSourceCVConfig3 =
        createAppDynamicsDataSourceCVConfig("appd application name 3", "monitoringSourceIdentifier 3");
    dsConfigService.upsert(dataSourceCVConfig3);
    AppDynamicsDSConfig dataSourceCVConfig4 =
        createAppDynamicsDataSourceCVConfig("appd application name 4", "monitoringSourceIdentifier 3");
    dsConfigService.upsert(dataSourceCVConfig4);
    List<MonitoringSourceDTO> monitoringSourceDTOS =
        dsConfigService.listMonitoringSources(accountId, orgIdentifier, projectIdentifier, 10, 0, null).getContent();
    assertThat(monitoringSourceDTOS.size()).isEqualTo(3);
    assertThat(monitoringSourceDTOS.get(0).getMonitoringSourceIdentifier()).isEqualTo("monitoringSourceIdentifier 3");
    assertThat(monitoringSourceDTOS.get(1).getMonitoringSourceIdentifier()).isEqualTo("monitoringSourceIdentifier 2");
    assertThat(monitoringSourceDTOS.get(2).getMonitoringSourceIdentifier()).isEqualTo("monitoringSourceIdentifier 1");
  }

  @Test
  @Owner(developers = DEEPAK)
  @Category(UnitTests.class)
  public void testListMonitoringSources_checkThatNumberOfServicesAreCorrectForAppdynamics() {
    AppDynamicsDSConfig dataSourceCVConfig1 =
        createAppDynamicsDataSourceCVConfig("appd application name 1", "monitoringSourceIdentifier 1");
    dataSourceCVConfig1.getAppConfigs().forEach(appdynamicsAppConfig -> {
      appdynamicsAppConfig.setApplicationName("application 1");
      appdynamicsAppConfig.setEnvIdentifier("env 1");
      appdynamicsAppConfig.setServiceMappings(
          Sets.newHashSet(ServiceMapping.builder().serviceIdentifier("harness-manager").tierName("manager").build(),
              ServiceMapping.builder().serviceIdentifier("harness-qa").tierName("manager-qa").build()));
    });
    dsConfigService.upsert(dataSourceCVConfig1);

    List<MonitoringSourceDTO> monitoringSourceDTOS =
        dsConfigService.listMonitoringSources(accountId, orgIdentifier, projectIdentifier, 10, 0, null).getContent();
    assertThat(monitoringSourceDTOS.size()).isEqualTo(1);
    MonitoringSourceDTO monitoringSourceDTO = monitoringSourceDTOS.get(0);
    assertThat(monitoringSourceDTO.getMonitoringSourceIdentifier()).isEqualTo("monitoringSourceIdentifier 1");
    assertThat(monitoringSourceDTO.getNumberOfServices()).isEqualTo(2);

    AppDynamicsDSConfig dataSourceCVConfig2 =
        createAppDynamicsDataSourceCVConfig("appd application name 2", "monitoringSourceIdentifier 1");
    dataSourceCVConfig2.getAppConfigs().forEach(appdynamicsAppConfig -> {
      appdynamicsAppConfig.setApplicationName("application 2");
      appdynamicsAppConfig.setEnvIdentifier("env 2");
      appdynamicsAppConfig.setServiceMappings(
          Sets.newHashSet(ServiceMapping.builder().serviceIdentifier("harness-manager-dev").tierName("manager").build(),
              ServiceMapping.builder().serviceIdentifier("harness-qa").tierName("manager-qa").build()));
    });
    dsConfigService.upsert(dataSourceCVConfig2);

    monitoringSourceDTOS =
        dsConfigService.listMonitoringSources(accountId, orgIdentifier, projectIdentifier, 10, 0, null).getContent();
    assertThat(monitoringSourceDTOS.size()).isEqualTo(1);
    monitoringSourceDTO = monitoringSourceDTOS.get(0);
    assertThat(monitoringSourceDTO.getMonitoringSourceIdentifier()).isEqualTo("monitoringSourceIdentifier 1");
    assertThat(monitoringSourceDTO.getNumberOfServices()).isEqualTo(2);

    AppDynamicsDSConfig dataSourceCVConfig3 =
        createAppDynamicsDataSourceCVConfig("appd application name 1", "monitoringSourceIdentifier 2");
    dataSourceCVConfig3.getAppConfigs().forEach(appdynamicsAppConfig -> {
      appdynamicsAppConfig.setApplicationName("application 1");
      appdynamicsAppConfig.setEnvIdentifier("env 1");
      appdynamicsAppConfig.setServiceMappings(
          Sets.newHashSet(ServiceMapping.builder().serviceIdentifier("harness-manager").tierName("manager").build(),
              ServiceMapping.builder().serviceIdentifier("harness-qa").tierName("manager-qa").build()));
    });
    dsConfigService.upsert(dataSourceCVConfig3);

    monitoringSourceDTOS =
        dsConfigService.listMonitoringSources(accountId, orgIdentifier, projectIdentifier, 10, 0, null).getContent();
    assertThat(monitoringSourceDTOS.size()).isEqualTo(2);
    monitoringSourceDTOS.forEach(monitoringSource -> assertThat(monitoringSource.getNumberOfServices()).isEqualTo(2));
  }
}

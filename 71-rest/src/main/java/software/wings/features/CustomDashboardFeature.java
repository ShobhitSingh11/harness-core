package software.wings.features;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.dashboard.DashboardSettings;
import io.harness.dashboard.DashboardSettingsService;
import lombok.extern.slf4j.Slf4j;
import software.wings.beans.EntityType;
import software.wings.beans.FeatureName;
import software.wings.features.api.AbstractPremiumFeature;
import software.wings.features.api.ComplianceByRemovingUsage;
import software.wings.features.api.FeatureRestrictions;
import software.wings.features.api.Usage;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.security.SecretManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author marklu on 9/6/19
 */
@Singleton
@Slf4j
public class CustomDashboardFeature extends AbstractPremiumFeature implements ComplianceByRemovingUsage {
  public static final String FEATURE_NAME = FeatureName.CUSTOM_DASHBOARD.name();

  @Inject private DashboardSettingsService dashboardSettingsService;

  @Inject
  public CustomDashboardFeature(AccountService accountService, FeatureRestrictions featureRestrictions,
      DashboardSettingsService dashboardSettingsService) {
    super(accountService, featureRestrictions);
    this.dashboardSettingsService = dashboardSettingsService;
  }

  @Override
  public boolean removeUsageForCompliance(String accountId, String targetAccountType) {
    // UI will disable to access of Custom Dashboards. No physical removal will be attempted.
    return true;
  }

  @Override
  public boolean isAvailable(String accountType) {
    boolean result = (boolean) getRestrictions(accountType).getOrDefault("available", true);

    logger.info("Is custom dashboard usage allowed for account type {}? {}", accountType, result);
    return result;
  }

  @Override
  public boolean isBeingUsed(String accountId) {
    return getDashboardSettingsByAccountId(accountId).size() > 0;
  }

  @Override
  public Collection<Usage> getDisallowedUsages(String accountId, String targetAccountType) {
    return getDashboardSettingsByAccountId(accountId)
        .stream()
        .map(CustomDashboardFeature::toUsage)
        .collect(Collectors.toList());
  }

  @Override
  public String getFeatureName() {
    return FEATURE_NAME;
  }

  private static Usage toUsage(DashboardSettings dashboardSettings) {
    return Usage.builder()
        .entityId(dashboardSettings.getUuid())
        .entityType(EntityType.CUSTOM_DASHBOARD.name())
        .entityName(dashboardSettings.getName())
        .build();
  }

  private List<DashboardSettings> getDashboardSettingsByAccountId(String accountId) {
    List<DashboardSettings> dashboardSettings = new ArrayList<>();

    PageResponse<DashboardSettings> pageResponse;
    int limit = 1000;
    int offset = 0;
    do {
      PageRequest<DashboardSettings> pageRequest = aPageRequest()
                                                       .withLimit(String.valueOf(limit))
                                                       .withOffset(String.valueOf(offset))
                                                       .addFilter(SecretManager.ACCOUNT_ID_KEY, Operator.EQ, accountId)
                                                       .build();
      pageResponse = dashboardSettingsService.getDashboardSettingSummary(accountId, pageRequest);
      dashboardSettings.addAll(pageResponse.getResponse());
      offset += limit;
      logger.info("Total custom dashboards available in account {} is {}, loaded {} custom dashboards so far...",
          accountId, pageResponse.getTotal(), dashboardSettings.size());
    } while (dashboardSettings.size() < pageResponse.getTotal());

    return dashboardSettings;
  }
}

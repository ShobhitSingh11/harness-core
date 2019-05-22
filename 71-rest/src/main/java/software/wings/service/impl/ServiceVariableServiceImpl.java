package software.wings.service.impl;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.CollectionUtils.isEqualCollection;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.exception.WingsException.USER;
import static io.harness.expression.SecretString.SECRET_MASK;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static org.mongodb.morphia.mapping.Mapper.ID_KEY;
import static software.wings.beans.Base.APP_ID_KEY;
import static software.wings.beans.EntityType.SERVICE;
import static software.wings.beans.Environment.GLOBAL_ENV_ID;
import static software.wings.beans.ServiceVariable.Type.ENCRYPTED_TEXT;
import static software.wings.service.intfc.ServiceVariableService.EncryptedFieldMode.MASKED;
import static software.wings.service.intfc.ServiceVariableService.EncryptedFieldMode.OBTAIN_VALUE;
import static software.wings.utils.Validator.duplicateCheck;
import static software.wings.utils.Validator.notNullCheck;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.persistence.HIterator;
import io.harness.validation.Create;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.validator.constraints.NotEmpty;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;
import ru.vyarus.guice.validator.group.annotation.ValidationGroups;
import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.Event;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;
import software.wings.beans.ServiceVariable;
import software.wings.beans.ServiceVariable.ServiceVariableKeys;
import software.wings.beans.ServiceVariable.Type;
import software.wings.dl.WingsPersistence;
import software.wings.security.encryption.EncryptedData;
import software.wings.security.encryption.EncryptedData.EncryptedDataKeys;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.EnvironmentService;
import software.wings.service.intfc.ServiceResourceService;
import software.wings.service.intfc.ServiceVariableService;
import software.wings.service.intfc.yaml.YamlPushService;
import software.wings.settings.SettingValue.SettingVariableTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import javax.validation.Valid;
import javax.validation.executable.ValidateOnExecution;

/**
 * Created by peeyushaggarwal on 9/14/16.
 */
@Singleton
@ValidateOnExecution
@Slf4j
public class ServiceVariableServiceImpl implements ServiceVariableService {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private EnvironmentService environmentService;
  @Inject private ServiceResourceService serviceResourceService;
  @Inject private AppService appService;
  @Inject private ExecutorService executorService;
  @Inject private YamlPushService yamlPushService;
  @Inject private AuditServiceHelper auditServiceHelper;

  @Override
  public PageResponse<ServiceVariable> list(PageRequest<ServiceVariable> request) {
    return list(request, OBTAIN_VALUE);
  }

  @Override
  public PageResponse<ServiceVariable> list(
      PageRequest<ServiceVariable> request, EncryptedFieldMode encryptedFieldMode) {
    PageResponse<ServiceVariable> response = wingsPersistence.query(ServiceVariable.class, request);
    if (encryptedFieldMode == MASKED) {
      response.getResponse().forEach(
          serviceVariable -> processEncryptedServiceVariable(encryptedFieldMode, serviceVariable));
    }
    return response;
  }

  @Override
  @ValidationGroups(Create.class)
  public ServiceVariable save(@Valid ServiceVariable serviceVariable) {
    checkValidEncryptedReference(serviceVariable);
    return save(serviceVariable, false);
  }

  @Override
  @ValidationGroups(Create.class)
  public ServiceVariable save(@Valid ServiceVariable serviceVariable, boolean syncFromGit) {
    if (!asList(SERVICE, EntityType.SERVICE_TEMPLATE, EntityType.ENVIRONMENT, EntityType.HOST)
             .contains(serviceVariable.getEntityType())) {
      throw new WingsException(INVALID_ARGUMENT)
          .addParam("args", "Service setting not supported for entityType " + serviceVariable.getEntityType());
    }

    ServiceVariable newServiceVariable = duplicateCheck(
        () -> wingsPersistence.saveAndGet(ServiceVariable.class, serviceVariable), "name", serviceVariable.getName());

    if (newServiceVariable == null) {
      return null;
    }

    executorService.submit(() -> addAndSaveSearchTags(serviceVariable));

    // Type.UPDATE is intentionally passed. Don't change this.
    String accountId = appService.getAccountIdByAppId(serviceVariable.getAppId());
    yamlPushService.pushYamlChangeSet(
        accountId, newServiceVariable, newServiceVariable, Event.Type.UPDATE, syncFromGit, false);

    return newServiceVariable;
  }

  @Override
  public ServiceVariable get(@NotEmpty String appId, @NotEmpty String settingId) {
    return get(appId, settingId, OBTAIN_VALUE);
  }

  @Override
  public ServiceVariable get(String appId, String settingId, EncryptedFieldMode encryptedFieldMode) {
    ServiceVariable serviceVariable = wingsPersistence.getWithAppId(ServiceVariable.class, appId, settingId);
    notNullCheck("ServiceVariable is null for id: " + settingId, serviceVariable);
    if (encryptedFieldMode == MASKED) {
      processEncryptedServiceVariable(encryptedFieldMode, serviceVariable);
    }
    return serviceVariable;
  }

  @Override
  public ServiceVariable update(@Valid ServiceVariable serviceVariable) {
    return update(serviceVariable, false);
  }

  @Override
  public ServiceVariable update(@Valid ServiceVariable serviceVariable, boolean syncFromGit) {
    checkValidEncryptedReference(serviceVariable);
    ServiceVariable savedServiceVariable = get(serviceVariable.getAppId(), serviceVariable.getUuid());
    executorService.submit(
        () -> removeSearchTagsIfNecessary(savedServiceVariable, String.valueOf(serviceVariable.getValue())));
    notNullCheck("Service variable", savedServiceVariable);
    if (serviceVariable.getName() != null) {
      if (savedServiceVariable.getName() != null && !savedServiceVariable.getName().equals(serviceVariable.getName())) {
        throw new InvalidRequestException(format("Service variable name can not be changed."));
      }
    }

    Map<String, Object> updateMap = new HashMap<>();
    if (isNotEmpty(serviceVariable.getValue())) {
      updateMap.put(ServiceVariableKeys.value, serviceVariable.getValue());
    }
    if (serviceVariable.getType() != null) {
      updateMap.put(ServiceVariableKeys.type, serviceVariable.getType());
    }
    if (isNotEmpty(updateMap)) {
      wingsPersistence.updateFields(ServiceVariable.class, serviceVariable.getUuid(), updateMap);
      ServiceVariable updatedServiceVariable = get(serviceVariable.getAppId(), serviceVariable.getUuid());
      if (updatedServiceVariable == null) {
        return null;
      }

      String accountId = appService.getAccountIdByAppId(serviceVariable.getAppId());
      yamlPushService.pushYamlChangeSet(
          accountId, serviceVariable, updatedServiceVariable, Event.Type.UPDATE, syncFromGit, false);
      serviceVariable.setEncryptedValue(String.valueOf(serviceVariable.getValue()));
      executorService.submit(() -> addAndSaveSearchTags(serviceVariable));
      return updatedServiceVariable;
    }
    return serviceVariable;
  }

  @Override
  public void delete(@NotEmpty String appId, @NotEmpty String settingId) {
    delete(appId, settingId, false);
  }

  @Override
  public void delete(@NotEmpty String appId, @NotEmpty String settingId, boolean syncFromGit) {
    ServiceVariable serviceVariable = get(appId, settingId);
    if (serviceVariable == null) {
      return;
    }

    executorService.submit(() -> removeSearchTagsIfNecessary(serviceVariable, null));
    Query<ServiceVariable> query = wingsPersistence.createQuery(ServiceVariable.class)
                                       .filter(ServiceVariableKeys.parentServiceVariableId, settingId)
                                       .filter(APP_ID_KEY, appId);
    List<ServiceVariable> modified = query.asList();
    UpdateOperations<ServiceVariable> updateOperations = wingsPersistence.createUpdateOperations(ServiceVariable.class)
                                                             .unset(ServiceVariableKeys.parentServiceVariableId);
    wingsPersistence.update(query, updateOperations);

    wingsPersistence.delete(
        wingsPersistence.createQuery(ServiceVariable.class).filter(APP_ID_KEY, appId).filter(ID_KEY, settingId));

    // Type.UPDATE is intentionally passed. Don't change this.
    String accountId = appService.getAccountIdByAppId(serviceVariable.getAppId());
    yamlPushService.pushYamlChangeSet(
        accountId, serviceVariable, serviceVariable, Event.Type.UPDATE, syncFromGit, false);
    if (isNotEmpty(modified)) {
      for (ServiceVariable serviceVariable1 : modified) {
        accountId = appService.getAccountIdByAppId(serviceVariable1.getAppId());
        yamlPushService.pushYamlChangeSet(
            accountId, serviceVariable1, serviceVariable1, Event.Type.UPDATE, syncFromGit, false);
      }
    }
  }

  @Override
  public List<ServiceVariable> getServiceVariablesForEntity(
      String appId, String entityId, EncryptedFieldMode encryptedFieldMode) {
    PageRequest<ServiceVariable> request = aPageRequest()
                                               .addFilter(ServiceVariableKeys.appId, Operator.EQ, appId)
                                               .addFilter(ServiceVariableKeys.entityId, Operator.EQ, entityId)
                                               .build();
    List<ServiceVariable> variables = wingsPersistence.query(ServiceVariable.class, request).getResponse();
    variables.forEach(serviceVariable -> processEncryptedServiceVariable(encryptedFieldMode, serviceVariable));
    return variables;
  }

  @Override
  public List<ServiceVariable> getServiceVariablesByTemplate(
      String appId, String envId, ServiceTemplate serviceTemplate, EncryptedFieldMode encryptedFieldMode) {
    PageRequest<ServiceVariable> request =
        aPageRequest()
            .addFilter(ServiceVariableKeys.appId, Operator.EQ, appId)
            .addFilter(ServiceVariableKeys.envId, Operator.EQ, envId)
            .addFilter(ServiceVariableKeys.templateId, Operator.EQ, serviceTemplate.getUuid())
            .build();
    List<ServiceVariable> variables = wingsPersistence.query(ServiceVariable.class, request).getResponse();
    variables.forEach(serviceVariable -> processEncryptedServiceVariable(encryptedFieldMode, serviceVariable));
    return variables;
  }

  @Override
  public void deleteByTemplateId(String appId, String serviceTemplateId) {
    wingsPersistence.delete(wingsPersistence.createQuery(ServiceVariable.class)
                                .filter(ServiceVariableKeys.appId, appId)
                                .filter(ServiceVariableKeys.templateId, serviceTemplateId));
  }

  @Override
  public void pruneByService(String appId, String entityId) {
    List<ServiceVariable> serviceVariables = wingsPersistence.createQuery(ServiceVariable.class)
                                                 .filter(ServiceVariableKeys.appId, appId)
                                                 .filter(ServiceVariableKeys.entityId, entityId)
                                                 .asList();
    for (ServiceVariable serviceVariable : serviceVariables) {
      if (wingsPersistence.delete(serviceVariable)) {
        auditServiceHelper.reportDeleteForAuditing(appId, serviceVariable);
      }
    }
  }

  private void processEncryptedServiceVariable(EncryptedFieldMode encryptedFieldMode, ServiceVariable serviceVariable) {
    if (serviceVariable.getType() == ENCRYPTED_TEXT) {
      if (encryptedFieldMode == MASKED) {
        serviceVariable.setValue(SECRET_MASK.toCharArray());
      }
      EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, serviceVariable.getEncryptedValue());
      notNullCheck("no encrypted ref found for " + serviceVariable.getUuid(), encryptedData, USER);
      serviceVariable.setSecretTextName(encryptedData.getName());
    }
  }

  @Override
  public int updateSearchTagsForSecrets(String accountId) {
    int updateRecords = 0;
    Query<EncryptedData> query = wingsPersistence.createQuery(EncryptedData.class)
                                     .filter(EncryptedDataKeys.accountId, accountId)
                                     .filter(EncryptedDataKeys.type, SettingVariableTypes.SECRET_TEXT);
    try (HIterator<EncryptedData> records = new HIterator<>(query.fetch())) {
      while (records.hasNext()) {
        EncryptedData savedData = records.next();
        List<String> appIds = savedData.getAppIds() == null ? null : new ArrayList<>(savedData.getAppIds());
        List<String> serviceIds = savedData.getServiceIds() == null ? null : new ArrayList<>(savedData.getServiceIds());
        List<String> envIds = savedData.getEnvIds() == null ? null : new ArrayList<>(savedData.getEnvIds());
        Set<String> serviceVariableIds =
            savedData.getServiceVariableIds() == null ? null : new HashSet<>(savedData.getServiceVariableIds());

        savedData.clearSearchTags();

        if (!isEmpty(savedData.getParentIds())) {
          savedData.getParentIds().forEach(serviceVariableId -> {
            ServiceVariable serviceVariable = wingsPersistence.get(ServiceVariable.class, serviceVariableId);
            if (serviceVariable == null) {
              return;
            }
            addSearchTags(serviceVariable, savedData);
          });
          if (!isEqualCollection(appIds, savedData.getAppIds())
              || !isEqualCollection(serviceIds, savedData.getServiceIds())
              || !isEqualCollection(envIds, savedData.getEnvIds())
              || !isEqualCollection(serviceVariableIds, savedData.getServiceVariableIds())) {
            logger.info("updating {}", savedData.getUuid());
            wingsPersistence.save(savedData);
            updateRecords++;
          }
        }
      }
    }
    return updateRecords;
  }

  private void addAndSaveSearchTags(ServiceVariable serviceVariable) {
    if (serviceVariable.getType() != Type.ENCRYPTED_TEXT) {
      return;
    }

    EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, serviceVariable.getEncryptedValue());
    addSearchTags(serviceVariable, encryptedData);

    wingsPersistence.save(encryptedData);
  }

  private void addSearchTags(ServiceVariable serviceVariable, EncryptedData encryptedData) {
    Preconditions.checkNotNull(encryptedData, "could not find encrypted reference for " + serviceVariable);

    String appId = serviceVariable.getAppId();
    try {
      Application app = appService.get(appId);
      encryptedData.addApplication(appId, app.getName());
    } catch (Exception e) {
      logger.info("application {} does not exists", appId);
    }

    String envId = serviceVariable.getEnvId();

    String serviceId;
    switch (serviceVariable.getEntityType()) {
      case SERVICE:
        serviceId = serviceVariable.getEntityId();
        encryptedData.addServiceVariable(serviceVariable.getUuid(), serviceVariable.getName());
        break;

      case SERVICE_TEMPLATE:
        ServiceTemplate serviceTemplate = wingsPersistence.get(ServiceTemplate.class, serviceVariable.getEntityId());
        serviceId = serviceTemplate.getServiceId();
        encryptedData.addServiceVariable(serviceTemplate.getUuid(), serviceTemplate.getName());
        break;

      case ENVIRONMENT:
        envId = serviceVariable.getEntityId();
        serviceId = null;
        break;

      default:
        return;
    }

    if (!isEmpty(envId) && !envId.equals(GLOBAL_ENV_ID)) {
      Environment environment = environmentService.get(appId, envId);
      if (environment != null) {
        encryptedData.addEnvironment(envId, environment.getName());
      }
    }

    if (!isEmpty(serviceId)) {
      Service service = serviceResourceService.get(appId, serviceId);
      if (service != null) {
        encryptedData.addService(serviceId, service.getName());
      }
    }
  }

  private void removeSearchTagsIfNecessary(ServiceVariable savedServiceVariable, String newValue) {
    Type savedServiceVariableType = savedServiceVariable.getType();
    if (savedServiceVariableType != ENCRYPTED_TEXT) {
      return;
    }

    if (savedServiceVariable.getEncryptedValue().equals(newValue)) {
      return;
    }

    EncryptedData encryptedData = wingsPersistence.get(EncryptedData.class, savedServiceVariable.getEncryptedValue());
    Preconditions.checkNotNull(encryptedData, "could not find encrypted reference for " + savedServiceVariable);

    String appId = savedServiceVariable.getAppId();
    encryptedData.removeApplication(appId, appService.get(appId).getName());
    String envId = savedServiceVariable.getEnvId();

    String serviceId;
    switch (savedServiceVariable.getEntityType()) {
      case SERVICE:
        serviceId = savedServiceVariable.getEntityId();
        encryptedData.removeServiceVariable(savedServiceVariable.getUuid(), savedServiceVariable.getName());
        break;

      case SERVICE_TEMPLATE:
        ServiceTemplate serviceTemplate =
            wingsPersistence.get(ServiceTemplate.class, savedServiceVariable.getEntityId());
        serviceId = serviceTemplate.getServiceId();
        encryptedData.removeServiceVariable(serviceTemplate.getUuid(), serviceTemplate.getName());
        break;

      case ENVIRONMENT:
        envId = savedServiceVariable.getEntityId();
        serviceId = null;
        break;

      default:
        throw new IllegalArgumentException("Invalid entity type " + savedServiceVariable.getEntityType());
    }

    if (!isEmpty(serviceId)) {
      encryptedData.removeService(serviceId, serviceResourceService.get(appId, serviceId).getName());
    }

    if (!isEmpty(envId) && !envId.equals(GLOBAL_ENV_ID)) {
      Environment environment = environmentService.get(appId, envId);
      encryptedData.removeEnvironment(envId, environment.getName());
    }

    wingsPersistence.save(encryptedData);
  }

  private void checkValidEncryptedReference(@Valid ServiceVariable serviceVariable) {
    if (serviceVariable.getType().equals(ENCRYPTED_TEXT)) {
      Preconditions.checkNotNull(serviceVariable.getValue(), "value passed is null for " + serviceVariable);
      EncryptedData encryptedData =
          wingsPersistence.get(EncryptedData.class, String.valueOf(serviceVariable.getValue()));
      if (encryptedData == null) {
        throw new WingsException(
            INVALID_ARGUMENT, "No secret text with id " + new String(serviceVariable.getValue()) + " exists", USER)
            .addParam("args", "No secret text with given name exists. Please select one from the drop down.");
      }
    }
  }
}

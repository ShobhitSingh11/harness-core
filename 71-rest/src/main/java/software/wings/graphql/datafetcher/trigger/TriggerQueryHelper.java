package software.wings.graphql.datafetcher.trigger;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.FieldEnd;
import org.mongodb.morphia.query.Query;
import software.wings.beans.EntityType;
import software.wings.beans.trigger.Trigger;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;
import software.wings.graphql.schema.type.aggregation.trigger.QLTriggerFilter;
import software.wings.graphql.schema.type.aggregation.trigger.QLTriggerTagFilter;
import software.wings.graphql.schema.type.aggregation.trigger.QLTriggerTagType;

import java.util.List;
import java.util.Set;

/**
 * @author rktummala on 07/12/19
 */
@Singleton
@Slf4j
public class TriggerQueryHelper {
  @Inject protected DataFetcherUtils utils;
  @Inject protected TagHelper tagHelper;

  public void setQuery(List<QLTriggerFilter> filters, Query query, String accountId) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<Trigger>> field;

      if (filter.getApplication() != null) {
        field = query.field("appId");
        QLIdFilter applicationFilter = filter.getApplication();
        utils.setIdFilter(field, applicationFilter);
      }

      if (filter.getTrigger() != null) {
        field = query.field("_id");
        QLIdFilter triggerFilter = filter.getTrigger();
        utils.setIdFilter(field, triggerFilter);
      }

      if (filter.getTag() != null) {
        QLTriggerTagFilter triggerTagFilter = filter.getTag();
        List<QLTagInput> tags = triggerTagFilter.getTags();
        Set<String> entityIds =
            tagHelper.getEntityIdsFromTags(accountId, tags, getEntityType(triggerTagFilter.getEntityType()));
        switch (triggerTagFilter.getEntityType()) {
          case APPLICATION:
            query.field("appId").in(entityIds);
            break;
          default:
            logger.error("EntityType {} not supported in query", triggerTagFilter.getEntityType());
            throw new InvalidRequestException("Error while compiling query", WingsException.USER);
        }
      }
    });
  }

  public EntityType getEntityType(QLTriggerTagType entityType) {
    switch (entityType) {
      case APPLICATION:
        return EntityType.APPLICATION;
      default:
        logger.error("Unsupported entity type {} for tag ", entityType);
        throw new InvalidRequestException("Unsupported entity type " + entityType);
    }
  }
}

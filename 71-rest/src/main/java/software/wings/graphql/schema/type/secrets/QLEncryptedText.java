package software.wings.graphql.schema.type.secrets;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldNameConstants;

@Value
@Builder
@FieldNameConstants(innerTypeName = "QLEncryptedTextKeys")
public class QLEncryptedText implements QLSecret {
  private String secretManagerId;
  private String name;
  private QLSecretType secretType;
  private String id;
  private QLUsageScope usageScope;
  private boolean scopedToAccount;
  private boolean inheritScopesFromSM;
}

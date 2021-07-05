package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static javax.ws.rs.Priorities.AUTHENTICATION;
import static org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion.$2A;

import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.common.beans.ApiKeyType;
import io.harness.ng.core.dto.TokenDTO;
import io.harness.remote.client.NGRestUtils;
import io.harness.security.dto.Principal;
import io.harness.security.dto.ServiceAccountPrincipal;
import io.harness.security.dto.UserPrincipal;
import io.harness.token.remote.TokenClient;

import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Priority;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@OwnedBy(PL)
@Singleton
@Priority(AUTHENTICATION)
@Slf4j
public class NextGenAuthenticationFilter extends JWTAuthenticationFilter {
  public static final String X_API_KEY = "X-Api-Key";
  private static final String delimiter = "\\.";

  private TokenClient tokenClient;

  public NextGenAuthenticationFilter(Predicate<Pair<ResourceInfo, ContainerRequestContext>> predicate,
      Map<String, JWTTokenHandler> serviceToJWTTokenHandlerMapping, Map<String, String> serviceToSecretMapping,
      @Named("PRIVILEGED") TokenClient tokenClient) {
    super(predicate, serviceToJWTTokenHandlerMapping, serviceToSecretMapping);
    this.tokenClient = tokenClient;
  }

  @Override
  public void filter(ContainerRequestContext containerRequestContext) {
    if (!super.testRequestPredicate(containerRequestContext)) {
      // Predicate testing failed with the current request context
      return;
    }

    Optional<String> apiKeyOptional = getApiKeyFromHeaders(containerRequestContext);
    if (apiKeyOptional.isPresent()) {
      String apiKey = apiKeyOptional.get();
      log.info("Found an API key in request {}", apiKey);
      String[] splitToken = apiKey.split(delimiter);
      if (EmptyPredicate.isNotEmpty(splitToken)) {
        TokenDTO tokenDTO = NGRestUtils.getResponse(tokenClient.getToken(splitToken[0]));
        String rawPassword = apiKey.replaceAll(splitToken[0] + delimiter, "");
        if (tokenDTO != null) {
          if (!new BCryptPasswordEncoder($2A, 10).matches(rawPassword, tokenDTO.getEncodedPassword())) {
            throw new InvalidRequestException("Could not find the API token in Harness");
          }
          if (!tokenDTO.isValid()) {
            throw new InvalidRequestException("Incoming API token " + tokenDTO.getName() + " has expired");
          }
          Principal principal = null;
          if (tokenDTO.getApiKeyType() == ApiKeyType.SERVICE_ACCOUNT) {
            principal = new ServiceAccountPrincipal(tokenDTO.getParentIdentifier());
          }
          if (tokenDTO.getApiKeyType() == ApiKeyType.USER) {
            principal = new UserPrincipal(tokenDTO.getParentIdentifier(), tokenDTO.getEmail(), tokenDTO.getUsername(),
                tokenDTO.getAccountIdentifier());
          }
          SecurityContextBuilder.setContext(principal);
          SourcePrincipalContextBuilder.setSourcePrincipal(principal);
        } else {
          throw new InvalidRequestException("Could not find the API token in Harness");
        }
      } else {
        throw new InvalidRequestException("Invalid API token");
      }
    } else {
      super.filter(containerRequestContext);
    }
  }

  private Optional<String> getApiKeyFromHeaders(ContainerRequestContext containerRequestContext) {
    String apiKey = containerRequestContext.getHeaderString(X_API_KEY);
    return StringUtils.isEmpty(apiKey) ? Optional.empty() : Optional.of(apiKey);
  }
}
package org.folio.service.settings;

import static org.folio.utils.CacheUtils.buildAsyncCache;

import org.folio.rest.core.RestClient;
import org.folio.rest.core.model.RequestContext;
import org.springframework.beans.factory.annotation.Value;

import com.github.benmanes.caffeine.cache.AsyncCache;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Log4j2
@RequiredArgsConstructor
public class CommonSettingsService {

  public static final String HOST_ADDRESS_ENDPOINT = "/host-address";
  public static final String HOST_ADDRESS_KEY_FIELD = "key";
  public static final String HOST_ADDRESS_VALUE_FIELD = "value";

  @Value("${finance.storage.cache.settings.expiration.time.seconds:30}")
  private long cacheExpirationTime;

  private final RestClient restClient;
  private AsyncCache<String, String> hostAddressCache;

  @PostConstruct
  void init() {
    var context = Vertx.currentContext();
    hostAddressCache = buildAsyncCache(context, cacheExpirationTime);
  }

  public Future<String> getHostAddress(RequestContext requestContext) {
    return Future.fromCompletionStage(hostAddressCache.get(HOST_ADDRESS_KEY_FIELD, (key, executor) ->
      fetchHostAddress(requestContext).toCompletionStage().toCompletableFuture()));
  }

  private Future<String> fetchHostAddress(RequestContext requestContext) {
    return restClient.get(HOST_ADDRESS_ENDPOINT, requestContext)
      .map(jsonObject -> jsonObject.getString(HOST_ADDRESS_VALUE_FIELD))
      .onFailure(t -> log.error("Failed to fetch host address from settings service", t));
  }

}

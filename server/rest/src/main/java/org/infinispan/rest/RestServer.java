package org.infinispan.rest;

import java.io.IOException;
import java.nio.file.Path;

import org.infinispan.counter.EmbeddedCounterManagerFactory;
import org.infinispan.counter.impl.manager.EmbeddedCounterManager;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.rest.cachemanager.RestCacheManager;
import org.infinispan.rest.configuration.AuthenticationConfiguration;
import org.infinispan.rest.configuration.RestServerConfiguration;
import org.infinispan.rest.framework.ResourceManager;
import org.infinispan.rest.framework.RestDispatcher;
import org.infinispan.rest.framework.impl.ResourceManagerImpl;
import org.infinispan.rest.framework.impl.RestDispatcherImpl;
import org.infinispan.rest.resources.CacheManagerResource;
import org.infinispan.rest.resources.CacheResource;
import org.infinispan.rest.resources.CacheResourceV2;
import org.infinispan.rest.resources.ClusterResource;
import org.infinispan.rest.resources.CounterResource;
import org.infinispan.rest.resources.MetricsResource;
import org.infinispan.rest.resources.ServerResource;
import org.infinispan.rest.resources.SplashResource;
import org.infinispan.rest.resources.StaticFileResource;
import org.infinispan.rest.resources.XSiteResource;
import org.infinispan.server.core.AbstractProtocolServer;
import org.infinispan.server.core.ServerManagement;
import org.infinispan.server.core.transport.NettyInitializers;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOutboundHandler;

/**
 * REST Protocol Server.
 *
 * @author Sebastian Łaskawiec
 */
public class RestServer extends AbstractProtocolServer<RestServerConfiguration> {
   private ServerManagement server;
   private RestDispatcher restDispatcher;
   private RestCacheManager<Object> restCacheManager;
   private InvocationHelper invocationHelper;

   public RestServer() {
      super("REST");
   }

   @Override
   public ChannelOutboundHandler getEncoder() {
      return null;
   }

   @Override
   public ChannelInboundHandler getDecoder() {
      return null;
   }

   @Override
   public ChannelInitializer<Channel> getInitializer() {
      return new NettyInitializers(getRestChannelInitializer());
   }

   /**
    * Returns Netty Channel Initializer for REST.
    *
    * @return Netty Channel Initializer for REST.
    */
   public RestChannelInitializer getRestChannelInitializer() {
      return new RestChannelInitializer(this, transport);
   }

   RestDispatcher getRestDispatcher() {
      return restDispatcher;
   }

   @Override
   public void stop() {
      if (restCacheManager != null) {
         restCacheManager.stop();
      }
      AuthenticationConfiguration auth = configuration.authentication();
      if (auth.enabled()) {
         try {
            auth.authenticator().close();
         } catch (IOException e) {
         }
      }
      super.stop();
   }

   public void setServer(ServerManagement server) {
      this.server = server;
   }

   @Override
   protected void startInternal(RestServerConfiguration configuration, EmbeddedCacheManager cacheManager) {
      this.configuration = configuration;
      AuthenticationConfiguration auth = configuration.authentication();
      if (auth.enabled()) {
         auth.authenticator().init(this);
      }
      super.startInternal(configuration, cacheManager);
      restCacheManager = new RestCacheManager<>(cacheManager, this::isCacheIgnored);

      invocationHelper = new InvocationHelper(restCacheManager,
            (EmbeddedCounterManager) EmbeddedCounterManagerFactory.asCounterManager(cacheManager),
            configuration, server, getExecutor());

      String restContext = configuration.contextPath();
      String rootContext = "/";
      ResourceManager resourceManager = new ResourceManagerImpl();
      resourceManager.registerResource(rootContext, new SplashResource());
      resourceManager.registerResource(restContext, new CacheResource(invocationHelper));
      resourceManager.registerResource(restContext, new CacheResourceV2(invocationHelper));
      resourceManager.registerResource(restContext, new CounterResource(invocationHelper));
      resourceManager.registerResource(restContext, new CacheManagerResource(invocationHelper));
      resourceManager.registerResource(restContext, new XSiteResource(invocationHelper));
      resourceManager.registerResource(rootContext, new MetricsResource());
      Path staticResources = configuration.staticResources();
      if (staticResources != null) {
         resourceManager.registerResource(rootContext, new StaticFileResource(staticResources, "static"));
      }
      if (server != null) {
         resourceManager.registerResource(restContext, new ServerResource(invocationHelper));
         resourceManager.registerResource(restContext, new ClusterResource(invocationHelper));
      }
      this.restDispatcher = new RestDispatcherImpl(resourceManager);
   }
}

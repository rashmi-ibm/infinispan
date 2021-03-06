package org.infinispan.client.hotrod;

import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.killRemoteCacheManager;
import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.killServers;
import static org.infinispan.client.hotrod.test.HotRodClientTestingUtil.remoteCacheObjectName;
import static org.infinispan.server.hotrod.test.HotRodTestingUtil.hotRodCacheConfiguration;
import static org.testng.AssertJUnit.assertEquals;

import java.lang.invoke.MethodHandles;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.infinispan.client.hotrod.configuration.NearCacheMode;
import org.infinispan.client.hotrod.test.HotRodClientTestingUtil;
import org.infinispan.commons.jmx.MBeanServerLookup;
import org.infinispan.commons.jmx.MBeanServerLookupProvider;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.manager.CacheContainer;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.server.hotrod.HotRodServer;
import org.infinispan.test.AbstractInfinispanTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

@Test(groups = "functional", testName = "client.hotrod.HotRodClientNearCacheJmxTest")
public class HotRodClientNearCacheJmxTest extends AbstractInfinispanTest {
   private HotRodServer hotrodServer;
   private CacheContainer cacheContainer;
   private RemoteCacheManager[] rcms;
   private RemoteCache<String, String>[] remoteCaches;

   private final MBeanServerLookup mBeanServerLookup = MBeanServerLookupProvider.create();

   @BeforeMethod
   protected void setup() throws Exception {
      ConfigurationBuilder cfg = hotRodCacheConfiguration();
      cfg.jmxStatistics().enable();
      cacheContainer = TestCacheManagerFactory
            .createClusteredCacheManagerEnforceJmxDomain(getClass().getSimpleName(), cfg);

      hotrodServer = HotRodClientTestingUtil.startHotRodServer((EmbeddedCacheManager) cacheContainer);
      rcms = new RemoteCacheManager[2];
      remoteCaches = new RemoteCache[2];
      for (int i = 0; i < 2; i++) {
         rcms[i] = addRemoteCacheManager();
         remoteCaches[i] = rcms[i].getCache();
      }
   }

   private RemoteCacheManager addRemoteCacheManager() {
      org.infinispan.client.hotrod.configuration.ConfigurationBuilder clientBuilder =
            HotRodClientTestingUtil.newRemoteConfigurationBuilder();
      clientBuilder.addServer().host("localhost").port(hotrodServer.getPort());
      clientBuilder.nearCache().mode(NearCacheMode.INVALIDATED).maxEntries(100);
      clientBuilder.statistics().enable()
            .jmxEnable()
            .jmxDomain(MethodHandles.lookup().lookupClass().getSimpleName())
            .mBeanServerLookup(mBeanServerLookup);
      return new RemoteCacheManager(clientBuilder.build());
   }

   @AfterMethod
   void tearDown() {
      TestingUtil.killCacheManagers(cacheContainer);
      for (int i = 0; i < 2; i++) {
         killRemoteCacheManager(rcms[i]);
      }
      killServers(hotrodServer);
   }

   public void testNearRemoteCacheMBean() throws Exception {
      MBeanServer mbeanServer = mBeanServerLookup.getMBeanServer();
      ObjectName objectName = remoteCacheObjectName(rcms[0], "org.infinispan.default");
      remoteCaches[0].get("a"); // miss
      assertEquals(1L, mbeanServer.getAttribute(objectName, "RemoteMisses"));
      assertEquals(1L, mbeanServer.getAttribute(objectName, "NearCacheMisses"));
      assertEquals(0L, mbeanServer.getAttribute(objectName, "NearCacheSize"));
      remoteCaches[0].put("a", "a");
      assertEquals(1L, mbeanServer.getAttribute(objectName, "RemoteStores"));
      assertEquals(0L, mbeanServer.getAttribute(objectName, "NearCacheSize"));
      remoteCaches[0].get("a"); // remote hit
      assertEquals(1L, mbeanServer.getAttribute(objectName, "RemoteHits"));
      assertEquals(2L, mbeanServer.getAttribute(objectName, "NearCacheMisses"));
      assertEquals(0L, mbeanServer.getAttribute(objectName, "NearCacheHits"));
      assertEquals(1L, mbeanServer.getAttribute(objectName, "NearCacheSize"));
      remoteCaches[0].get("a"); // near hit
      assertEquals(1L, mbeanServer.getAttribute(objectName, "RemoteHits"));
      assertEquals(2L, mbeanServer.getAttribute(objectName, "NearCacheMisses"));
      assertEquals(1L, mbeanServer.getAttribute(objectName, "NearCacheHits"));
      assertEquals(1L, mbeanServer.getAttribute(objectName, "NearCacheSize"));
      assertEquals(0L, mbeanServer.getAttribute(objectName, "NearCacheInvalidations"));
      remoteCaches[1].put("a", "b"); // cause an invalidation from the other client
      eventually(() -> ((Long) mbeanServer.getAttribute(objectName, "NearCacheInvalidations")) == 1L, 1000);
   }
}

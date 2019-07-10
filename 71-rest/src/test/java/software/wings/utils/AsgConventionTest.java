package software.wings.utils;

import static org.junit.Assert.assertEquals;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import org.junit.Test;
import org.junit.experimental.categories.Category;

public class AsgConventionTest extends CategoryTest {
  @Test
  @Category(UnitTests.class)
  public void testGetRevisionFromTag() {
    int revision = AsgConvention.getRevisionFromTag("aaa__123");
    assertEquals(123, revision);

    revision = AsgConvention.getRevisionFromTag("aaa__bbb__ccc__123");
    assertEquals(123, revision);

    revision = AsgConvention.getRevisionFromTag("aaabbbccc");
    assertEquals(0, revision);

    revision = AsgConvention.getRevisionFromTag("aaa__bbb__ccc");
    assertEquals(0, revision);

    revision = AsgConvention.getRevisionFromTag(null);
    assertEquals("null string as input doesn't work", 0, revision);
  }

  @Test
  @Category(UnitTests.class)
  public void testGetAsgNamePrefix() {
    String asgNamePrefix = AsgConvention.getAsgNamePrefix("appName", "serviceName", "envName");
    assertEquals("appName__serviceName__envName", asgNamePrefix);

    asgNamePrefix = AsgConvention.getAsgNamePrefix("app&Name", "service+Name", "env*Name");
    assertEquals("app__Name__service__Name__env__Name", asgNamePrefix);

    asgNamePrefix = AsgConvention.getAsgNamePrefix("app/Name", "service.Name", "env'Name");
    assertEquals("app__Name__service__Name__env__Name", asgNamePrefix);

    asgNamePrefix = AsgConvention.getAsgNamePrefix("app$Name", "service Name", "env\"Name");
    assertEquals("app__Name__service__Name__env__Name", asgNamePrefix);

    asgNamePrefix = AsgConvention.getAsgNamePrefix("app$Name", "service|Name", "env\\Name");
    assertEquals("app__Name__service__Name__env__Name", asgNamePrefix);

    asgNamePrefix = AsgConvention.getAsgNamePrefix("appName", null, null);
    assertEquals("appName__null__null", asgNamePrefix);
  }
}

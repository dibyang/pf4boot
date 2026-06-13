package net.xdob.pf4boot.version;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DefaultVersionRangeMatcherTest {

  private final DefaultVersionRangeMatcher matcher = new DefaultVersionRangeMatcher();

  @Test
  public void matchesExactVersion() {
    assertTrue(matcher.matches("1.2.3", "1.2.3").isMatched());
    assertFalse(matcher.matches("1.2.4", "1.2.3").isMatched());
  }

  @Test
  public void matchesInclusiveExclusiveRange() {
    assertTrue(matcher.matches("1.0", "[1.0,2.0)").isMatched());
    assertTrue(matcher.matches("1.5", "[1.0,2.0)").isMatched());
    assertFalse(matcher.matches("2.0", "[1.0,2.0)").isMatched());
  }

  @Test
  public void matchesOpenEndedRange() {
    assertTrue(matcher.matches("2.1", "[2.0,)").isMatched());
    assertTrue(matcher.matches("1.9", "(,2.0]").isMatched());
    assertFalse(matcher.matches("2.1", "(,2.0]").isMatched());
  }

  @Test
  public void rejectsInvalidRange() {
    VersionMatchResult result = matcher.matches("1.0", "[1.0 2.0)");

    assertFalse(result.isValid());
    assertFalse(result.isMatched());
  }

  @Test
  public void comparesQualifiedVersionsDeterministically() {
    assertTrue(matcher.matches("1.0.1", "[1.0.0,1.1.0)").isMatched());
    assertFalse(matcher.matches("1.0-alpha", "[1.0,2.0)").isMatched());
  }
}

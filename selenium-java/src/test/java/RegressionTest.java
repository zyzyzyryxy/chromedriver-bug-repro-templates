/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogEntry;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;

public class RegressionTest {

  private WebDriver driver;

  @BeforeEach
  public void setUp() {
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--headless");
    options.addArguments("--no-sandbox");
    options.setBrowserVersion("stable");

    LoggingPreferences logPrefs = new LoggingPreferences();
    logPrefs.enable(LogType.PERFORMANCE, Level.ALL);
    options.setCapability("goog:loggingPrefs", logPrefs);

    Map<String, Object> perfLogPrefs = new HashMap<>();
    perfLogPrefs.put("traceCategories", "devtools.timeline,blink,cc");
    options.setExperimentalOption("perfLoggingPrefs", perfLogPrefs);

    ChromeDriverService service =
        new ChromeDriverService.Builder()
            .withLogFile(new java.io.File("chromedriver.log"))
            .withVerbose(true)
            .build();

    driver = new ChromeDriver(service, options);
  }

  @AfterEach
  public void tearDown() {
    if (driver != null) {
      driver.quit();
    }
  }

  @Test
  public void verifySetup_shouldBeAbleToNavigateToGoogleCom() {
    // Navigate to a URL
    driver.get("https://www.google.com");
    // Assert that the navigation was successful
    assertEquals("Google", driver.getTitle());
  }

  @Test
  public void ISSUE_REPRODUCTION() throws InterruptedException {
    String fileUrl = Paths.get("src/test/resources/repro.html").toAbsolutePath().toUri().toString();
    driver.get(fileUrl);

    JavascriptExecutor js = (JavascriptExecutor) driver;

    // 1. Setup MutationObserver to check for DOM changes
    js.executeScript(
        "window.mutations = [];" +
        "var observer = new MutationObserver(function(records) {" +
        "   records.forEach(function(record) {" +
        "       window.mutations.push(record.type + ' on ' + record.target.nodeName);" +
        "   });" +
        "});" +
        "observer.observe(document.documentElement, { attributes: true, childList: true, subtree: true, characterData: true });"
    );

    // 2. Setup IntersectionObserver to check for visibility changes
    js.executeScript(
        "window.hiddenDetected = false;" +
        "var other = document.getElementById('other');" +
        "var intObserver = new IntersectionObserver(function(entries) {" +
        "   entries.forEach(function(entry) {" +
        "       if (!entry.isIntersecting) {" +
        "           window.hiddenDetected = true;" +
        "       }" +
        "   });" +
        "});" +
        "intObserver.observe(other);"
    );

    // 3. Setup Event Listeners for blur/focus/visibilitychange
    js.executeScript(
        "window.events = [];" +
        "window.onblur = function() { window.events.push('blur'); };" +
        "window.onfocus = function() { window.events.push('focus'); };" +
        "document.onvisibilitychange = function() { window.events.push('visibilitychange:' + document.visibilityState); };"
    );

    WebElement target = driver.findElement(By.id("target"));
    
    // Clear logs after page load
    driver.manage().logs().get(LogType.PERFORMANCE);

    System.out.println("Starting screenshot capture analysis...");

    long totalLayoutEvents = 0;
    long totalPaintEvents = 0;

    for (int i = 0; i < 10; i++) {
        target.getScreenshotAs(OutputType.BYTES);
        List<LogEntry> entries = driver.manage().logs().get(LogType.PERFORMANCE).getAll();
        
        long layoutCount = entries.stream().filter(e -> e.getMessage().contains("Layout")).count();
        long paintCount = entries.stream().filter(e -> e.getMessage().contains("Paint")).count();
        
        System.out.println("Screenshot " + (i+1) + ": Layout events=" + layoutCount + ", Paint events=" + paintCount);
        
        totalLayoutEvents += layoutCount;
        totalPaintEvents += paintCount;
    }

    // --- Assertions for "Non-Observability" in standard layers ---
    
    // Check DOM Mutations
    List<Object> mutations = (List<Object>) js.executeScript("return window.mutations;");
    assertTrue(mutations.isEmpty(), "Unexpected DOM mutations detected: " + mutations);

    // Check Intersection (Hidden Detection)
    boolean hiddenDetected = (boolean) js.executeScript("return window.hiddenDetected;");
    assertFalse(hiddenDetected, "IntersectionObserver detected element hiding.");

    // Check JS Events
    List<String> events = (List<String>) js.executeScript("return window.events;");
    assertTrue(events.isEmpty(), "Unexpected JS events detected: " + events);

    // --- Assertion for the Bug (Performance Trace) ---
    
    // On a perfectly static page, capturing a screenshot should ideally not trigger 
    // a flurry of layout and paint events. The presence of these events confirms the issue.
    // We expect this assertion to FAIL if the bug is present.
    assertEquals(0, totalLayoutEvents, "Expected no layout events during screenshot capture (Bug Reproduced).");
    assertEquals(0, totalPaintEvents, "Expected no paint events during screenshot capture (Bug Reproduced).");
  }
}

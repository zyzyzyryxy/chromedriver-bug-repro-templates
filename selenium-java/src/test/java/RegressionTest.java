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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class RegressionTest {

  private WebDriver driver;

  @BeforeEach
  public void setUp() {
    ChromeOptions options = new ChromeOptions();
    options.addArguments("--headless");
    options.addArguments("--no-sandbox");

    // By default, the test uses the latest stable Chrome version.
    // Replace the "stable" with the specific browser version if needed,
    // e.g. 'canary', '115' or '144.0.7534.0' for example.
    options.setBrowserVersion("stable");

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
  public void ISSUE_REPRODUCTION() {
    // Quit the driver created in setUp() as we need a specific configuration
    if (driver != null) {
      driver.quit();
    }

    ChromeOptions options = new ChromeOptions();
    options.setExperimentalOption("androidPackage", "com.android.chrome");

    ChromeDriverService service =
        new ChromeDriverService.Builder()
            .withLogFile(new java.io.File("chromedriver_issue.log"))
            .withVerbose(true)
            .build();

    try {
      driver = new ChromeDriver(service, options);

      // Navigate to a simple page with file input
      driver.get("data:text/html,<html><body><input type='file' id='f'></body></html>");

      WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
      WebElement fileInput = wait.until(ExpectedConditions.presenceOfElementLocated(By.id("f")));

      // The bug: Providing an Android path throws "path is not absolute" on Windows
      String androidPath = "/sdcard/Download/test.txt";

      Exception exception = assertThrows(WebDriverException.class, () -> {
        fileInput.sendKeys(androidPath);
      });

      String message = exception.getMessage();
      // We expect the specific error about path not being absolute
      assertTrue(message.contains("path is not absolute"), 
          "Expected 'path is not absolute' error, but got: " + message);

    } catch (Exception e) {
       if (e instanceof RuntimeException && e.getMessage() != null && e.getMessage().startsWith("Expected")) {
           throw e;
       }
       // If initialization failed (e.g. no device), fail the test
       throw new RuntimeException("Test failed with exception: " + e.getMessage(), e);
    }
  }
}

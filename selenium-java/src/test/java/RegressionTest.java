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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;

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
    options.setBrowserVersion("121");

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
    // Print the actual browser version being used
    System.out.println("Browser Version: " + ((ChromeDriver) driver).getCapabilities().getBrowserVersion());

    // 1. Navigate to a page that opens a popup.
    String html = "<html><body><script>" +
            "var win = window.open('about:blank', '_blank', 'width=200,height=200');" +
            "document.body.setAttribute('data-popup-opened', win ? 'true' : 'false');" +
            "</script></body></html>";
    driver.get("data:text/html," + html);

    // 2. Wait a bit for the popup to open.
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    // Check if the window object was null (indication of blocking)
    String popupOpened = driver.findElement(org.openqa.selenium.By.tagName("body")).getAttribute("data-popup-opened");
    System.out.println("Did javascript report window.open success? " + popupOpened);

    // 3. Verify that the popup opened.
    int handleCount = driver.getWindowHandles().size();
    System.out.println("Window handles found: " + handleCount);
    assertEquals(2, handleCount, "Expected 2 windows (main + popup), but found: " + handleCount);
  }
}

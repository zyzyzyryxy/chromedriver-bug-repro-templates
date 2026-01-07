#  Copyright 2025 Google LLC
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import logging
import pytest
import threading
import time
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium.webdriver.chrome.service import Service

# The chrome and chromedriver installation can take some time.
# Give 5 minutes to install everything.
TIMEOUT = 5 * 60 * 1000


@pytest.fixture(scope="module")
def driver():
    # By default, the test uses the latest stable Chrome version.
    # Replace the "stable" with the specific browser version if needed,
    # e.g. 'canary', '115' or '144.0.7534.0' for example.
    browser_version = "stable"

    options = Options()
    options.add_argument("--headless")
    options.add_argument("--no-sandbox")
    options.browser_version = browser_version

    service = Service(service_args=["--log-path=chromedriver.log", "--verbose"])

    driver = webdriver.Chrome(options=options, service=service)

    yield driver

    driver.quit()


@pytest.mark.timeout(TIMEOUT)
def test_should_be_able_to_navigate_to_google_com(driver):
    """This test is intended to verify the setup is correct."""
    driver.get("https://www.google.com")
    logging.info(driver.title)
    assert driver.title == "Google"


@pytest.mark.timeout(TIMEOUT)
def test_issue_reproduction(driver):
    """
    Reproduces Issue 468228355: Chromedriver process crashes when it is processing
    a command for a secondary window that is closing.
    """
    def spam_with_requests(d, stop_evt):
        # Make repeated requests to the target window until stop_event is set
        while not stop_evt.is_set():
            try:
                d.execute_script("return !!window.test;")
            except Exception:
                # when window is closed this will eventually result in an error
                break

    def close_window_while_spamming_with_requests(d, child_window, base_window):
        # Close window after timeout while making repeated requests
        stop_evt = threading.Event()

        d.switch_to.window(child_window)

        # Start thread to make repeated requests to the child window
        request_thread = threading.Thread(
            target=spam_with_requests,
            args=(d, stop_evt),
            daemon=True
        )

        try:
            # Navigate the window before closing
            # Using data URL for empty page since we don't have the server setup from the original test
            logging.info("Navigating window before closing...")
            d.get("data:text/html,<html><body>Empty</body></html>")
            
            # Navigate the window
            logging.info("Starting timeout to close the window...")
            # Use setTimeout to trigger window.close() asynchronously from within the browser
            # This creates the race condition with the external driver commands
            d.execute_script('setTimeout(function() { window.close(); }, 200);')
            
            request_thread.start()
            time.sleep(0.25)
        finally:
            # Ensure request thread stops
            stop_evt.set()
            if request_thread.is_alive():
                request_thread.join(timeout=1.0)
            
            # Switch back to base window to avoid "no such window" errors in main loop
            try:
                d.switch_to.window(base_window)
            except Exception:
                # If base window is somehow gone (shouldn't happen), try to recover handle
                handles = d.window_handles
                if handles:
                    d.switch_to.window(handles[0])

    # Setup the main page with a button to open a new window
    driver.get("data:text/html,"
               "<!doctype html><meta charset='utf-8'><title>repro</title>"
               "<button id='btn'>open and maybe close</button>"
               "<script>"
               "const btn=document.getElementById('btn');"
               "btn.onclick=()=>{"
               "const w=window.open("
               "'about:blank',"
               "'_blank',"
               "'width=400,height=300,left=100,top=100,resizable=yes,"
               "scrollbars=yes,status=yes,menubar=no,"
               "toolbar=no,location=no');};"
               "</script>")

    # The crash doesn't consistently reproduce
    # it generally happens within 10 iterations
    for i in range(20):
        logging.info(f"Test iteration {i+1}/20")

        # Click the button to open a new window
        # We need to ensure the button is there and clickable
        btn = driver.find_element("css selector", "#btn")
        btn.click()

        # Wait for the new window to appear
        # Simple wait loop since we don't have WebDriverWait imported
        # and we want to keep imports minimal if possible, but a short sleep is safer
        time.sleep(0.5)

        # Switch to the newest window
        handles = driver.window_handles
        if len(handles) < 2:
            # Retry waiting if window didn't appear immediately
            time.sleep(1)
            handles = driver.window_handles
            if len(handles) < 2:
                raise RuntimeError("Second window did not open")
        
        base = handles[0]
        child = handles[-1]

        # Close with timeout mechanism
        close_window_while_spamming_with_requests(driver, child, base)

        time.sleep(0.3)


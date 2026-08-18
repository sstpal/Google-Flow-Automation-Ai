let port = browser.runtime.connectNative("browser");

port.onMessage.addListener((message) => {
    // Messages from Android App
});

browser.runtime.onMessage.addListener((message, sender, sendResponse) => {
    if (message.action === "long_press_media") {
        // Forward to Android App
        port.postMessage(message);
    }
});

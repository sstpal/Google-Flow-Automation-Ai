// Listen for long presses (contextmenu events) on images and videos
document.addEventListener("contextmenu", async function(event) {
    let target = event.target;
    if (target.tagName === "IMG" || target.tagName === "VIDEO") {
        event.preventDefault(); // Stop default browser context menu

        let mediaUrl = target.src || target.currentSrc;
        let mediaType = target.tagName.toLowerCase();

        // Check if this is inside a picture tag with better sources
        if (target.tagName === "IMG" && target.parentElement && target.parentElement.tagName === "PICTURE") {
            let sources = target.parentElement.getElementsByTagName('source');
            if (sources.length > 0) {
                mediaUrl = sources[0].srcset.split(' ')[0]; // Very basic extraction
            }
        }

        if (mediaUrl) {
            if (mediaUrl.startsWith("blob:")) {
                // Fetch blob and convert to base64
                try {
                    let response = await fetch(mediaUrl);
                    let blob = await response.blob();
                    let reader = new FileReader();
                    reader.onloadend = function() {
                        let base64data = reader.result;
                        browser.runtime.sendMessage({
                            action: "long_press_media",
                            url: base64data,
                            type: mediaType,
                            isBlob: true
                        });
                    };
                    reader.readAsDataURL(blob);
                } catch (e) {
                    console.error("Failed to fetch blob", e);
                }
            } else {
                browser.runtime.sendMessage({
                    action: "long_press_media",
                    url: mediaUrl,
                    type: mediaType,
                    isBlob: false
                });
            }
        }
    }
});

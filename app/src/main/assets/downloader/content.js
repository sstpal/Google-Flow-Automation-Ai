// Listen for long presses (contextmenu events) on images and videos
document.addEventListener("contextmenu", function(event) {
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
            browser.runtime.sendMessage({
                action: "long_press_media",
                url: mediaUrl,
                type: mediaType
            });
        }
    }
});

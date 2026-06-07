(function () {
    "use strict";

    if (!("serviceWorker" in navigator)) {
        return;
    }

    window.addEventListener("load", function () {
        navigator.serviceWorker.register("/service-worker.js")
            .catch(function (error) {
                console.warn("Registrazione Service Worker non riuscita:", error);
            });
    });
}());

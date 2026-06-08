(function () {
    "use strict";

    /*
     * Service worker disattivato per checkout/pagamenti.
     *
     * La vecchia versione intercettava /checkout e, in assenza di cache valida,
     * restituiva undefined invece di una Response, causando:
     * "Failed to convert value to 'Response'".
     *
     * Per un e-commerce con carrello, sessione, Stripe e PayPal è più sicuro
     * non cacheare le pagine dinamiche. Questo script rimuove eventuali service
     * worker/cache già installati nei browser dei clienti.
     */
    if ("serviceWorker" in navigator) {
        window.addEventListener("load", function () {
            navigator.serviceWorker.getRegistrations()
                .then(function (registrations) {
                    registrations.forEach(function (registration) {
                        registration.unregister();
                    });
                })
                .catch(function (error) {
                    console.warn("Rimozione Service Worker non riuscita:", error);
                });
        });
    }

    if ("caches" in window) {
        window.addEventListener("load", function () {
            caches.keys()
                .then(function (cacheNames) {
                    cacheNames.forEach(function (cacheName) {
                        caches.delete(cacheName);
                    });
                })
                .catch(function (error) {
                    console.warn("Pulizia cache PWA non riuscita:", error);
                });
        });
    }
}());

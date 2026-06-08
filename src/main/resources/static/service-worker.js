/*
 * Service worker di dismissione.
 *
 * Non registra cache e non intercetta le pagine dinamiche del checkout.
 * Serve solo a sostituire la vecchia versione già pubblicata, eliminare le cache
 * PWA e deregistrarsi dai browser che l'avevano installato.
 */
self.addEventListener("install", function () {
    self.skipWaiting();
});

self.addEventListener("activate", function (event) {
    event.waitUntil(
        caches.keys()
            .then(function (cacheNames) {
                return Promise.all(
                    cacheNames.map(function (cacheName) {
                        return caches.delete(cacheName);
                    })
                );
            })
            .then(function () {
                return self.registration.unregister();
            })
            .then(function () {
                return self.clients.matchAll({ type: "window" });
            })
            .then(function (clients) {
                clients.forEach(function (client) {
                    client.navigate(client.url);
                });
            })
    );
});

self.addEventListener("fetch", function (event) {
    event.respondWith(
        fetch(event.request).catch(function () {
            return new Response("Connessione non disponibile", {
                status: 503,
                statusText: "Service Unavailable",
                headers: {
                    "Content-Type": "text/plain; charset=UTF-8"
                }
            });
        })
    );
});

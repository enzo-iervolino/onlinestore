const CACHE_NAME = "online-store-pwa-v20260607-1";
const APP_SHELL = [
    "/",
    "/css/bootstrap.min.css",
    "/css/navbar.css",
    "/css/style.css",
    "/css/home.css",
    "/css/shop.css",
    "/js/bootstrap.bundle.min.js",
    "/js/cart.js",
    "/js/pwa.js?v=20260607-1",
    "/icons/icon-192.png",
    "/icons/icon-512.png",
    "/manifest.json"
];

self.addEventListener("install", function (event) {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(function (cache) {
                return cache.addAll(APP_SHELL);
            })
            .then(function () {
                return self.skipWaiting();
            })
    );
});

self.addEventListener("activate", function (event) {
    event.waitUntil(
        caches.keys()
            .then(function (cacheNames) {
                return Promise.all(
                    cacheNames
                        .filter(function (cacheName) {
                            return cacheName !== CACHE_NAME;
                        })
                        .map(function (cacheName) {
                            return caches.delete(cacheName);
                        })
                );
            })
            .then(function () {
                return self.clients.claim();
            })
    );
});

self.addEventListener("fetch", function (event) {
    if (event.request.method !== "GET") {
        return;
    }

    event.respondWith(
        fetch(event.request)
            .then(function (response) {
                return response;
            })
            .catch(function () {
                return caches.match(event.request)
                    .then(function (cachedResponse) {
                        if (cachedResponse) {
                            return cachedResponse;
                        }

                        if (event.request.mode === "navigate") {
                            return caches.match("/");
                        }

                        return undefined;
                    });
            })
    );
});

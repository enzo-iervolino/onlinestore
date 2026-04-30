document.addEventListener("DOMContentLoaded", () => {
    const cartCountEls = document.querySelectorAll(".cart-count");
    const cartButtons = document.querySelectorAll(".cart-btn");
    const cartItemsContainer = document.getElementById("cartItemsContainer");
    const cartItemsList = document.getElementById("cartItemsList");
    const cartEmptyState = document.getElementById("cartEmptyState");
    const cartFooter = document.getElementById("cartFooter");
    const cartTotalEl = document.getElementById("cartTotal");
    const clearCartBtn = document.getElementById("clearCartBtn");
    const cartOffcanvasEl = document.getElementById("cartOffcanvas");

    const VIDEO_EXTENSIONS = [".webm", ".mp4", ".mov", ".ogg", ".ogv"];

    function euro(value) {
        const number = Number(value || 0);
        return number.toLocaleString("it-IT", {
            minimumFractionDigits: 2,
            maximumFractionDigits: 2
        });
    }

    function escapeHtml(value) {
        if (value === null || value === undefined) {
            return "";
        }

        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");
    }

    function firstNotBlank(...values) {
        for (const value of values) {
            if (value !== null && value !== undefined && String(value).trim() !== "") {
                return String(value).trim();
            }
        }

        return "";
    }

    function isVideoUrl(url) {
        if (!url) {
            return false;
        }

        const cleanUrl = String(url).split("?")[0].split("#")[0].toLowerCase();
        return VIDEO_EXTENSIONS.some(extension => cleanUrl.endsWith(extension));
    }

    function resolveVideoType(url) {
        const cleanUrl = String(url || "").split("?")[0].split("#")[0].toLowerCase();

        if (cleanUrl.endsWith(".webm")) {
            return "video/webm";
        }

        if (cleanUrl.endsWith(".ogg") || cleanUrl.endsWith(".ogv")) {
            return "video/ogg";
        }

        if (cleanUrl.endsWith(".mov")) {
            return "video/quicktime";
        }

        return "video/mp4";
    }

    function buildCartMediaHtml(item) {
        const mediaUrl = firstNotBlank(
            item?.image,
            item?.mainImage,
            item?.imageUrl,
            item?.mediaUrl,
            item?.mainMediaUrl
        );

        if (!mediaUrl) {
            return `<div class="cart-item-placeholder">
                        <i class="bi bi-image text-light"></i>
                    </div>`;
        }

        const safeUrl = escapeHtml(mediaUrl);
        const safeAlt = escapeHtml(item?.name || "Prodotto");

        if (isVideoUrl(mediaUrl)) {
            return `<video class="cart-item-media"
                           muted
                           autoplay
                           loop
                           playsinline
                           preload="metadata"
                           aria-label="Video ${safeAlt}">
                        <source src="${safeUrl}" type="${resolveVideoType(mediaUrl)}">
                    </video>`;
        }

        return `<img src="${safeUrl}"
                    alt="${safeAlt}"
                    class="cart-item-media">`;
    }

    function buildVariantCodeText(item) {
        return firstNotBlank(item?.variantCode, item?.productCode);
    }

    function buildColorSizeText(item) {
        const parts = [];

        const colorText = firstNotBlank(item?.colorName, item?.color);
        const sizeText = firstNotBlank(item?.sizeName, item?.size);

        if (colorText) {
            parts.push(colorText);
        }

        if (sizeText) {
            parts.push(sizeText);
        }

        return parts.join(" / ");
    }

    function setCartCount(count) {
        const numericCount = Number(count || 0);

        cartCountEls.forEach(el => {
            el.textContent = numericCount;
            el.classList.toggle("d-none", numericCount === 0);
        });

        cartButtons.forEach(btn => {
            btn.disabled = numericCount === 0;
            btn.classList.toggle("cart-btn-disabled", numericCount === 0);
        });
    }

    function toggleCartState(hasItems) {
        if (!cartItemsContainer || !cartEmptyState || !cartFooter) {
            return;
        }

        cartItemsContainer.classList.toggle("d-none", !hasItems);
        cartFooter.classList.toggle("d-none", !hasItems);
        cartEmptyState.classList.toggle("d-none", hasItems);
    }

    function getOffcanvasInstance() {
        if (!cartOffcanvasEl || typeof bootstrap === "undefined" || !bootstrap.Offcanvas) {
            return null;
        }

        return bootstrap.Offcanvas.getOrCreateInstance(cartOffcanvasEl);
    }

    function openCartOffcanvas() {
        const offcanvas = getOffcanvasInstance();

        if (offcanvas) {
            offcanvas.show();
        }
    }

    function closeCartOffcanvas() {
        const offcanvas = getOffcanvasInstance();

        if (offcanvas) {
            offcanvas.hide();
        }
    }

    function buildCartItemHtml(item) {
        const id = escapeHtml(item?.id);
        const name = escapeHtml(item?.name || "Prodotto");
        const quantity = Number(item?.quantity || 0);
        const price = euro(item?.price || 0);
        const rowTotal = euro(item?.rowTotal || ((item?.price || 0) * (item?.quantity || 0)));

        const variantCodeText = buildVariantCodeText(item);
        const colorSizeText = buildColorSizeText(item);

        return `
            <div class="cart-offcanvas-item" data-cart-item-id="${id}">
                <div class="cart-offcanvas-main">
                    <div class="cart-offcanvas-media-box">
                        ${buildCartMediaHtml(item)}

                        <button type="button"
                                class="btn btn-sm btn-link p-0 cart-remove-btn cart-remove-btn-under-image"
                                data-id="${id}">
                            <i class="bi bi-trash3"></i>
                            <span>Rimuovi</span>
                        </button>
                    </div>

                    <div class="cart-offcanvas-info">
                        <strong class="cart-item-name" title="${name}">
                            ${name}
                        </strong>

                        ${variantCodeText ? `
                            <div class="cart-item-meta">
                                <span class="cart-item-variant-code" title="${escapeHtml(variantCodeText)}">
                                    ${escapeHtml(variantCodeText)}
                                </span>
                            </div>
                        ` : ""}

                        ${colorSizeText ? `
                            <div class="cart-item-colorSize" title="${escapeHtml(colorSizeText)}">
                                ${escapeHtml(colorSizeText)}
                            </div>
                        ` : ""}

                        <div class="cart-item-price">
                            € ${price} × ${quantity}
                        </div>

                        <div class="cart-offcanvas-bottom">
                            <div class="cart-item-actions">
                                <div class="cart-qty-group" role="group" aria-label="Quantità prodotto">
                                    <button type="button"
                                            class="cart-qty-btn cart-decrease-btn"
                                            data-id="${id}"
                                            aria-label="Diminuisci quantità">
                                        −
                                    </button>

                                    <span class="cart-qty-value">
                                        ${escapeHtml(quantity)}
                                    </span>

                                    <button type="button"
                                            class="cart-qty-btn cart-increase-btn"
                                            data-id="${id}"
                                            aria-label="Aumenta quantità">
                                        +
                                    </button>
                                </div>
                            </div>

                            <div class="cart-item-total-row">
                                <span class="cart-item-total-label">Totale</span>
                                <span class="cart-item-total">€ ${rowTotal}</span>
                            </div>
                        </div>
                    </div>
                </div>
            </div>`;
    }

    function renderCart(data) {
        const items = Array.isArray(data?.items) ? data.items : [];
        const count = Number(data?.count || 0);
        const total = data?.total || 0;

        setCartCount(count);

        if (!cartItemsList || !cartTotalEl) {
            return;
        }

        if (items.length === 0) {
            cartItemsList.innerHTML = "";
            cartTotalEl.textContent = euro(0);
            toggleCartState(false);
            return;
        }

        toggleCartState(true);
        cartTotalEl.textContent = euro(total);
        cartItemsList.innerHTML = items.map(buildCartItemHtml).join("");
    }

    async function callCart(url, options = {}) {
        const response = await fetch(url, {
            method: "POST",
            headers: {
                "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8"
            },
            credentials: "same-origin",
            ...options
        });

        if (!response.ok) {
            const text = await response.text();
            throw new Error(text || "Errore nella richiesta al carrello");
        }

        return response.json();
    }

    async function refreshCart() {
        try {
            const response = await fetch("/cart/view", {
                headers: {
                    "X-Requested-With": "XMLHttpRequest"
                },
                credentials: "same-origin"
            });

            if (!response.ok) {
                return;
            }

            const data = await response.json();
            renderCart(data);
        } catch (error) {
            console.error("Errore refresh carrello:", error);
        }
    }

    function scheduleCartRefresh(delay = 250) {
        window.setTimeout(() => {
            refreshCart();
        }, delay);
    }

    document.addEventListener("submit", (event) => {
        const form = event.target;

        if (!(form instanceof HTMLFormElement)) {
            return;
        }

        const action = form.getAttribute("action") || "";

        if (
            action.includes("/cart/add") ||
            action.includes("/cart/increase") ||
            action.includes("/cart/decrease") ||
            action.includes("/cart/remove") ||
            action.includes("/cart/clear")
        ) {
            scheduleCartRefresh(300);
        }
    });

    document.addEventListener("click", async (event) => {
        const openTrigger = event.target.closest(
            '[data-bs-target="#cartOffcanvas"],[href="#cartOffcanvas"],[data-cart-open],.cart-open-btn'
        );

        if (openTrigger) {
            event.preventDefault();
            await refreshCart();
            openCartOffcanvas();
            return;
        }

        const addTrigger = event.target.closest(
            '[data-cart-add],.add-to-cart-btn,.cart-add-btn,[data-cart-action="add"]'
        );

        if (addTrigger) {
            scheduleCartRefresh(300);
        }

        const increaseBtn = event.target.closest(".cart-increase-btn");
        if (increaseBtn) {
            event.preventDefault();

            const id = increaseBtn.dataset.id;

            try {
                const data = await callCart(`/cart/increase/${id}`);
                renderCart(data);
                openCartOffcanvas();
            } catch (error) {
                console.error("Errore aumento quantità:", error);
            }

            return;
        }

        const decreaseBtn = event.target.closest(".cart-decrease-btn");
        if (decreaseBtn) {
            event.preventDefault();

            const id = decreaseBtn.dataset.id;

            try {
                const data = await callCart(`/cart/decrease/${id}`);
                renderCart(data);
                openCartOffcanvas();
            } catch (error) {
                console.error("Errore diminuzione quantità:", error);
            }

            return;
        }

        const removeBtn = event.target.closest(".cart-remove-btn");
        if (removeBtn) {
            event.preventDefault();

            const id = removeBtn.dataset.id;

            try {
                const data = await callCart(`/cart/remove/${id}`);
                renderCart(data);
                openCartOffcanvas();
            } catch (error) {
                console.error("Errore rimozione prodotto:", error);
            }

            return;
        }
    });

    if (clearCartBtn) {
        clearCartBtn.addEventListener("click", async () => {
            const confirmed = window.confirm("Vuoi davvero svuotare il carrello?");

            if (!confirmed) {
                return;
            }

            try {
                const data = await callCart("/cart/clear");
                renderCart(data);
                openCartOffcanvas();
            } catch (error) {
                console.error("Errore svuotamento carrello:", error);
            }
        });
    }

    refreshCart();

    window.renderCartOffcanvas = renderCart;
    window.refreshCartOffcanvas = refreshCart;
    window.openCartOffcanvas = openCartOffcanvas;
    window.closeCartOffcanvas = closeCartOffcanvas;
});
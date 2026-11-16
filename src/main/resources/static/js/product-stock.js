/* Totale varianti sulle schede amministrative, senza modificare i campi editati. */
(() => {
    const targets = document.querySelectorAll('[data-product-stock-url]');
    targets.forEach(target => {
        let running = false;
        let hadVariants = target instanceof HTMLInputElement && target.readOnly;
        const refresh = async () => {
            if (running || document.hidden) return;
            running = true;
            try {
                const response = await fetch(target.dataset.productStockUrl, {
                    credentials: 'same-origin', cache: 'no-store',
                    headers: {'Accept': 'application/json'}
                });
                if (!response.ok || response.redirected) return;
                const variants = await response.json();
                if (!Array.isArray(variants)) return;
                if (!variants.length && !hadVariants) return;
                const total = variants.reduce((sum, variant) => sum + Number(variant.stockQty ?? 0), 0);
                if (!Number.isSafeInteger(total)) return;
                if (target instanceof HTMLInputElement) {
                    target.value = String(total);
                    target.readOnly = variants.length > 0;
                } else {
                    target.textContent = total.toLocaleString('it-IT', {minimumFractionDigits: 2});
                }
                hadVariants = variants.length > 0;
            } catch (_) {
                // Un errore di rete conserva l'ultimo totale, senza azzerarlo.
            } finally {
                running = false;
            }
        };
        refresh();
        window.setInterval(refresh, 5000);
        document.addEventListener('visibilitychange', refresh);
    });
})();

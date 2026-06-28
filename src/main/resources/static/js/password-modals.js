document.addEventListener("DOMContentLoaded", function () {

    const params = new URLSearchParams(window.location.search);

    function openModal(id) {
        const modalEl = document.getElementById(id);
        if (!modalEl) return;
        const modal = new bootstrap.Modal(modalEl);
        modal.show();
    }

    // =========================
    // FORGOT PASSWORD SUCCESS
    // =========================
    if (params.get("forgotSuccess") === "true") {
        openModal("forgotPasswordSuccessModal");
        cleanUrl();
    }

    // =========================
    // RESET PASSWORD FORM
    // =========================
    if (params.has("resetToken")) {
        const token = params.get("resetToken");
        const tokenInput = document.getElementById("resetTokenInput");
        if (tokenInput) {
            tokenInput.value = token;
        }
        openModal("resetPasswordModal");
        cleanUrl();
    }

    // =========================
    // PASSWORD MISMATCH
    // =========================
    if (params.get("passwordMismatch") === "true") {
        openModal("resetPasswordModal");
        cleanUrl();
    }

    // =========================
    // INVALID TOKEN
    // =========================
    if (params.get("invalidToken") === "true") {
        openModal("invalidTokenModal");
        cleanUrl();
    }

    // =========================
    // RESET SUCCESS
    // =========================
    if (params.get("resetSuccess") === "true") {
        openModal("resetSuccessModal");
        cleanUrl();
    }

    function cleanUrl() {
        const url = new URL(window.location);
        url.search = "";
        window.history.replaceState({}, document.title, url);
    }
});

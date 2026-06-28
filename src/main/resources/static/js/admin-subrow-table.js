document.addEventListener("DOMContentLoaded", function () {

    const mainRows = document.querySelectorAll(".main-row");

    function closeAllRows() {
        document.querySelectorAll(".detail-row").forEach(function (row) {
            row.classList.add("is-hidden");
        });

        document.querySelectorAll(".main-row").forEach(function (row) {
            row.classList.remove("row-open");
        });
    }

    mainRows.forEach(function (row) {
        row.addEventListener("click", function (event) {

            if (event.target.closest(".action-link")) {
                return;
            }

            const targetId = row.getAttribute("data-target");
            const detailRow = document.getElementById(targetId);

            if (!detailRow) {
                return;
            }

            const isClosed = detailRow.classList.contains("is-hidden");

            closeAllRows();

            if (isClosed) {
                detailRow.classList.remove("is-hidden");
                row.classList.add("row-open");
            }
        });
    });

    document.querySelectorAll(".action-link").forEach(function (btn) {
        btn.addEventListener("click", function (event) {
            event.stopPropagation();
        });
    });
});
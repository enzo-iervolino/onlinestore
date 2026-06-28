const toggleBtn = document.getElementById("toggleSidebar");
const wrapper = document.querySelector(".dashboard-wrapper");

toggleBtn.addEventListener("click", () => {
    wrapper.classList.toggle("sidebar-collapsed");
});

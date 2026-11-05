(function () {
  "use strict";

  const back = document.getElementById("mBack");
  if (back) {
    back.addEventListener("click", () => {
      window.App.controller.navigate("home");
    });
  }

  window.App.controller.init({ variant: "mobile" });
})();
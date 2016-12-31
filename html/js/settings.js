var reason = "Most likely this is due to the fact you're not accessing on the" +
    " server itself, Please access this configuration page on the server.";

function generateAlert(type, message) {
    var alert = document.createElement("div");
    alert.setAttribute("class", "alert alert-dismissible alert-" + type);
    alert.setAttribute("role", "alert");
    alert.setAttribute("style", "margin-top: 1%");

    var button = document.createElement("button");
    button.setAttribute("type", "button");
    button.setAttribute("class", "close");
    button.setAttribute("data-dismiss", "alert");
    button.setAttribute("aria-label", "close");

    var buttonText = document.createElement("span");
    buttonText.setAttribute("aria-hidden", "true");
    buttonText.innerHTML = "&times;";
    button.appendChild(buttonText);

    alert.appendChild(button);
    alert.innerHTML = alert.innerHTML + message;

    return alert;
}

function addAlert(type, message) {
    var alertBox = document.getElementById("alert-box");
    alertBox.appendChild(generateAlert(type, message));
}

function generateForm(k, json) {
    // Create a new form-group
    var group = document.createElement("div");
    group.setAttribute("class", "form-group");
    group.setAttribute("id", k + "-group");
    // Create a label for the form-group
    var label = document.createElement("label");
    label.setAttribute("for", k);
    label.setAttribute("id", k + "-label");
    // Add text to the label
    var text = document.createTextNode(k);
    label.appendChild(text);
    // Create text box
    var input = document.createElement("input");
    input.setAttribute("class", "form-control");
    input.setAttribute("id", k);
    input.value = json[k];
    group.appendChild(label);
    group.appendChild(input);

    return group;
}

function renderSettingsData(json) {
    if (json == null) {
        return;
    }

    for (var k in json) {
        if ({}.hasOwnProperty.call(json, k)) {
            var form = document.getElementById("settings-form");;
            form.appendChild(generateForm(k, json));
        }
    }
}

function generatePOSTDataFromInputs() {
    var inputs = document.getElementsByTagName("input");

    var data = "";

    inputs = Array.prototype.slice.call(inputs);

    inputs.forEach((input, idx) => {
        if (idx !== 0) {
            data += "&";
        }

        data += input.id + "=" + input.value;
    });

    return data;
}


function destroyInputs() {
    var groups = document.getElementsByClassName("form-group");

    groups = Array.prototype.slice.call(groups);

    groups.forEach((group) => {
        group.parentNode.removeChild(group);
    });
}

function getSettingsData() {
    destroyInputs();

    fetch("/api/settings.json", {
        method: "get"
    }).then((res) => {
        if (!res.ok) {
            addAlert("danger", "<b>FAILURE</b>: Failed to receive settings.<br>" + reason);
            return null;
        }

        return res.json(); // Return JSON data
    }).then(renderSettingsData);
}

function updateSettings() {
    fetch("/api/settings.json", {
        method: "post",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"
        },
        body: generatePOSTDataFromInputs()
    }).then((res) => {
        if (!res.ok) {
            addAlert("danger", "<b>FAILURE</b>: Failed to update settings.<br>" + reason);
            return null;
        }

        return res.json();
    }).then((json) => {
        if (json == null) {
            return;
        }

        addAlert("success", "<b>SUCCESS</b>: " + json.message);
    }).then(getSettingsData());
}

document.getElementById("apply-btn").addEventListener("click", updateSettings);

getSettingsData();

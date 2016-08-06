$(document).ready(function() {

    // javascript fallback support
    if (!String.prototype.endsWith) {
      String.prototype.endsWith = function(searchString, position) {
          var subjectString = this.toString();
          if (typeof position !== 'number' || !isFinite(position) || Math.floor(position) !== position || position > subjectString.length) {
            position = subjectString.length;
          }
          position -= searchString.length;
          var lastIndex = subjectString.indexOf(searchString, position);
          return lastIndex !== -1 && lastIndex === position;
      };
    }

    // global variables
    var installationHtmlPath = "/installation/";
    var installationApiPath = "/api/installation.json";
    var nextButton;

    // prevent side from unloading

    window.onbeforeunload = function() {
        return 'You have unsaved changes!';
    }


    // submit form via ajax

    function showRestart(responseObject, statusText, xhr, $form){
        $('#step0').addClass("hidden");
        $('#step1').addClass("hidden");
        $('#step2').addClass("hidden");
        $('#step3').addClass("hidden");
        $('#step4').addClass("hidden");
        $('#step5').addClass("hidden");
        $('#abort').addClass("hidden");
        $('#finish').removeClass("hidden");
        window.onbeforeunload = null;
        $('#abort').ajaxSubmit(optionsRestart);
    }

    function showError(xhr, ajaxOptions, thrownError) {
        alert("An error occured: " + thrownError);
    }

    var options = {
        success:        showRestart,   // post-submit callback
        error:          showError,      // on error
        url:            installationApiPath,
        type:           'get',
        dataType:       'json'
    };

    $('#submit').click(function(){
        $('#installation').ajaxSubmit(options);
    });

    var optionsRestart = {
        url:            installationApiPath,
        type:           'get',
        dataType:       'json'
    };

    $('#abort_button').click(function(){
        if (confirm('Are you sure you want to abort the installation?')) {
            $('#abort').ajaxSubmit(optionsRestart);
            showRestart();
        }
    });


    // navigation on enter being hit

    $("input").keydown(function(event){onEnter(event);});
    $("select").keydown(function(event){onEnter(event);});
    $("textarea").keydown(function(event){onEnter(event);});

    function onEnter(event){
        if(event.keyCode == 13){
            nextButton.click();
        }
    }


    // admin settings page

    var admin_email = $("#admin_email")[0];
    var admin_password = $("#admin_password")[0];
    var admin_confirm_password = $("#admin_password_confirm")[0];
    var admin_local_only = $("#admin_local_only_hidden")[0];
    var passwordsMatch = false;

    admin_email.onkeyup = checkStep0;
    admin_password.onkeyup = checkStep0;
    admin_confirm_password.onkeyup = checkStep0;

    function checkStep0(){

        if(admin_email.value == admin_password.value){
            admin_password.setCustomValidity("Password must not be equal to the email address");
        } else {
            admin_password.setCustomValidity("");
        }

        if(admin_password.value != admin_confirm_password.value) {
            admin_confirm_password.setCustomValidity("Passwords Don't Match");
            passwordsMatch = false;
        } else {
            admin_confirm_password.setCustomValidity('');
            passwordsMatch = true;
        }

        if(passwordsMatch
        && admin_email.checkValidity()
        && admin_password.checkValidity()
        && admin_confirm_password.checkValidity()
        && admin_local_only.checkValidity){
            $('#next0').prop("disabled", false);
            return true;
        }
        else{
            $('#next0').prop("disabled", true);
            return false;
        }
    }

    // work around for checkboxes to always get a valid value
    function setAdminLocal(){
        $('#admin_local_only_hidden').val($('#admin_local_only').is(':checked'));
    }
    $('#admin_local_only').click(function(){setAdminLocal();});

    setAdminLocal();
    $("#admin_email").focus();
    nextButton = $("#next0");
    checkStep0();


    // user settings page

    // if email registration is enabled, make smtp settings mandatory
    var smtpEnabled_value = $('#smtp_enabled').is(':checked');
    function setEmailRequired(){
        if($('#user_registration_confirmation').val() == 'email'){
            smtpEnabled_value = $('#smtp_enabled').is(':checked');
            $("#smtp_enabled").prop('checked', true);
            $("#smtp_enabled").prop('disabled', true);
            $("#label_smtp_enabled").text("Mandatory for registration email confirmation");
            setSmtpEnabled();
        }
        else{
            $("#smtp_enabled").prop('checked', smtpEnabled_value);
            $("#smtp_enabled").prop('disabled', false);
            $("#label_smtp_enabled").text("");
            setSmtpEnabled();
        }
    }
    $("#user_registration_confirmation").change(function(){setEmailRequired();});
    $("#user_registration_confirmation")[0].onkeyup = setEmailRequired;


    // general settings page

    var shortlink_value;
    // set current url
    function setUrl(){
        var url = $(location).attr('href');
        if(url.endsWith("index.html")){
            url = url.slice(0,-"index.html".length);
        }
        if(url.endsWith(installationHtmlPath)){
            url = url.slice(0,-installationHtmlPath.length);
        }
        $("#host_url").val(url);
        $("#shortlink_url").val(url);
        shortlink_value = url;
    }
    setUrl();

    // save value of shortlink field if checkbox is toggled
    function setShortLink(){
        if($("#shortlink_checkbox").is(':checked')){
            $("#shortlink_url").addClass("hidden");
            $("#label_shortlink_url").addClass("hidden");
            shortlink_value = $("#shortlink_url").val();
            $("#shortlink_url").val($("#host_url").val());
        } else {
            $("#shortlink_url").removeClass("hidden");
            $("#label_shortlink_url").removeClass("hidden");
            $("#shortlink_url").val(shortlink_value);
        }
        checkStep2();
    }
    $("#shortlink_checkbox").click(function(){setShortLink()});

    var hostUrl = $('#host_url')[0];
    var shortlinkUrl = $('#shortlink_url')[0];
    var peername = $('#peername')[0];
    var backends = $('#backends')[0];
    var backendPush = $('#backend_push_hidden')[0];

    host_url.onkeyup = checkStep2;
    shortlinkUrl.onkeyup = checkStep2;
    peername.onkeyup = checkStep2;
    backends.onkeyup = checkStep2;

    function checkStep2(){
        if(host_url.checkValidity()
        && shortlinkUrl.checkValidity()
        && peername.checkValidity()
        && backends.checkValidity()
        && backendPush.checkValidity()){
            $('#next2').prop("disabled", false);
            return true;
        }
        else{
            $('#next2').prop("disabled", true);
            return false;
        }
    }

    // work around for checkboxes to always get a valid value
    function setPushBackend(){
        if($('#backend_push').is(':checked')){
            $('#backend_push_hidden').val(true);
            $("#backends").prop('disabled', false);
            $("#backend_settings").removeClass('hidden');
        }
        else{
            $('#backend_push_hidden').val(false);
            $("#backends").prop('disabled', true);
            $("#backend_settings").addClass('hidden');
        }
        checkStep2();
    }
    $('#backend_push').click(function(){setPushBackend()});
    setPushBackend();

    setShortLink();


    // smtp settings

    $("#smtp_enabled").click(function(){
        smtpEnabled_value = $('#smtp_enabled').is(':checked');
        setSmtpEnabled();
    });
    function setSmtpEnabled(){

        if($('#smtp_enabled').is(':checked')){
            $('#smtp_enabled_hidden').val(true);
            $("#smtp_settings").removeClass("hidden");
            $("#smtp_host").prop('disabled', false);
            $("#smtp_port").prop('disabled', false);
            $("#smtp_email").prop('disabled', false);
            $("#smtp_displayname").prop('disabled', false);
            $("#smtp_username").prop('disabled', false);
            $("#smtp_password").prop('disabled', false);
            $("#smtp_encryption").prop('disabled', false);
            $('#smtp_disable_certificate_checking_hidden').prop('disabled', false);
        } else {
            $('#smtp_enabled_hidden').val(false);
            $("#smtp_settings").addClass("hidden");
            $("#smtp_host").prop('disabled', true);
            $("#smtp_port").prop('disabled', true);
            $("#smtp_email").prop('disabled', true);
            $("#smtp_displayname").prop('disabled', true);
            $("#smtp_username").prop('disabled', true);
            $("#smtp_password").prop('disabled', true);
            $("#smtp_encryption").prop('disabled', true);
            $('#smtp_disable_certificate_checking_hidden').prop('disabled', true);
        }
        checkStep3();
    }

    $("#smtp_encryption").change(function(){
        switch($(this).val()){
            case "none":
                $("#smtp_port").val(25);
                break;
            case "starttls":
                $("#smtp_port").val(587);
                break;
            case "tls":
                $("#smtp_port").val(465);
        }
    });

    // work around for checkboxes to always get a valid value
    function setSmtpDisableCertificateChecking(){
        $('#smtp_disable_certificate_checking_hidden').val($('#smtp_disable_certificate_checking').is(':checked'));
    }
    $('#smtp_disable_certificate_checking').click(function(){setSmtpDisableCertificateChecking();});
    setSmtpDisableCertificateChecking();

    // test smtp settings
    $("#smtp_test_button").click(function(){
        $('#label_smtp_test_button').removeClass("error");
        $('#label_smtp_test_button').removeClass("success");
        $('#label_smtp_test_button').text("Testing");

        $.ajax(	installationApiPath, {
            data: { checkSmtpCredentials: true,
                    smtpHostName: $("#smtp_host").val(),
                    smtpUsername: $("#smtp_username").val(),
                    smtpPassword: $("#smtp_password").val(),
                    smtpHostEncryption: $("#smtp_encryption").val(),
                    smtpHostPort: $("#smtp_port").val(),
                    smtpDisableCertificateChecking: $('#smtp_disable_certificate_checking_hidden').val()},
            dataType: 'json',
            success: function (response) {
                $('#label_smtp_test_button').text("Success");
                $('#label_smtp_test_button').addClass("success");
            },
            error: function (xhr, ajaxOptions, thrownError) {
                $('#label_smtp_test_button').text(thrownError);
                $('#label_smtp_test_button').addClass("error");
           },
           timeout: 30000
        });
    });

    var smtpHost = $('#smtp_host')[0];
    var smtpEmail = $('#smtp_email')[0];
    var smtpDisplayname = $('#smtp_displayname')[0];
    var smtpUsername = $('#smtp_username')[0];
    var smtpPassword = $('#smtp_password')[0];
    var smtpPort = $('#smtp_port')[0];
    var smtpEncryption = $('#smtp_encryption')[0];
    var smtpDisableCertificateChecking = $('#smtp_disable_certificate_checking_hidden')[0];

    smtpHost.onkeyup = checkStep3;
    smtpEmail.onkeyup = checkStep3;
    smtpDisplayname.onkeyup = checkStep3;
    smtpUsername.onkeyup = checkStep3;
    smtpPassword.onkeyup = checkStep3;
    smtpPort.onkeyup = checkStep3;
    smtpEncryption.onchange = checkStep3;

    function checkStep3(){
        if(smtpHost.checkValidity()
        && smtpEmail.checkValidity()
        && smtpUsername.checkValidity()
        && smtpDisplayname.checkValidity()
        && smtpPassword.checkValidity()
        && smtpEncryption.checkValidity()
        && smtpPort.checkValidity()
        && smtpDisableCertificateChecking.checkValidity()){
            $('#next3').prop("disabled", false);
            $('#smtp_test_button').prop("disabled", false);
            return true;
        }
        else{
            $('#next3').prop("disabled", true);
            $('#smtp_test_button').prop("disabled", true);
            return false;
        }
    }
    setSmtpEnabled();
    setEmailRequired(); // has to be run after defining the variables above


    // https settings

    var certificatesTrustSelfsigned = $('#certificates_trust_selfsigned')[0];
    var httpsMode = $('#https_mode')[0];
    var httpsKeySource = $('#https_key_source')[0];
    var httpsKeyStore = $('#https_keystore_name')[0];
    var httpsKeyStorePW = $('#https_keystore_password')[0];
    var httpsKey = $('#https_key')[0];
    var httpsCert = $('#https_cert')[0];

    certificatesTrustSelfsigned.onchange = checkStep4;
    httpsMode.onchange = setHttpsMode;
    httpsKeySource.onchange = setHttpsKeySource;
    httpsKeyStore.onkeyup = checkStep4;
    httpsKeyStorePW.onkeyup = checkStep4;
    httpsKey.onkeyup = checkStep4;
    httpsCert.onkeyup = checkStep4;

    function setHttpsMode(){
        if($("#https_mode").val() == 'on' || $("#https_mode").val() == 'redirect' || $("#https_mode").val() == 'only'){
            $("#https_settings").removeClass("hidden");
            $("#https_key_source").prop('disabled', false);
            setHttpsKeySource();
        } else {
            $("#https_settings").addClass("hidden");
            $("#https_key_source").prop('disabled', true);
            $("#https_keystore_name").prop('disabled', true);
            $("#https_keystore_password").prop('disabled', true);
            $("#https_key").prop('disabled', true);
            $("#https_cert").prop('disabled', true);
            checkStep4();
        }
    }

    function setHttpsKeySource(){
        if($("#https_key_source").val() == 'keystore'){

            $("#https_keystore_settings").removeClass("hidden");
            $("#https_keystore_name").prop('disabled', false);
            $("#https_keystore_password").prop('disabled', false);

            $("#https_keycert_settings").addClass("hidden");
            $("#https_key").prop('disabled', true);
            $("#https_cert").prop('disabled', true);

        } else if($("#https_key_source").val() == 'key-cert'){

            $("#https_keystore_settings").addClass("hidden");
            $("#https_keystore_name").prop('disabled', true);
            $("#https_keystore_password").prop('disabled', true);

            $("#https_keycert_settings").removeClass("hidden");
            $("#https_key").prop('disabled', false);
            $("#https_cert").prop('disabled', false);

        }
        checkStep4();
    }

    function checkStep4(){
        if(certificatesTrustSelfsigned.checkValidity()
        && httpsMode.checkValidity()
        && httpsKeySource.checkValidity()
        && httpsKeyStore.checkValidity()
        && httpsKeyStorePW.checkValidity()
        && httpsKey.checkValidity()
        && httpsCert.checkValidity()){
            $('#next4').prop("disabled", false);
            return true;
        }
        else{
            $('#next4').prop("disabled", true);
            return false;
        }
    }
    setHttpsMode();


    // summary

    function setSummary(){
        var summary = $("#summary");
        var showPasswords = $('#summary_show_passwords').is(':checked');

        summary.val("Admin settings");
        summary.val(summary.val() + "\nAdmin email: " + $('#admin_email').val());
        summary.val(summary.val() + "\nAdmin password: " + (showPasswords ? $('#admin_password').val()  :"******"));
        summary.val(summary.val() + "\nLocal only: " + $('#admin_local_only_hidden').val());

        summary.val(summary.val() + "\n\nUser settings:");
        summary.val(summary.val() + "\nPublic user registration: " + $('#user_registration_confirmation').find("option:selected").text());

        summary.val(summary.val() + "\n\nGeneral settings:");
        summary.val(summary.val() + "\nHost URL: " + $('#host_url').val());
        summary.val(summary.val() + "\nShortlink URL: " + $('#shortlink_url').val());
        summary.val(summary.val() + "\nPeername: " + $('#peername').val());
        summary.val(summary.val() + "\nPush to backend: " + $('#backend_push_hidden').val());
        if($('#backend_push').is(':checked')){summary.val(summary.val() + "\nBackends: " + $('#backends').val());}

        summary.val(summary.val() + "\n\nSMTP settings:");
        summary.val(summary.val() + "\nEnable SMTP: " + $('#smtp_enabled_hidden').val());
        if($('#smtp_enabled').is(':checked')){
            summary.val(summary.val() + "\nHostname: " + $('#smtp_host').val());
            summary.val(summary.val() + "\nEmail: " + $('#smtp_email').val());
            summary.val(summary.val() + "\nDisplayname: " + $('#smtp_displayname').val());
            summary.val(summary.val() + "\nUsername: " + $('#smtp_username').val());
            summary.val(summary.val() + "\nPassword: " + (showPasswords ? $('#smtp_password').val() : "******"));
            summary.val(summary.val() + "\nPort: " + $('#smtp_port').val());
            summary.val(summary.val() + "\nEncryption: " + $('#smtp_encryption').find("option:selected").text());
            summary.val(summary.val() + "\nDisable certificate checking: " + $('#smtp_disable_certificate_checking_hidden').val());
        }

        summary.val(summary.val() + "\n\nHTTPS settings:");
        summary.val(summary.val() + "\nTrust self-signed certificates: " + $('#certificates_trust_selfsigned').find("option:selected").text());
        summary.val(summary.val() + "\nHTTPS Mode: " + $('#https_mode').find("option:selected").text());
        if($("#https_mode").val() == 'on' || $("#https_mode").val() == 'redirect' || $("#https_mode").val() == 'only'){
            summary.val(summary.val() + "\nKey Source: " + $('#https_key_source').find("option:selected").text());
            if($("#https_key_source").val() == 'keystore'){
                summary.val(summary.val() + "\nKeystore name: " + $('#https_keystore_name').val());
                summary.val(summary.val() + "\nKeystore password: " + (showPasswords ? $('#https_keystore_name').val() : "******"));
            } else if($("#https_key_source").val() == 'key-cert'){
                summary.val(summary.val() + "\nKey file: " + $('#https_key').val());
                summary.val(summary.val() + "\nCertificate: " + $('#https_cert').val());
            }
        }
    }

    $("#summary_show_passwords").click(function(){setSummary();});


    // buttons
    $('#next0').click(function(){
        if(checkStep0()){
            $('#step0').addClass("hidden");
            $('#step1').removeClass("hidden");
            nextButton = $("#next1");
            $("#user_registration_confirmation").focus();
        }
    });
    $('#next1').click(function(){
        $('#step1').addClass("hidden");
        $('#step2').removeClass("hidden");
        nextButton = $("#next2");
        $("#host_url").focus();
    });
    $('#next2').click(function(){
        if(checkStep2()){
            $('#step2').addClass("hidden");
            $('#step3').removeClass("hidden");
            nextButton = $("#next3");
            $("#smtp_enabled").focus();
        }
    });
    $('#next3').click(function(){
        if(checkStep3()){
            $('#step3').addClass("hidden");
            $('#step4').removeClass("hidden");
            nextButton = $("#next4");
            $("#certificates_trust_selfsigned").focus();
            //checkStep4();
        }
    });
    $('#next4').click(function(){
        if(checkStep4()){
            $('#step4').addClass("hidden");
            $('#step5').removeClass("hidden");
            nextButton = $("#submit");
            $("#summary").focus();
            setSummary();
        }
    });
    $('#back1').click(function(){
        $('#step1').addClass("hidden");
        $('#step0').removeClass("hidden");
        nextButton = $("#next0");
        $("#admin_email").focus();
    });
    $('#back2').click(function(){
        $('#step2').addClass("hidden");
        $('#step1').removeClass("hidden");
        nextButton = $("#next1");
        $("#user_registration_confirmation").focus();
    });
    $('#back3').click(function(){
        $('#step3').addClass("hidden");
        $('#step2').removeClass("hidden");
        nextButton = $("#next2");
        $("#host_url").focus();
    });
    $('#back4').click(function(){
        $('#step4').addClass("hidden");
        $('#step3').removeClass("hidden");
        nextButton = $("#next3");
        $("#smtp_enabled").focus();
    });
    $('#back5').click(function(){
        $('#step5').addClass("hidden");
        $('#step4').removeClass("hidden");
        nextButton = $("#next4");
        $("#certificates_trust_selfsigned").focus();
    });
});
using System;
using System.Collections.Generic;
using System.Linq;
using System.Web;
using System.Net;

/// <summary>
/// Summary description for AccessToken
/// </summary>
public class AccessToken
{
    public AccessToken()
    {
        //
        // TODO: Add constructor logic here
        //
    }

    public string accessToken()
    {
        WebClient fbaccess = new WebClient();
        fbaccess.Proxy.Credentials = CredentialCache.DefaultNetworkCredentials;
        var accesstoken = fbaccess.DownloadString("https://graph.facebook.com/oauth/access_token?grant_type=fb_exchange_token&client_id=762222720586687&client_secret=5689f1c0088c76069087c442ffa7fa12&fb_exchange_token=EAAK1PM6AE78BAGODZAi9DyHYZCGLwDZCsG1x8xZA8Y6AEcncr8I556fUbsolLchr2jXyjAhBriCX0cI0BykGwf8R61EqGzmZCvWRkCZCoRRDVGwZBQ6O66QMMmDcWZAZBMo36WNkQeIkxaGQpSW7iQj7v21CF9GCbaP4ZD");
        string token = accesstoken.Remove(0, 13);
        token = token.Remove(token.Length - 16);
        return token;
    }
}

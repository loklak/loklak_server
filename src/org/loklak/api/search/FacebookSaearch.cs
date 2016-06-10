using Newtonsoft.Json.Linq;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.IO;
using System.Net;
//using System.Text;
using System.Threading.Tasks;

namespace FacebookSaearch
{
    class Program
    {
        static void Main(string[] args)
        {
            
                string pageName=args[1];
                y(pageName);
            
        }

        static void y(string pageName) {

            WebClient fbaccess = new WebClient();
            fbaccess.Proxy.Credentials = CredentialCache.DefaultNetworkCredentials;
            var accesstoken = fbaccess.DownloadString("https://graph.facebook.com/oauth/access_token?grant_type=fb_exchange_token&client_id=1006289226156546&client_secret=c343d2c827f69c9c0895768a07ae0ad7&fb_exchange_token=EAACEdEose0cBAP7CYSje1E1AEMkQ9jhQbkozjWdjxgPRKjTAsJrWGr3s9PXIbY6IdnldffVD4QaJvcS1URkoXG0h5Sp6ca6pSA5PGFAsQKuB3dMplKiCXZBYJIrxpYbItjcC3ZBMEDZCKxKsX2LmCYNyi560Tx5g5J2K08WJAZDZD");
            string token = accesstoken.Remove(0, 13);
            token = token.Remove(token.Length - 16);

            WebClient fbdev = new WebClient();
            fbdev.Proxy.Credentials = CredentialCache.DefaultNetworkCredentials;
            var jdata = fbdev.DownloadString("https://graph.facebook.com/"+pageName+"/feed?access_token=" + token + "");
            JObject jparse = JObject.Parse(jdata);

            //for (int i = 0; i <= 20; i++)
            //{
                var data = jparse["data"];
                int i = 0;

                foreach (JObject root in data)
                {
                    
                    try
                    {
                        Console.WriteLine((string)data[i]["id"] + "|" + (string)data[i]["message"] + "|" + (string)data[i]["created_time"] + "|"+(string)data[i]["likes"]);

                    }

                    catch (Exception)
                    {

                        Console.WriteLine((string)data[i]["id"] + "|" + (string)data[i]["message"] + "|" + "" + "|" + (string)data[i]["created_time"]);
                    }
                }

            //}
            
        
        }
    }
}

package org.loklak.data;

import java.util.logging.*;
import java.io.*;

public class logger{    


    Logger logger = Logger.getLogger("MyLog");  
    FileHandler fh;  
    
    public logger()
    {
 //       this.logstart();


    }

    public void logstart()
    {
        try {  
            // This block configure the logger with handler and formatter  
            
            this.fh = new FileHandler("/home/vibhcool/Documents/terminal_logs/loklak_server_logs/TryLog.log", true);
            
this.logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();  
            this.fh.setFormatter(formatter);  

            // the following statement is used to log any messages  
            //this.logger.info("nope, logger ready....");  

        } catch (SecurityException e) {  
            e.printStackTrace();  
        } catch (IOException e) {  
            e.printStackTrace();  
        }  




    }

public void logit(String a)
{
this.logstart();
logger.info(a);
this.fh.close();
}


}
